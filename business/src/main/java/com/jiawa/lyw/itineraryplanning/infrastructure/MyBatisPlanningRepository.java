package com.jiawa.lyw.itineraryplanning.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jiawa.lyw.itinerary.application.ItineraryIdGenerator;
import com.jiawa.lyw.itineraryplanning.application.PlanningRepository;
import com.jiawa.lyw.itineraryplanning.domain.PlanningModels;
import com.jiawa.lyw.itineraryplanning.domain.PlanningStatus;
import com.jiawa.lyw.itineraryplanning.domain.ProposalStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class MyBatisPlanningRepository implements PlanningRepository {
    private static final long POSITION_GAP = 1024L;

    private final PlanningMapper mapper;
    private final ItineraryIdGenerator ids;
    private final ObjectMapper objectMapper;
    private final RevisionContractParser contractParser;

    public MyBatisPlanningRepository(
            PlanningMapper mapper,
            ItineraryIdGenerator ids,
            ObjectMapper objectMapper,
            RevisionContractParser contractParser
    ) {
        this.mapper = mapper;
        this.ids = ids;
        this.objectMapper = objectMapper.copy();
        this.contractParser = contractParser;
    }

    @Override
    @Transactional
    public RequestRecord createDraft(
            long id,
            long ownerMemberId,
            PlanningModels.RequestDraft draft,
            Instant now
    ) {
        mapper.insertRequest(requestRow(id, ownerMemberId, 1, PlanningStatus.DRAFT, draft));
        replaceDestinations(id, draft.destinations());
        return requireRequest(id);
    }

    @Override
    public Optional<RequestRecord> findRequest(long requestId) {
        PlanningRows.RequestRow row = mapper.findRequest(requestId);
        return Optional.ofNullable(row).map(this::requestRecord);
    }

    @Override
    @Transactional
    public Optional<RequestRecord> updateDraft(
            long requestId,
            long ownerMemberId,
            long expectedVersion,
            PlanningModels.RequestDraft draft,
            Instant now
    ) {
        PlanningRows.RequestRow row = requestRow(
                requestId, ownerMemberId, expectedVersion + 1, PlanningStatus.DRAFT, draft
        );
        if (mapper.updateRequestDraft(row) != 1) {
            return Optional.empty();
        }
        mapper.expireReadyProposals(requestId, null, now);
        replaceDestinations(requestId, draft.destinations());
        return findRequest(requestId);
    }

    @Override
    public Optional<RequestRecord> claimGeneration(
            long requestId,
            long ownerMemberId,
            long expectedVersion,
            Instant now
    ) {
        if (mapper.claimGeneration(requestId, ownerMemberId, expectedVersion, now) != 1) {
            return Optional.empty();
        }
        return findRequest(requestId);
    }

    @Override
    @Transactional
    public ProposalRecord saveReadyProposal(ProposalRecord proposal, Instant now) {
        mapper.expireReadyProposals(proposal.requestId(), proposal.id(), now);
        mapper.insertProposal(proposalRow(proposal));
        insertOperations(proposal);
        if (mapper.finishGeneration(
                proposal.requestId(), proposal.ownerMemberId(), PlanningStatus.READY.name(), now
        ) != 1) {
            throw new IllegalStateException("Planning request was not generating");
        }
        return requireProposal(proposal.id());
    }

    @Override
    @Transactional
    public ProposalRecord saveFailedProposal(ProposalRecord proposal, Instant now) {
        mapper.insertProposal(proposalRow(proposal));
        if (mapper.finishGeneration(
                proposal.requestId(), proposal.ownerMemberId(), PlanningStatus.FAILED.name(), now
        ) != 1) {
            throw new IllegalStateException("Planning request was not generating");
        }
        return requireProposal(proposal.id());
    }

    @Override
    public Optional<ProposalRecord> findProposal(long proposalId) {
        PlanningRows.ProposalRow row = mapper.findProposal(proposalId);
        return Optional.ofNullable(row).map(this::proposalRecord);
    }

    @Override
    public List<ProposalRecord> findProposals(long requestId, long ownerMemberId) {
        return mapper.findProposals(requestId, ownerMemberId).stream().map(this::proposalRecord).toList();
    }

    @Override
    public void expireProposal(long proposalId, Instant now) {
        mapper.expireProposal(proposalId, now);
    }

    @Override
    public Optional<ResolutionRecord> findResolutionByDecision(UUID decisionId) {
        return Optional.ofNullable(mapper.findResolutionByDecision(decisionId.toString()))
                .map(MyBatisPlanningRepository::resolutionRecord);
    }

    @Override
    public Optional<ResolutionRecord> findResolutionByProposal(long proposalId) {
        return Optional.ofNullable(mapper.findResolutionByProposal(proposalId))
                .map(MyBatisPlanningRepository::resolutionRecord);
    }

    @Override
    @Transactional
    public ResolutionRecord saveResolution(
            ResolutionRecord resolution,
            ProposalStatus proposalStatus,
            Instant now
    ) {
        try {
            mapper.insertResolution(resolutionRow(resolution));
            if (mapper.resolveProposal(resolution.proposalId(), proposalStatus.name(), now) != 1) {
                throw new IllegalStateException("Proposal is no longer ready");
            }
            return resolution;
        } catch (DuplicateKeyException conflict) {
            ResolutionRecord stored = findResolutionByDecision(resolution.decisionId())
                    .orElseGet(() -> findResolutionByProposal(resolution.proposalId()).orElseThrow(() -> conflict));
            if (!stored.equals(resolution)) {
                throw conflict;
            }
            return stored;
        }
    }

    private void replaceDestinations(long requestId, List<PlanningModels.DestinationInput> destinations) {
        mapper.deleteDestinations(requestId);
        for (int index = 0; index < destinations.size(); index++) {
            PlanningModels.DestinationInput destination = destinations.get(index);
            mapper.insertDestination(new PlanningRows.DestinationRow(
                    ids.nextId(), requestId, destination.name(), destination.countryCode(),
                    destination.timeZone().getId(), Math.multiplyExact((long) index + 1, POSITION_GAP)
            ));
        }
    }

    private void insertOperations(ProposalRecord proposal) {
        PlanningModels.ValidatedProposal validated = proposal.validatedProposal();
        if (validated == null) {
            return;
        }
        List<PlanningModels.RevisionOperation> operations = validated.proposal().operations();
        for (int index = 0; index < operations.size(); index++) {
            PlanningModels.RevisionOperation operation = operations.get(index);
            mapper.insertOperation(new PlanningRows.OperationRow(
                    ids.nextId(), proposal.id(), operation.operationKey(), operation.type().name(),
                    targetDate(operation), targetItemId(operation), operation.summary(),
                    operationPayload(operation, validated), null, "VALID",
                    Math.multiplyExact((long) index + 1, POSITION_GAP)
            ));
        }
    }

    private PlanningRows.RequestRow requestRow(
            long id,
            long ownerMemberId,
            long version,
            PlanningStatus status,
            PlanningModels.RequestDraft draft
    ) {
        return new PlanningRows.RequestRow(
                id, draft.itineraryId(), ownerMemberId, draft.schemaVersion(), draft.startDate(),
                draft.endDate(), draft.budgetAmount(), draft.budgetCurrency().getCurrencyCode(),
                draft.partySize(), json(draft.preferences()), status.name(), version
        );
    }

    private RequestRecord requestRecord(PlanningRows.RequestRow row) {
        PlanningModels.Preferences preferences = read(row.preferencesJson(), PlanningModels.Preferences.class);
        List<PlanningModels.DestinationInput> destinations = mapper.findDestinations(row.id()).stream()
                .map(destination -> new PlanningModels.DestinationInput(
                        destination.name(), destination.countryCode(), ZoneId.of(destination.timeZone())
                )).toList();
        PlanningModels.RequestDraft draft = new PlanningModels.RequestDraft(
                row.itineraryId(), row.startDate(), row.endDate(), row.budgetAmount(),
                Currency.getInstance(row.budgetCurrency()), row.partySize(), preferences, destinations
        );
        return new RequestRecord(
                row.id(), row.ownerMemberId(), row.version(), PlanningStatus.valueOf(row.status()), draft
        );
    }

    private PlanningRows.ProposalRow proposalRow(ProposalRecord proposal) {
        PlanningModels.ValidatedProposal validated = proposal.validatedProposal();
        String contractVersion = validated == null
                ? PlanningModels.REVISION_CONTRACT_V1 : validated.proposal().contractVersion();
        List<String> references = validated == null
                ? List.of() : validated.proposal().knowledgeReferenceIds();
        return new PlanningRows.ProposalRow(
                proposal.id(), proposal.requestId(), proposal.itineraryId(), proposal.ownerMemberId(),
                proposal.baseItineraryVersion(), contractVersion, proposal.status().name(),
                proposal.provider(), proposal.providerRunId(), proposal.modelName(),
                proposal.workflowVersion(), json(references), proposal.elapsedMillis(),
                proposal.totalTokens(), proposal.failureCode()
        );
    }

    private ProposalRecord proposalRecord(PlanningRows.ProposalRow row) {
        List<PlanningRows.OperationRow> operationRows = mapper.findOperations(row.id());
        PlanningModels.ValidatedProposal validated = operationRows.isEmpty()
                ? null : validatedProposal(row, operationRows);
        return new ProposalRecord(
                row.id(), row.planningRequestId(), row.itineraryId(), row.ownerMemberId(),
                row.baseItineraryVersion(), ProposalStatus.valueOf(row.status()), validated,
                row.provider(), row.providerRunId(), row.modelName(), row.workflowVersion(),
                row.elapsedMillis(), row.totalTokens(), row.failureCode()
        );
    }

    private PlanningModels.ValidatedProposal validatedProposal(
            PlanningRows.ProposalRow proposal,
            List<PlanningRows.OperationRow> rows
    ) {
        ArrayNode operations = objectMapper.createArrayNode();
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        String summary = null;
        BigDecimal projectedCost = null;
        for (PlanningRows.OperationRow row : rows) {
            JsonNode wrapper = readTree(row.payloadJson());
            operations.add(wrapper.get("operation"));
            dependencies.put(row.operationKey(), Set.copyOf(readStrings(wrapper.get("dependencies"))));
            if (summary == null) {
                summary = wrapper.path("proposal_summary").asText();
                projectedCost = wrapper.path("projected_cost").decimalValue()
                        .setScale(2, java.math.RoundingMode.UNNECESSARY);
            }
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("contract_version", proposal.contractVersion());
        root.put("summary", summary);
        root.set("operations", operations);
        root.set("knowledge_reference_ids", readTree(proposal.knowledgeReferenceIdsJson()));
        PlanningModels.CandidateProposal candidate = contractParser.parse(root.toString());
        return new PlanningModels.ValidatedProposal(candidate, projectedCost, dependencies);
    }

    private String operationPayload(
            PlanningModels.RevisionOperation operation,
            PlanningModels.ValidatedProposal validated
    ) {
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("proposal_summary", validated.proposal().summary());
        wrapper.put("projected_cost", validated.projectedCost());
        wrapper.set("dependencies", objectMapper.valueToTree(
                validated.dependencies().getOrDefault(operation.operationKey(), Set.of())
        ));
        wrapper.set("operation", operationNode(operation));
        return wrapper.toString();
    }

    private ObjectNode operationNode(PlanningModels.RevisionOperation operation) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("operation_key", operation.operationKey());
        node.put("type", operation.type().name());
        node.put("summary", operation.summary());
        if (operation instanceof PlanningModels.AddItemOperation add) {
            node.set("item", itemNode(add.item()));
        } else if (operation instanceof PlanningModels.UpdateItemOperation update) {
            node.put("target_item_id", update.targetItemId());
            node.set("item", itemNode(update.item()));
        } else if (operation instanceof PlanningModels.DeleteItemOperation delete) {
            node.put("target_item_id", delete.targetItemId());
        } else if (operation instanceof PlanningModels.ReorderDayItemsOperation reorder) {
            node.put("target_date", reorder.date().toString());
            ArrayNode references = node.putArray("item_references");
            for (PlanningModels.ItemReference reference : reorder.itemReferences()) {
                ObjectNode value = references.addObject();
                if (reference.existingItemId() != null) {
                    value.put("existing_item_id", reference.existingItemId());
                } else {
                    value.put("added_by_operation_key", reference.addedByOperationKey());
                }
            }
        }
        return node;
    }

    private ObjectNode itemNode(PlanningModels.ItemFields item) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("date", item.date().toString());
        node.put("title", item.title());
        node.put("place_name", item.placeName());
        putNullable(node, "start_time", item.startTime() == null ? null : item.startTime().toString());
        putNullable(node, "end_time", item.endTime() == null ? null : item.endTime().toString());
        putNullable(node, "notes", item.notes());
        if (item.estimatedCost() == null) {
            node.putNull("estimated_cost");
        } else {
            node.put("estimated_cost", item.estimatedCost());
        }
        return node;
    }

    private static java.time.LocalDate targetDate(PlanningModels.RevisionOperation operation) {
        if (operation instanceof PlanningModels.AddItemOperation add) return add.item().date();
        if (operation instanceof PlanningModels.UpdateItemOperation update) return update.item().date();
        if (operation instanceof PlanningModels.ReorderDayItemsOperation reorder) return reorder.date();
        return null;
    }

    private static Long targetItemId(PlanningModels.RevisionOperation operation) {
        if (operation instanceof PlanningModels.UpdateItemOperation update) return update.targetItemId();
        if (operation instanceof PlanningModels.DeleteItemOperation delete) return delete.targetItemId();
        return null;
    }

    private static PlanningRows.ResolutionRow resolutionRow(ResolutionRecord record) {
        return new PlanningRows.ResolutionRow(
                record.id(), record.proposalId(), record.memberId(), record.decisionId().toString(),
                record.confirmed() ? "CONFIRM" : "REJECT", record.selectedOperationsHash(),
                record.expectedItineraryVersion(),
                record.itineraryCommandId() == null ? null : record.itineraryCommandId().toString(),
                record.resultVersion()
        );
    }

    private static ResolutionRecord resolutionRecord(PlanningRows.ResolutionRow row) {
        return new ResolutionRecord(
                row.id(), row.proposalId(), row.memberId(), UUID.fromString(row.decisionId()),
                "CONFIRM".equals(row.decisionType()), row.selectedOperationsHash(),
                row.expectedItineraryVersion(),
                row.itineraryCommandId() == null ? null : UUID.fromString(row.itineraryCommandId()),
                row.resultVersion()
        );
    }

    private RequestRecord requireRequest(long id) {
        return findRequest(id).orElseThrow();
    }

    private ProposalRecord requireProposal(long id) {
        return findProposal(id).orElseThrow();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize planning state", exception);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read planning state", exception);
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read planning JSON", exception);
        }
    }

    private static List<String> readStrings(JsonNode array) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }

    private static void putNullable(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }
}
