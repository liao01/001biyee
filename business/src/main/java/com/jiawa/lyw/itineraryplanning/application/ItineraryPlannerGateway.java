package com.jiawa.lyw.itineraryplanning.application;

import com.jiawa.lyw.itinerary.domain.ItineraryModels;
import com.jiawa.lyw.itineraryplanning.domain.PlanningModels;

public interface ItineraryPlannerGateway {
    Generation generate(long actorMemberId, PlanningModels.RequestDraft request, ItineraryModels.Snapshot snapshot);

    record Generation(
            PlanningModels.CandidateProposal proposal,
            String providerRunId,
            String modelName,
            String workflowVersion,
            long elapsedMillis,
            Long totalTokens
    ) {
    }
}
