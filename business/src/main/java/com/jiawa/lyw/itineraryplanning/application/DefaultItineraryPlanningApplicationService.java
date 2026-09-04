package com.jiawa.lyw.itineraryplanning.application;

import com.jiawa.lyw.itinerary.application.ItineraryApplicationService;
import com.jiawa.lyw.itinerary.application.ItineraryCommands;
import com.jiawa.lyw.itinerary.application.ItineraryIdGenerator;
import com.jiawa.lyw.itinerary.domain.ItineraryModels;
import com.jiawa.lyw.itineraryplanning.domain.PlanningError;
import com.jiawa.lyw.itineraryplanning.domain.PlanningException;
import com.jiawa.lyw.itineraryplanning.domain.PlanningModels;
import com.jiawa.lyw.itineraryplanning.domain.PlanningStatus;
import com.jiawa.lyw.itineraryplanning.domain.ProposalStatus;
import com.jiawa.lyw.itineraryplanning.domain.RevisionProposalValidator;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultItineraryPlanningApplicationService implements ItineraryPlanningApplicationService {
    private final PlanningRepository repository;
    private final ItineraryPlannerGateway planner;
    private final RevisionProposalValidator validator;
    private final ItineraryApplicationService itineraries;
    private final ItineraryIdGenerator ids;
    private final Clock clock;

    public DefaultItineraryPlanningApplicationService(
            PlanningRepository repository,
            ItineraryPlannerGateway planner,
            RevisionProposalValidator validator,
            ItineraryApplicationService itineraries,
            ItineraryIdGenerator ids,
            Clock clock
    ) {
        this.repository = repository;
        this.planner = planner;
        this.validator = validator;
        this.itineraries = itineraries;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public PlanningRequestView saveDraft(long actorMemberId, PlanningCommands.SaveDraft command) {
        if (actorMemberId <= 0 || command == null) {
            throw invalidRequest();
        }
        assertDraftMatchesItinerary(actorMemberId, command.draft());
        PlanningRepository.RequestRecord stored;
        if (command.requestId() == null) {
            stored = repository.createDraft(ids.nextId(), actorMemberId, command.draft(), clock.instant());
        } else {
            stored = repository.updateDraft(
                    command.requestId(), actorMemberId, command.expectedVersion(), command.draft(), clock.instant()
            ).orElseThrow(() -> classifyRequestFailure(
                    actorMemberId, command.requestId(), command.expectedVersion()
            ));
        }
        return requestView(stored);
    }

    @Override
    public PlanningRequestView getRequest(long actorMemberId, long requestId) {
        return requestView(requireRequest(actorMemberId, requestId));
    }

    @Override
    public PlanningRequestView getRequestForItinerary(long actorMemberId, long itineraryId) {
        if (actorMemberId <= 0 || itineraryId <= 0) {
            throw planningNotFound();
        }
        itineraries.get(actorMemberId, itineraryId);
        return requestView(repository.findLatestRequest(itineraryId, actorMemberId)
                .orElseThrow(DefaultItineraryPlanningApplicationService::planningNotFound));
    }

    @Override
    public ProposalView generate(long actorMemberId, long requestId, long expectedRequestVersion) {
        if (actorMemberId <= 0 || requestId <= 0 || expectedRequestVersion < 1) {
            throw invalidRequest();
        }
        PlanningRepository.RequestRecord claimed = repository.claimGeneration(
                requestId, actorMemberId, expectedRequestVersion, clock.instant()
        ).orElseThrow(() -> classifyRequestFailure(actorMemberId, requestId, expectedRequestVersion));

        ItineraryModels.Snapshot snapshot = itineraries.get(actorMemberId, claimed.draft().itineraryId());
        long proposalId = ids.nextId();
        try {
            ItineraryPlannerGateway.Generation generation = planner.generate(
                    actorMemberId, claimed.draft(), snapshot
            );
            PlanningModels.ValidatedProposal validated = validator.validate(
                    snapshot, claimed.draft(), generation.proposal()
            );
            PlanningRepository.ProposalRecord proposal = new PlanningRepository.ProposalRecord(
                    proposalId, requestId, snapshot.id(), actorMemberId, snapshot.version(),
                    ProposalStatus.READY, validated, "DIFY", generation.providerRunId(),
                    generation.modelName(), generation.workflowVersion(), generation.elapsedMillis(),
                    generation.totalTokens(), null
            );
            return proposalView(repository.saveReadyProposal(proposal, clock.instant()));
        } catch (PlanningException exception) {
            ProposalStatus status = providerFailure(exception.error())
                    ? ProposalStatus.FAILED : ProposalStatus.INVALID;
            PlanningRepository.ProposalRecord failed = new PlanningRepository.ProposalRecord(
                    proposalId, requestId, snapshot.id(), actorMemberId, snapshot.version(), status,
                    null, "DIFY", null, null, null, null, null, exception.error().name()
            );
            return proposalView(repository.saveFailedProposal(failed, clock.instant()));
        }
    }

    @Override
    public List<ProposalView> listProposals(long actorMemberId, long requestId) {
        requireRequest(actorMemberId, requestId);
        return repository.findProposals(requestId, actorMemberId).stream().map(this::proposalView).toList();
    }

    @Override
    public ProposalView getProposal(long actorMemberId, long proposalId) {
        return proposalView(requireProposal(actorMemberId, proposalId));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = PlanningException.class)
    public ResolutionView confirm(
            long actorMemberId,
            long proposalId,
            PlanningCommands.Confirm command
    ) {
        if (actorMemberId <= 0 || command == null) {
            throw invalidRequest();
        }
        PlanningRepository.ResolutionRecord replay = repository.findResolutionByDecision(command.decisionId())
                .orElse(null);
        if (replay != null) {
            return replayResolution(replay, actorMemberId, proposalId, true, command);
        }
        if (repository.findResolutionByProposal(proposalId).isPresent()) {
            throw new PlanningException(
                    PlanningError.IDEMPOTENCY_CONFLICT, "该建议已经由另一个决定处理"
            );
        }
        PlanningRepository.ProposalRecord proposal = requireReadyProposal(actorMemberId, proposalId);
        ItineraryModels.Snapshot snapshot = itineraries.get(actorMemberId, proposal.itineraryId());
        if (snapshot.version() != proposal.baseItineraryVersion()
                || snapshot.version() != command.expectedItineraryVersion()) {
            repository.expireProposal(proposalId, clock.instant());
            throw new PlanningException(PlanningError.PROPOSAL_EXPIRED, "行程已变化，请重新生成建议");
        }
        PlanningModels.ValidatedProposal validated = proposal.validatedProposal();
        List<PlanningModels.RevisionOperation> selected = selectedOperations(validated, command.selectedOperationKeys());
        validator.validate(snapshot, requireRequest(actorMemberId, proposal.requestId()).draft(),
                new PlanningModels.CandidateProposal(
                        validated.proposal().contractVersion(), validated.proposal().summary(),
                        selected, validated.proposal().knowledgeReferenceIds()));
        ItineraryCommands.ApplyRevision revision = toRevision(snapshot, selected);
        String selectionHash = selectionHash(selected.stream().map(
                PlanningModels.RevisionOperation::operationKey
        ).toList());
        ItineraryCommands.CommandResult result = itineraries.applyRevision(
                actorMemberId,
                proposal.itineraryId(),
                new ItineraryCommands.CommandEnvelope<>(
                        command.itineraryCommandId(), command.expectedItineraryVersion(), revision
                )
        );
        PlanningRepository.ResolutionRecord resolution = new PlanningRepository.ResolutionRecord(
                ids.nextId(), proposalId, actorMemberId, command.decisionId(), true, selectionHash,
                command.expectedItineraryVersion(), command.itineraryCommandId(), result.version()
        );
        PlanningRepository.ResolutionRecord saved = repository.saveResolution(
                resolution, ProposalStatus.CONFIRMED, clock.instant()
        );
        return new ResolutionView(saved.proposalId(), true, saved.resultVersion(), result.replayed());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResolutionView reject(long actorMemberId, long proposalId, PlanningCommands.Reject command) {
        if (actorMemberId <= 0 || command == null) {
            throw invalidRequest();
        }
        PlanningRepository.ResolutionRecord replay = repository.findResolutionByDecision(command.decisionId())
                .orElse(null);
        if (replay != null) {
            return replayResolution(replay, actorMemberId, proposalId, false, null);
        }
        if (repository.findResolutionByProposal(proposalId).isPresent()) {
            throw new PlanningException(
                    PlanningError.IDEMPOTENCY_CONFLICT, "该建议已经由另一个决定处理"
            );
        }
        requireReadyProposal(actorMemberId, proposalId);
        PlanningRepository.ResolutionRecord resolution = new PlanningRepository.ResolutionRecord(
                ids.nextId(), proposalId, actorMemberId, command.decisionId(), false,
                selectionHash(List.of()), null, null, null
        );
        PlanningRepository.ResolutionRecord saved = repository.saveResolution(
                resolution, ProposalStatus.REJECTED, clock.instant()
        );
        return new ResolutionView(saved.proposalId(), false, null, false);
    }

    private void assertDraftMatchesItinerary(long actorMemberId, PlanningModels.RequestDraft draft) {
        ItineraryModels.Snapshot snapshot = itineraries.get(actorMemberId, draft.itineraryId());
        if (draft.startDate().isBefore(snapshot.startDate())
                || draft.endDate().isAfter(snapshot.endDate())
                || !draft.budgetCurrency().equals(snapshot.baseCurrency())) {
            throw invalidRequest();
        }
    }

    private PlanningRepository.RequestRecord requireRequest(long actorMemberId, long requestId) {
        PlanningRepository.RequestRecord request = repository.findRequest(requestId)
                .orElseThrow(DefaultItineraryPlanningApplicationService::planningNotFound);
        if (request.ownerMemberId() != actorMemberId) {
            throw planningNotFound();
        }
        return request;
    }

    private PlanningRepository.ProposalRecord requireProposal(long actorMemberId, long proposalId) {
        PlanningRepository.ProposalRecord proposal = repository.findProposal(proposalId)
                .orElseThrow(DefaultItineraryPlanningApplicationService::proposalNotFound);
        if (proposal.ownerMemberId() != actorMemberId) {
            throw proposalNotFound();
        }
        return proposal;
    }

    private PlanningRepository.ProposalRecord requireReadyProposal(long actorMemberId, long proposalId) {
        PlanningRepository.ProposalRecord proposal = requireProposal(actorMemberId, proposalId);
        if (proposal.status() == ProposalStatus.EXPIRED) {
            throw new PlanningException(PlanningError.PROPOSAL_EXPIRED, "建议已经过期");
        }
        if (proposal.status() != ProposalStatus.READY || proposal.validatedProposal() == null) {
            throw new PlanningException(PlanningError.PROPOSAL_NOT_READY, "建议不可确认");
        }
        return proposal;
    }

    private RuntimeException classifyRequestFailure(long actorMemberId, long requestId, long expectedVersion) {
        PlanningRepository.RequestRecord current = requireRequest(actorMemberId, requestId);
        if (current.status() == PlanningStatus.GENERATING) {
            return new PlanningException(PlanningError.GENERATION_IN_PROGRESS, "规划正在生成");
        }
        if (current.version() != expectedVersion) {
            return new PlanningException(PlanningError.VERSION_CONFLICT, "规划请求版本冲突");
        }
        return invalidRequest();
    }

    private List<PlanningModels.RevisionOperation> selectedOperations(
            PlanningModels.ValidatedProposal proposal,
            List<String> selectedKeys
    ) {
        Set<String> selected = Set.copyOf(selectedKeys);
        Set<String> known = proposal.proposal().operations().stream()
                .map(PlanningModels.RevisionOperation::operationKey)
                .collect(java.util.stream.Collectors.toSet());
        if (!known.containsAll(selected)) {
            throw new PlanningException(PlanningError.INVALID_SELECTION, "选择包含未知建议操作");
        }
        for (String key : selected) {
            if (!selected.containsAll(proposal.dependencies().getOrDefault(key, Set.of()))) {
                throw new PlanningException(PlanningError.INVALID_SELECTION, "选择缺少依赖操作");
            }
        }
        return proposal.proposal().operations().stream()
                .filter(operation -> selected.contains(operation.operationKey()))
                .toList();
    }

    private ItineraryCommands.ApplyRevision toRevision(
            ItineraryModels.Snapshot snapshot,
            List<PlanningModels.RevisionOperation> operations
    ) {
        List<ItineraryCommands.RevisionOperation> commands = new ArrayList<>();
        for (PlanningModels.RevisionOperation operation : operations) {
            if (operation instanceof PlanningModels.AddItemOperation add) {
                commands.add(addCommand(add.operationKey(), dayId(snapshot, add.item().date()), add.item()));
            } else if (operation instanceof PlanningModels.UpdateItemOperation update) {
                PlanningModels.ItemFields item = update.item();
                commands.add(new ItineraryCommands.RevisionUpdateItem(
                        update.operationKey(), update.targetItemId(), dayId(snapshot, item.date()),
                        item.title(), item.placeName(), item.startTime(), item.endTime(), item.notes(),
                        item.estimatedCost()
                ));
            } else if (operation instanceof PlanningModels.DeleteItemOperation delete) {
                commands.add(new ItineraryCommands.RevisionDeleteItem(
                        delete.operationKey(), delete.targetItemId()
                ));
            } else if (operation instanceof PlanningModels.ReorderDayItemsOperation reorder) {
                commands.add(new ItineraryCommands.RevisionReorderItems(
                        reorder.operationKey(), dayId(snapshot, reorder.date()),
                        reorder.itemReferences().stream().map(reference -> reference.existingItemId() != null
                                ? ItineraryCommands.RevisionItemReference.existing(reference.existingItemId())
                                : ItineraryCommands.RevisionItemReference.addedBy(
                                        reference.addedByOperationKey())).toList()
                ));
            }
        }
        return new ItineraryCommands.ApplyRevision(commands);
    }

    private static ItineraryCommands.RevisionAddItem addCommand(
            String key,
            long dayId,
            PlanningModels.ItemFields item
    ) {
        return new ItineraryCommands.RevisionAddItem(
                key, dayId, item.title(), item.placeName(), item.startTime(), item.endTime(),
                item.notes(), item.estimatedCost()
        );
    }

    private static long dayId(ItineraryModels.Snapshot snapshot, LocalDate date) {
        return snapshot.days().stream().filter(day -> day.date().equals(date))
                .map(ItineraryModels.Day::id).findFirst()
                .orElseThrow(() -> new PlanningException(
                        PlanningError.DATE_OUT_OF_RANGE, "建议日期不属于当前行程"
                ));
    }

    private ResolutionView replayResolution(
            PlanningRepository.ResolutionRecord stored,
            long actorMemberId,
            long proposalId,
            boolean expectedConfirmed,
            PlanningCommands.Confirm confirm
    ) {
        boolean mismatch = stored.memberId() != actorMemberId || stored.proposalId() != proposalId
                || stored.confirmed() != expectedConfirmed;
        if (confirm != null) {
            mismatch = mismatch
                    || !confirm.itineraryCommandId().equals(stored.itineraryCommandId())
                    || confirm.expectedItineraryVersion() != stored.expectedItineraryVersion()
                    || !selectionHash(confirm.selectedOperationKeys()).equals(stored.selectedOperationsHash());
        }
        if (mismatch) {
            throw new PlanningException(PlanningError.IDEMPOTENCY_CONFLICT, "决定编号已被其他请求使用");
        }
        return new ResolutionView(stored.proposalId(), stored.confirmed(), stored.resultVersion(), true);
    }

    private ProposalView proposalView(PlanningRepository.ProposalRecord proposal) {
        return new ProposalView(
                proposal.id(), proposal.requestId(), proposal.itineraryId(),
                proposal.baseItineraryVersion(), proposal.status(), proposal.validatedProposal(),
                proposal.failureCode()
        );
    }

    private static PlanningRequestView requestView(PlanningRepository.RequestRecord request) {
        return new PlanningRequestView(
                request.id(), request.ownerMemberId(), request.version(), request.status(), request.draft()
        );
    }

    private static String selectionHash(List<String> keys) {
        try {
            List<String> ordered = new ArrayList<>(keys);
            ordered.sort(Comparator.naturalOrder());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    String.join("\n", ordered).getBytes(StandardCharsets.UTF_8)
            ));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean providerFailure(PlanningError error) {
        return error == PlanningError.PROVIDER_UNAVAILABLE
                || error == PlanningError.PROVIDER_RATE_LIMITED
                || error == PlanningError.PROVIDER_TIMEOUT;
    }

    private static PlanningException invalidRequest() {
        return new PlanningException(PlanningError.INVALID_REQUEST, "规划请求无效");
    }

    private static PlanningException planningNotFound() {
        return new PlanningException(PlanningError.PLANNING_NOT_FOUND, "规划请求不存在");
    }

    private static PlanningException proposalNotFound() {
        return new PlanningException(PlanningError.PROPOSAL_NOT_FOUND, "修订建议不存在");
    }
}
