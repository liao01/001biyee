package com.jiawa.lyw.itineraryplanning.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface PlanningMapper {
    void insertRequest(PlanningRows.RequestRow row);

    int updateRequestDraft(PlanningRows.RequestRow row);

    int claimGeneration(
            @Param("requestId") long requestId,
            @Param("ownerMemberId") long ownerMemberId,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now
    );

    int finishGeneration(
            @Param("requestId") long requestId,
            @Param("ownerMemberId") long ownerMemberId,
            @Param("status") String status,
            @Param("now") Instant now
    );

    PlanningRows.RequestRow findRequest(@Param("requestId") long requestId);

    void deleteDestinations(@Param("requestId") long requestId);

    void insertDestination(PlanningRows.DestinationRow row);

    List<PlanningRows.DestinationRow> findDestinations(@Param("requestId") long requestId);

    void insertProposal(PlanningRows.ProposalRow row);

    void insertOperation(PlanningRows.OperationRow row);

    int expireReadyProposals(
            @Param("requestId") long requestId,
            @Param("exceptProposalId") Long exceptProposalId,
            @Param("now") Instant now
    );

    int expireProposal(@Param("proposalId") long proposalId, @Param("now") Instant now);

    PlanningRows.ProposalRow findProposal(@Param("proposalId") long proposalId);

    List<PlanningRows.ProposalRow> findProposals(
            @Param("requestId") long requestId,
            @Param("ownerMemberId") long ownerMemberId
    );

    List<PlanningRows.OperationRow> findOperations(@Param("proposalId") long proposalId);

    PlanningRows.ResolutionRow findResolutionByDecision(@Param("decisionId") String decisionId);

    PlanningRows.ResolutionRow findResolutionByProposal(@Param("proposalId") long proposalId);

    void insertResolution(PlanningRows.ResolutionRow row);

    int resolveProposal(
            @Param("proposalId") long proposalId,
            @Param("status") String status,
            @Param("now") Instant now
    );
}
