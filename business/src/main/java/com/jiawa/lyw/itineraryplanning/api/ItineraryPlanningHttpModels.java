package com.jiawa.lyw.itineraryplanning.api;

import com.jiawa.lyw.itineraryplanning.application.ItineraryPlanningApplicationService;
import com.jiawa.lyw.itinerary.domain.ItineraryModels;
import com.jiawa.lyw.itineraryplanning.application.PlanningCommands;
import com.jiawa.lyw.itineraryplanning.domain.PlanningModels;
import com.jiawa.lyw.itineraryplanning.domain.PlanningStatus;
import com.jiawa.lyw.itineraryplanning.domain.ProposalStatus;
import com.jiawa.lyw.itineraryplanning.domain.RevisionOperationType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ItineraryPlanningHttpModels {
    private ItineraryPlanningHttpModels() {
    }

    public record DestinationInput(String name, String countryCode, String timeZone) {
        PlanningModels.DestinationInput domain() {
            return new PlanningModels.DestinationInput(name, countryCode, ZoneId.of(timeZone));
        }
    }

    public record PreferencesInput(
            PlanningModels.TravelPace pace,
            Set<PlanningModels.PreferenceTag> tags,
            String notes
    ) {
        PlanningModels.Preferences domain() {
            return new PlanningModels.Preferences(pace, tags, notes);
        }
    }

    public record DraftInput(
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal budgetAmount,
            String budgetCurrency,
            int partySize,
            PreferencesInput preferences,
            List<DestinationInput> destinations
    ) {
        PlanningModels.RequestDraft domain(long itineraryId) {
            return new PlanningModels.RequestDraft(
                    itineraryId, startDate, endDate, budgetAmount, Currency.getInstance(budgetCurrency),
                    partySize, preferences == null ? null : preferences.domain(),
                    destinations == null ? null : destinations.stream().map(DestinationInput::domain).toList()
            );
        }
    }

    public record SaveRequest(String requestId, long expectedVersion, DraftInput draft) {
        PlanningCommands.SaveDraft command(long itineraryId) {
            return new PlanningCommands.SaveDraft(
                    requestId == null ? null : Long.parseLong(requestId),
                    expectedVersion,
                    draft == null ? null : draft.domain(itineraryId)
            );
        }
    }

    public record GenerateRequest(long expectedVersion) {
    }

    public record ConfirmRequest(
            UUID decisionId,
            UUID commandId,
            long expectedItineraryVersion,
            List<String> selectedOperationKeys
    ) {
        PlanningCommands.Confirm command() {
            return new PlanningCommands.Confirm(
                    decisionId, commandId, expectedItineraryVersion, selectedOperationKeys
            );
        }
    }

    public record RejectRequest(UUID decisionId) {
        PlanningCommands.Reject command() {
            return new PlanningCommands.Reject(decisionId);
        }
    }

    public record DestinationResponse(String name, String countryCode, String timeZone) {
        static DestinationResponse from(PlanningModels.DestinationInput destination) {
            return new DestinationResponse(
                    destination.name(), destination.countryCode(), destination.timeZone().getId()
            );
        }
    }

    public record PreferencesResponse(
            PlanningModels.TravelPace pace,
            Set<PlanningModels.PreferenceTag> tags,
            String notes
    ) {
        static PreferencesResponse from(PlanningModels.Preferences preferences) {
            return new PreferencesResponse(preferences.pace(), preferences.tags(), preferences.notes());
        }
    }

    public record RequestResponse(
            String id,
            String itineraryId,
            long version,
            PlanningStatus status,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal budgetAmount,
            String budgetCurrency,
            int partySize,
            PreferencesResponse preferences,
            List<DestinationResponse> destinations
    ) {
        static RequestResponse from(ItineraryPlanningApplicationService.PlanningRequestView view) {
            PlanningModels.RequestDraft draft = view.draft();
            return new RequestResponse(
                    Long.toString(view.id()), Long.toString(draft.itineraryId()), view.version(),
                    view.status(), draft.startDate(), draft.endDate(), draft.budgetAmount(),
                    draft.budgetCurrency().getCurrencyCode(), draft.partySize(),
                    PreferencesResponse.from(draft.preferences()),
                    draft.destinations().stream().map(DestinationResponse::from).toList()
            );
        }
    }

    public record ItemResponse(
            LocalDate date,
            String title,
            String placeName,
            LocalTime startTime,
            LocalTime endTime,
            String notes,
            BigDecimal estimatedCost
    ) {
        static ItemResponse from(PlanningModels.ItemFields item) {
            return item == null ? null : new ItemResponse(
                    item.date(), item.title(), item.placeName(), item.startTime(), item.endTime(),
                    item.notes(), item.estimatedCost()
            );
        }

        static ItemResponse from(LocalDate date, ItineraryModels.Item item) {
            return item == null ? null : new ItemResponse(
                    date, item.title(), item.placeName(), item.startTime(), item.endTime(),
                    item.notes(), item.estimatedCost()
            );
        }
    }

    public record ItemReferenceResponse(String existingItemId, String addedByOperationKey) {
        static ItemReferenceResponse from(PlanningModels.ItemReference reference) {
            return new ItemReferenceResponse(
                    reference.existingItemId() == null ? null : Long.toString(reference.existingItemId()),
                    reference.addedByOperationKey()
            );
        }
    }

    public record OperationResponse(
            String operationKey,
            RevisionOperationType type,
            String summary,
            String targetItemId,
            LocalDate targetDate,
            ItemResponse beforeItem,
            ItemResponse afterItem,
            List<ItemReferenceResponse> beforeItemReferences,
            List<ItemReferenceResponse> afterItemReferences,
            Set<String> dependencies
    ) {
        static OperationResponse from(
                PlanningModels.RevisionOperation operation,
                Set<String> dependencies,
                ItineraryModels.Snapshot snapshot
        ) {
            String targetItemId = null;
            LocalDate targetDate = null;
            ItemResponse beforeItem = null;
            ItemResponse afterItem = null;
            List<ItemReferenceResponse> beforeReferences = List.of();
            List<ItemReferenceResponse> afterReferences = List.of();
            if (operation instanceof PlanningModels.AddItemOperation add) {
                afterItem = ItemResponse.from(add.item()); targetDate = add.item().date();
            } else if (operation instanceof PlanningModels.UpdateItemOperation update) {
                targetItemId = Long.toString(update.targetItemId());
                ExistingItem existing = findItem(snapshot, update.targetItemId());
                beforeItem = existing == null ? null : ItemResponse.from(existing.date(), existing.item());
                afterItem = ItemResponse.from(update.item());
                targetDate = update.item().date();
            } else if (operation instanceof PlanningModels.DeleteItemOperation delete) {
                targetItemId = Long.toString(delete.targetItemId());
                ExistingItem existing = findItem(snapshot, delete.targetItemId());
                beforeItem = existing == null ? null : ItemResponse.from(existing.date(), existing.item());
                targetDate = existing == null ? null : existing.date();
            } else if (operation instanceof PlanningModels.ReorderDayItemsOperation reorder) {
                targetDate = reorder.date();
                beforeReferences = snapshot.days().stream()
                        .filter(day -> day.date().equals(reorder.date()))
                        .findFirst().stream().flatMap(day -> day.items().stream())
                        .filter(item -> !item.deleted())
                        .map(item -> new ItemReferenceResponse(Long.toString(item.id()), null))
                        .toList();
                afterReferences = reorder.itemReferences().stream().map(ItemReferenceResponse::from).toList();
            }
            return new OperationResponse(
                    operation.operationKey(), operation.type(), operation.summary(), targetItemId,
                    targetDate, beforeItem, afterItem, beforeReferences, afterReferences, dependencies
            );
        }

        private static ExistingItem findItem(ItineraryModels.Snapshot snapshot, long itemId) {
            return snapshot.days().stream()
                    .flatMap(day -> day.items().stream()
                            .filter(item -> item.id() == itemId && !item.deleted())
                            .map(item -> new ExistingItem(day.date(), item)))
                    .findFirst().orElse(null);
        }

        private record ExistingItem(LocalDate date, ItineraryModels.Item item) {
        }
    }

    public record ProposalResponse(
            String id,
            String requestId,
            String itineraryId,
            long baseItineraryVersion,
            long comparedItineraryVersion,
            ProposalStatus status,
            String summary,
            BigDecimal projectedCost,
            List<String> knowledgeReferenceIds,
            List<OperationResponse> operations,
            String failureCode
    ) {
        static ProposalResponse from(
                ItineraryPlanningApplicationService.ProposalView view,
                ItineraryModels.Snapshot snapshot
        ) {
            PlanningModels.ValidatedProposal validated = view.proposal();
            return new ProposalResponse(
                    Long.toString(view.id()), Long.toString(view.requestId()),
                    Long.toString(view.itineraryId()), view.baseItineraryVersion(), snapshot.version(), view.status(),
                    validated == null ? null : validated.proposal().summary(),
                    validated == null ? null : validated.projectedCost(),
                    validated == null ? List.of() : validated.proposal().knowledgeReferenceIds(),
                    validated == null ? List.of() : validated.proposal().operations().stream()
                            .map(operation -> OperationResponse.from(
                                    operation,
                                    validated.dependencies().getOrDefault(operation.operationKey(), Set.of()),
                                    snapshot
                            )).toList(),
                    view.failureCode()
            );
        }
    }

    public record ResolutionResponse(
            String proposalId,
            boolean confirmed,
            Long resultVersion,
            boolean replayed
    ) {
        static ResolutionResponse from(ItineraryPlanningApplicationService.ResolutionView view) {
            return new ResolutionResponse(
                    Long.toString(view.proposalId()), view.confirmed(), view.resultVersion(), view.replayed()
            );
        }
    }

    public record ErrorContent(String errorCode) {
    }
}
