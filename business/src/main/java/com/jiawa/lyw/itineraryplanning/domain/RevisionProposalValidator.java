package com.jiawa.lyw.itineraryplanning.domain;

import com.jiawa.lyw.itinerary.domain.ItineraryModels;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class RevisionProposalValidator {
    private static final Pattern OPERATION_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");
    private static final Pattern KNOWLEDGE_REFERENCE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9:._/-]{0,127}");

    private final String expectedContractVersion;
    private final int maxOperations;

    public RevisionProposalValidator(String expectedContractVersion, int maxOperations) {
        if (expectedContractVersion == null || expectedContractVersion.isBlank() || maxOperations < 1) {
            throw new IllegalArgumentException("校验器配置无效");
        }
        this.expectedContractVersion = expectedContractVersion;
        this.maxOperations = maxOperations;
    }

    public PlanningModels.ValidatedProposal validate(
            ItineraryModels.Snapshot snapshot,
            PlanningModels.RequestDraft request,
            PlanningModels.CandidateProposal proposal
    ) {
        validateEnvelope(snapshot, request, proposal);
        validateOperationKeys(proposal.operations());
        validateKnowledgeReferences(proposal.knowledgeReferenceIds());

        State state = State.from(snapshot);
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        Map<LocalDate, Set<String>> dayMutations = new LinkedHashMap<>();
        Set<Long> changedTargets = new HashSet<>();

        for (PlanningModels.RevisionOperation operation : proposal.operations()) {
            Set<String> operationDependencies = new LinkedHashSet<>();
            if (operation instanceof PlanningModels.AddItemOperation add) {
                validateItem(add.item(), snapshot, request);
                String reference = addedReference(add.operationKey());
                state.add(reference, null, add.item());
                rememberMutation(dayMutations, add.item().date(), add.operationKey());
            } else if (operation instanceof PlanningModels.UpdateItemOperation update) {
                requirePositiveTarget(update.targetItemId());
                requireSingleTargetChange(changedTargets, update.targetItemId());
                validateItem(update.item(), snapshot, request);
                MutableItem existing = state.existing(update.targetItemId());
                LocalDate oldDate = existing.fields.date();
                state.update(existingReference(update.targetItemId()), update.item());
                rememberMutation(dayMutations, oldDate, update.operationKey());
                rememberMutation(dayMutations, update.item().date(), update.operationKey());
            } else if (operation instanceof PlanningModels.DeleteItemOperation delete) {
                requirePositiveTarget(delete.targetItemId());
                requireSingleTargetChange(changedTargets, delete.targetItemId());
                MutableItem existing = state.existing(delete.targetItemId());
                state.delete(existingReference(delete.targetItemId()));
                rememberMutation(dayMutations, existing.fields.date(), delete.operationKey());
            } else if (operation instanceof PlanningModels.ReorderDayItemsOperation reorder) {
                validateDate(reorder.date(), snapshot, request);
                validateReorder(reorder, state, operationDependencies);
                operationDependencies.addAll(dayMutations.getOrDefault(reorder.date(), Set.of()));
            } else {
                throw error(PlanningError.INVALID_OPERATION, "不支持的修改操作");
            }
            dependencies.put(operation.operationKey(), Set.copyOf(operationDependencies));
        }

        validateNoConflicts(state);
        BigDecimal projectedCost = state.projectedCost();
        if (projectedCost.compareTo(request.budgetAmount()) > 0) {
            throw error(PlanningError.BUDGET_EXCEEDED, "建议行程预计费用超过预算");
        }
        return new PlanningModels.ValidatedProposal(proposal, projectedCost, dependencies);
    }

    private void validateEnvelope(
            ItineraryModels.Snapshot snapshot,
            PlanningModels.RequestDraft request,
            PlanningModels.CandidateProposal proposal
    ) {
        if (snapshot == null || request == null || proposal == null
                || snapshot.id() != request.itineraryId()
                || snapshot.startDate() == null || snapshot.endDate() == null
                || request.startDate().isBefore(snapshot.startDate())
                || request.endDate().isAfter(snapshot.endDate())
                || !Objects.equals(snapshot.baseCurrency(), request.budgetCurrency())) {
            throw error(PlanningError.INVALID_REQUEST, "规划请求与正式行程不一致");
        }
        if (!expectedContractVersion.equals(proposal.contractVersion())) {
            throw error(PlanningError.UNSUPPORTED_CONTRACT, "建议契约版本不受支持");
        }
        if (proposal.operations() == null || proposal.operations().isEmpty()
                || proposal.operations().stream().anyMatch(Objects::isNull)) {
            throw error(PlanningError.INVALID_OPERATION, "建议必须包含修改操作");
        }
        if (proposal.operations().size() > maxOperations) {
            throw error(PlanningError.TOO_MANY_OPERATIONS, "建议操作数量超过限制");
        }
        if (proposal.summary() == null || proposal.summary().isBlank() || proposal.summary().trim().length() > 500) {
            throw error(PlanningError.INVALID_OPERATION, "建议摘要无效");
        }
    }

    private void validateOperationKeys(List<PlanningModels.RevisionOperation> operations) {
        Set<String> keys = new HashSet<>();
        for (PlanningModels.RevisionOperation operation : operations) {
            if (operation.operationKey() == null || !OPERATION_KEY.matcher(operation.operationKey()).matches()
                    || operation.summary() == null || operation.summary().isBlank()
                    || operation.summary().trim().length() > 500) {
                throw error(PlanningError.INVALID_OPERATION, "建议操作标识或摘要无效");
            }
            if (!keys.add(operation.operationKey())) {
                throw error(PlanningError.DUPLICATE_OPERATION, "建议操作标识重复");
            }
        }
    }

    private void validateKnowledgeReferences(List<String> references) {
        if (references == null || references.size() > 50) {
            throw error(PlanningError.INVALID_KNOWLEDGE_REFERENCE, "知识引用数量无效");
        }
        Set<String> unique = new HashSet<>();
        for (String reference : references) {
            if (reference == null || !KNOWLEDGE_REFERENCE.matcher(reference).matches() || !unique.add(reference)) {
                throw error(PlanningError.INVALID_KNOWLEDGE_REFERENCE, "知识引用标识无效");
            }
        }
    }

    private void validateItem(
            PlanningModels.ItemFields item,
            ItineraryModels.Snapshot snapshot,
            PlanningModels.RequestDraft request
    ) {
        if (item == null) {
            throw error(PlanningError.INVALID_OPERATION, "行程项不能为空");
        }
        validateDate(item.date(), snapshot, request);
        if (item.title() == null || item.title().isBlank() || item.title().trim().length() > 120) {
            throw error(PlanningError.INVALID_OPERATION, "行程项标题无效");
        }
        if (item.placeName() == null || item.placeName().isBlank()
                || item.placeName().trim().length() > 200
                || item.placeName().codePoints().anyMatch(Character::isISOControl)) {
            throw error(PlanningError.INVALID_PLACE, "行程项地点无效");
        }
        if ((item.startTime() == null) != (item.endTime() == null)
                || (item.startTime() != null && !item.endTime().isAfter(item.startTime()))) {
            throw error(PlanningError.INVALID_TIME, "行程项时间无效");
        }
        if (item.notes() != null && item.notes().trim().length() > 2000) {
            throw error(PlanningError.INVALID_OPERATION, "行程项说明过长");
        }
        PlanningModels.validateMoney(item.estimatedCost(), PlanningError.INVALID_COST, "行程项费用无效");
    }

    private void validateDate(
            LocalDate date,
            ItineraryModels.Snapshot snapshot,
            PlanningModels.RequestDraft request
    ) {
        if (date == null) {
            throw error(PlanningError.INVALID_DATE, "行程日期不能为空");
        }
        if (date.isBefore(snapshot.startDate()) || date.isAfter(snapshot.endDate())
                || date.isBefore(request.startDate()) || date.isAfter(request.endDate())) {
            throw error(PlanningError.DATE_OUT_OF_RANGE, "行程日期超出规划范围");
        }
    }

    private void validateReorder(
            PlanningModels.ReorderDayItemsOperation reorder,
            State state,
            Set<String> dependencies
    ) {
        if (reorder.itemReferences() == null) {
            throw error(PlanningError.INVALID_REORDER, "排序引用不能为空");
        }
        List<String> requested = new ArrayList<>();
        for (PlanningModels.ItemReference reference : reorder.itemReferences()) {
            if (reference == null || (reference.existingItemId() == null) == (reference.addedByOperationKey() == null)) {
                throw error(PlanningError.INVALID_REORDER, "排序引用无效");
            }
            String key;
            if (reference.existingItemId() != null) {
                key = existingReference(reference.existingItemId());
            } else {
                if (!OPERATION_KEY.matcher(reference.addedByOperationKey()).matches()) {
                    throw error(PlanningError.INVALID_REORDER, "新增项排序引用无效");
                }
                key = addedReference(reference.addedByOperationKey());
                dependencies.add(reference.addedByOperationKey());
            }
            MutableItem item = state.items.get(key);
            if (item == null || !item.fields.date().equals(reorder.date())) {
                throw error(PlanningError.INVALID_REORDER, "排序引用不属于指定日期");
            }
            requested.add(key);
        }
        Set<String> requestedSet = new LinkedHashSet<>(requested);
        Set<String> activeForDay = state.referencesForDay(reorder.date());
        if (requestedSet.size() != requested.size() || !requestedSet.equals(activeForDay)) {
            throw error(PlanningError.INVALID_REORDER, "排序必须完整且不能重复");
        }
    }

    private void validateNoConflicts(State state) {
        Map<LocalDate, List<MutableItem>> byDate = new LinkedHashMap<>();
        state.items.values().forEach(item -> byDate.computeIfAbsent(item.fields.date(), ignored -> new ArrayList<>()).add(item));
        for (List<MutableItem> items : byDate.values()) {
            List<MutableItem> timed = items.stream()
                    .filter(item -> item.fields.startTime() != null && item.fields.endTime() != null)
                    .sorted(Comparator.comparing(item -> item.fields.startTime()))
                    .toList();
            for (int index = 1; index < timed.size(); index++) {
                if (timed.get(index).fields.startTime().isBefore(timed.get(index - 1).fields.endTime())) {
                    throw error(PlanningError.TIME_CONFLICT, "建议行程存在时间冲突");
                }
            }
        }
    }

    private void requirePositiveTarget(long targetItemId) {
        if (targetItemId <= 0) {
            throw error(PlanningError.ITEM_NOT_FOUND, "目标行程项不存在");
        }
    }

    private void requireSingleTargetChange(Set<Long> changedTargets, long targetItemId) {
        if (!changedTargets.add(targetItemId)) {
            throw error(PlanningError.INVALID_OPERATION, "同一行程项不能被重复修改");
        }
    }

    private static void rememberMutation(Map<LocalDate, Set<String>> mutations, LocalDate date, String key) {
        mutations.computeIfAbsent(date, ignored -> new LinkedHashSet<>()).add(key);
    }

    private static String existingReference(long id) {
        return "id:" + id;
    }

    private static String addedReference(String key) {
        return "add:" + key;
    }

    private static PlanningException error(PlanningError error, String message) {
        return new PlanningException(error, message);
    }

    private static final class State {
        private final Map<String, MutableItem> items = new LinkedHashMap<>();

        static State from(ItineraryModels.Snapshot snapshot) {
            State state = new State();
            for (ItineraryModels.Day day : snapshot.days()) {
                for (ItineraryModels.Item item : day.items()) {
                    if (!item.deleted()) {
                        state.items.put(existingReference(item.id()), new MutableItem(
                                item.id(),
                                new PlanningModels.ItemFields(
                                        day.date(), item.title(), item.placeName(), item.startTime(), item.endTime(),
                                        item.notes(), item.estimatedCost()
                                )
                        ));
                    }
                }
            }
            return state;
        }

        void add(String reference, Long existingId, PlanningModels.ItemFields fields) {
            if (items.putIfAbsent(reference, new MutableItem(existingId, fields)) != null) {
                throw error(PlanningError.DUPLICATE_OPERATION, "新增操作重复");
            }
        }

        MutableItem existing(long id) {
            MutableItem item = items.get(existingReference(id));
            if (item == null) {
                throw error(PlanningError.ITEM_NOT_FOUND, "目标行程项不存在");
            }
            return item;
        }

        void update(String reference, PlanningModels.ItemFields fields) {
            MutableItem item = items.get(reference);
            items.put(reference, new MutableItem(item.existingId, fields));
        }

        void delete(String reference) {
            items.remove(reference);
        }

        Set<String> referencesForDay(LocalDate date) {
            Set<String> result = new LinkedHashSet<>();
            items.forEach((reference, item) -> {
                if (item.fields.date().equals(date)) {
                    result.add(reference);
                }
            });
            return result;
        }

        BigDecimal projectedCost() {
            return items.values().stream()
                    .map(item -> item.fields.estimatedCost())
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private record MutableItem(Long existingId, PlanningModels.ItemFields fields) {
    }
}
