package com.jiawa.lyw.itineraryplanning.api;

import com.jiawa.lyw.identity.application.CurrentMemberProvider;
import com.jiawa.lyw.itinerary.application.ItineraryApplicationService;
import com.jiawa.lyw.itineraryplanning.application.ItineraryPlanningApplicationService;
import com.jiawa.lyw.resp.CommonResp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@ConditionalOnProperty(prefix = "app.ai.itinerary.dify", name = "base-url")
@RequestMapping("/web/itineraries/{itineraryId}/planning")
public class ItineraryPlanningController {
    private final ItineraryPlanningApplicationService planning;
    private final CurrentMemberProvider currentMember;
    private final ItineraryApplicationService itineraries;

    public ItineraryPlanningController(
            ItineraryPlanningApplicationService planning,
            CurrentMemberProvider currentMember,
            ItineraryApplicationService itineraries
    ) {
        this.planning = planning;
        this.currentMember = currentMember;
        this.itineraries = itineraries;
    }

    @GetMapping("/request")
    public ResponseEntity<CommonResp<ItineraryPlanningHttpModels.RequestResponse>> getRequest(
            @PathVariable long itineraryId
    ) {
        return ok(ItineraryPlanningHttpModels.RequestResponse.from(
                planning.getRequestForItinerary(currentMember.memberId(), itineraryId)
        ));
    }

    @PutMapping("/request")
    public ResponseEntity<CommonResp<ItineraryPlanningHttpModels.RequestResponse>> saveRequest(
            @PathVariable long itineraryId,
            @RequestBody ItineraryPlanningHttpModels.SaveRequest request
    ) {
        return ok(ItineraryPlanningHttpModels.RequestResponse.from(
                planning.saveDraft(currentMember.memberId(), request.command(itineraryId))
        ));
    }

    @PostMapping("/generate")
    public ResponseEntity<CommonResp<ItineraryPlanningHttpModels.ProposalResponse>> generate(
            @PathVariable long itineraryId,
            @RequestBody ItineraryPlanningHttpModels.GenerateRequest request
    ) {
        long actorMemberId = currentMember.memberId();
        var current = planning.getRequestForItinerary(actorMemberId, itineraryId);
        var proposal = planning.generate(actorMemberId, current.id(), request.expectedVersion());
        return ok(ItineraryPlanningHttpModels.ProposalResponse.from(
                proposal, itineraries.get(actorMemberId, itineraryId)
        ));
    }

    @GetMapping("/proposals")
    public ResponseEntity<CommonResp<List<ItineraryPlanningHttpModels.ProposalResponse>>> proposals(
            @PathVariable long itineraryId
    ) {
        long actorMemberId = currentMember.memberId();
        var current = planning.getRequestForItinerary(actorMemberId, itineraryId);
        var snapshot = itineraries.get(actorMemberId, itineraryId);
        return ok(planning.listProposals(actorMemberId, current.id()).stream()
                .map(proposal -> ItineraryPlanningHttpModels.ProposalResponse.from(proposal, snapshot))
                .toList());
    }

    @GetMapping("/proposals/{proposalId}")
    public ResponseEntity<CommonResp<ItineraryPlanningHttpModels.ProposalResponse>> proposal(
            @PathVariable long itineraryId,
            @PathVariable long proposalId
    ) {
        long actorMemberId = currentMember.memberId();
        var result = planning.getProposal(actorMemberId, proposalId);
        if (result.itineraryId() != itineraryId) {
            throw new com.jiawa.lyw.itineraryplanning.domain.PlanningException(
                    com.jiawa.lyw.itineraryplanning.domain.PlanningError.PROPOSAL_NOT_FOUND,
                    "修订建议不存在"
            );
        }
        return ok(ItineraryPlanningHttpModels.ProposalResponse.from(
                result, itineraries.get(actorMemberId, itineraryId)
        ));
    }

    @PostMapping("/proposals/{proposalId}/confirm")
    public ResponseEntity<CommonResp<ItineraryPlanningHttpModels.ResolutionResponse>> confirm(
            @PathVariable long itineraryId,
            @PathVariable long proposalId,
            @RequestBody ItineraryPlanningHttpModels.ConfirmRequest request
    ) {
        assertProposalItinerary(itineraryId, proposalId);
        return ok(ItineraryPlanningHttpModels.ResolutionResponse.from(
                planning.confirm(currentMember.memberId(), proposalId, request.command())
        ));
    }

    @PostMapping("/proposals/{proposalId}/reject")
    public ResponseEntity<CommonResp<ItineraryPlanningHttpModels.ResolutionResponse>> reject(
            @PathVariable long itineraryId,
            @PathVariable long proposalId,
            @RequestBody ItineraryPlanningHttpModels.RejectRequest request
    ) {
        assertProposalItinerary(itineraryId, proposalId);
        return ok(ItineraryPlanningHttpModels.ResolutionResponse.from(
                planning.reject(currentMember.memberId(), proposalId, request.command())
        ));
    }

    private void assertProposalItinerary(long itineraryId, long proposalId) {
        if (planning.getProposal(currentMember.memberId(), proposalId).itineraryId() != itineraryId) {
            throw new com.jiawa.lyw.itineraryplanning.domain.PlanningException(
                    com.jiawa.lyw.itineraryplanning.domain.PlanningError.PROPOSAL_NOT_FOUND,
                    "修订建议不存在"
            );
        }
    }

    private static <T> ResponseEntity<CommonResp<T>> ok(T content) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new CommonResp<>(content));
    }
}
