package com.jiawa.lyw.itineraryplanning.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class PlanningRows {
    private PlanningRows() {
    }

    public record RequestRow(
            long id,
            long itineraryId,
            long ownerMemberId,
            String schemaVersion,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal budgetAmount,
            String budgetCurrency,
            int partySize,
            String preferencesJson,
            String status,
            long version
    ) {
    }

    public record DestinationRow(
            long id,
            long planningRequestId,
            String name,
            String countryCode,
            String timeZone,
            long position
    ) {
    }

    public record ProposalRow(
            long id,
            long planningRequestId,
            long itineraryId,
            long ownerMemberId,
            long baseItineraryVersion,
            String contractVersion,
            String status,
            String provider,
            String providerRunId,
            String modelName,
            String workflowVersion,
            String knowledgeReferenceIdsJson,
            Long elapsedMillis,
            Long totalTokens,
            String failureCode
    ) {
    }

    public record OperationRow(
            long id,
            long proposalId,
            String operationKey,
            String operationType,
            LocalDate targetDate,
            Long targetItemId,
            String summary,
            String payloadJson,
            BigDecimal estimatedCostDelta,
            String validationStatus,
            long position
    ) {
    }

    public record ResolutionRow(
            long id,
            long proposalId,
            long memberId,
            String decisionId,
            String decisionType,
            String selectedOperationsHash,
            Long expectedItineraryVersion,
            String itineraryCommandId,
            Long resultVersion
    ) {
    }
}
