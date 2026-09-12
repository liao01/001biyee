package com.jiawa.lyw.itineraryplanning.application;

import com.jiawa.lyw.itineraryplanning.domain.PlanningModels;
import com.jiawa.lyw.itineraryplanning.domain.PlanningStatus;
import com.jiawa.lyw.itineraryplanning.domain.ProposalStatus;

import java.util.List;

public interface ItineraryPlanningApplicationService {
    PlanningRequestView saveDraft(long actorMemberId, PlanningCommands.SaveDraft command);

    PlanningRequestView getRequest(long actorMemberId, long requestId);

    PlanningRequestView getRequestForItinerary(long actorMemberId, long itineraryId);

    ProposalView generate(long actorMemberId, long requestId, long expectedRequestVersion);

    List<ProposalView> listProposals(long actorMemberId, long requestId);

    ProposalView getProposal(long actorMemberId, long proposalId);

    ResolutionView confirm(long actorMemberId, long proposalId, PlanningCommands.Confirm command);

    ResolutionView reject(long actorMemberId, long proposalId, PlanningCommands.Reject command);

    record PlanningRequestView(
            long id,
            long ownerMemberId,
            long version,
            PlanningStatus status,
            PlanningModels.RequestDraft draft
    ) {
    }

    record ProposalView(
            long id,
            long requestId,
            long itineraryId,
            long baseItineraryVersion,
            ProposalStatus status,
            PlanningModels.ValidatedProposal proposal,
            String failureCode
    ) {
    }

    record ResolutionView(
            long proposalId,
            boolean confirmed,
            Long resultVersion,
            boolean replayed
    ) {
    }
}
