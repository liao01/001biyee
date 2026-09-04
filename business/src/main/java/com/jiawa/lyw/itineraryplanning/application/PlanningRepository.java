package com.jiawa.lyw.itineraryplanning.application;

import com.jiawa.lyw.itineraryplanning.domain.PlanningModels;
import com.jiawa.lyw.itineraryplanning.domain.PlanningStatus;
import com.jiawa.lyw.itineraryplanning.domain.ProposalStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanningRepository {
    RequestRecord createDraft(long id, long ownerMemberId, PlanningModels.RequestDraft draft, Instant now);

    Optional<RequestRecord> findRequest(long requestId);

    Optional<RequestRecord> findLatestRequest(long itineraryId, long ownerMemberId);

    Optional<RequestRecord> updateDraft(
            long requestId,
            long ownerMemberId,
            long expectedVersion,
            PlanningModels.RequestDraft draft,
            Instant now
    );

    Optional<RequestRecord> claimGeneration(
            long requestId,
            long ownerMemberId,
            long expectedVersion,
            Instant now
    );

    ProposalRecord saveReadyProposal(ProposalRecord proposal, Instant now);

    ProposalRecord saveFailedProposal(ProposalRecord proposal, Instant now);

    Optional<ProposalRecord> findProposal(long proposalId);

    List<ProposalRecord> findProposals(long requestId, long ownerMemberId);

    void expireProposal(long proposalId, Instant now);

    Optional<ResolutionRecord> findResolutionByDecision(UUID decisionId);

    Optional<ResolutionRecord> findResolutionByProposal(long proposalId);

    ResolutionRecord saveResolution(ResolutionRecord resolution, ProposalStatus proposalStatus, Instant now);

    record RequestRecord(
            long id,
            long ownerMemberId,
            long version,
            PlanningStatus status,
            PlanningModels.RequestDraft draft
    ) {
    }

    record ProposalRecord(
            long id,
            long requestId,
            long itineraryId,
            long ownerMemberId,
            long baseItineraryVersion,
            ProposalStatus status,
            PlanningModels.ValidatedProposal validatedProposal,
            String provider,
            String providerRunId,
            String modelName,
            String workflowVersion,
            Long elapsedMillis,
            Long totalTokens,
            String failureCode
    ) {
    }

    record ResolutionRecord(
            long id,
            long proposalId,
            long memberId,
            UUID decisionId,
            boolean confirmed,
            String selectedOperationsHash,
            Long expectedItineraryVersion,
            UUID itineraryCommandId,
            Long resultVersion
    ) {
    }
}
