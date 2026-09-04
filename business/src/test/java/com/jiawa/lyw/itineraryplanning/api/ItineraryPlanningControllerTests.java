package com.jiawa.lyw.itineraryplanning.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.identity.application.CurrentMemberProvider;
import com.jiawa.lyw.itineraryplanning.application.ItineraryPlanningApplicationService;
import com.jiawa.lyw.itineraryplanning.application.PlanningCommands;
import com.jiawa.lyw.itineraryplanning.domain.PlanningError;
import com.jiawa.lyw.itineraryplanning.domain.PlanningException;
import com.jiawa.lyw.itineraryplanning.domain.PlanningModels;
import com.jiawa.lyw.itineraryplanning.domain.PlanningStatus;
import com.jiawa.lyw.itineraryplanning.domain.ProposalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItineraryPlanningControllerTests {
    private final ItineraryPlanningApplicationService planning = mock(ItineraryPlanningApplicationService.class);
    private final CurrentMemberProvider currentMember = mock(CurrentMemberProvider.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(currentMember.memberId()).thenReturn(7L);
        mvc = MockMvcBuilders.standaloneSetup(new ItineraryPlanningController(planning, currentMember))
                .setControllerAdvice(new ItineraryPlanningExceptionHandler())
                .build();
    }

    @Test
    void savesAPathBoundDraftWithoutAcceptingAMemberId() throws Exception {
        when(planning.saveDraft(eq(7L), any())).thenReturn(requestView());

        mvc.perform(put("/web/itineraries/42/planning/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":null,"expectedVersion":0,"draft":{
                                  "startDate":"2026-10-02","endDate":"2026-10-03",
                                  "budgetAmount":3000.00,"budgetCurrency":"CNY","partySize":2,
                                  "preferences":{"pace":"BALANCED","tags":["CULTURE"],"notes":"不早起"},
                                  "destinations":[{"name":"杭州","countryCode":"CN","timeZone":"Asia/Shanghai"}]
                                }}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.content.id").value("100"))
                .andExpect(jsonPath("$.content.itineraryId").value("42"));

        ArgumentCaptor<PlanningCommands.SaveDraft> command = ArgumentCaptor.forClass(PlanningCommands.SaveDraft.class);
        verify(planning).saveDraft(eq(7L), command.capture());
        assertEquals(42, command.getValue().draft().itineraryId());
    }

    @Test
    void exposesOnlyValidatedProposalDataAndOpaqueKnowledgeReferences() throws Exception {
        when(planning.getProposal(7, 200)).thenReturn(proposalView());

        mvc.perform(get("/web/itineraries/42/planning/proposals/200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.status").value("READY"))
                .andExpect(jsonPath("$.content.knowledgeReferenceIds[0]").value("kb:guide:1"))
                .andExpect(jsonPath("$.content.operations[0].targetItemId").value("1001"))
                .andExpect(jsonPath("$.content.providerRunId").doesNotExist())
                .andExpect(jsonPath("$.content.apiKey").doesNotExist())
                .andExpect(jsonPath("$.content.rawResponse").doesNotExist());
    }

    @Test
    void routesOwnerReadsListsAndDecisionsThroughTheApplicationBoundary() throws Exception {
        UUID confirmDecision = UUID.fromString("00000000-0000-0000-0000-000000000501");
        UUID commandId = UUID.fromString("00000000-0000-0000-0000-000000000502");
        UUID rejectDecision = UUID.fromString("00000000-0000-0000-0000-000000000503");
        when(planning.getRequestForItinerary(7, 42)).thenReturn(requestView());
        when(planning.listProposals(7, 100)).thenReturn(List.of(proposalView()));
        when(planning.getProposal(7, 200)).thenReturn(proposalView());
        when(planning.confirm(eq(7L), eq(200L), any())).thenReturn(
                new ItineraryPlanningApplicationService.ResolutionView(200, true, 4L, false)
        );
        when(planning.reject(eq(7L), eq(200L), any())).thenReturn(
                new ItineraryPlanningApplicationService.ResolutionView(200, false, null, false)
        );

        mvc.perform(get("/web/itineraries/42/planning/request"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.id").value("100"));
        mvc.perform(get("/web/itineraries/42/planning/proposals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("200"));
        mvc.perform(post("/web/itineraries/42/planning/proposals/200/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisionId":"%s","commandId":"%s",
                                 "expectedItineraryVersion":3,
                                 "selectedOperationKeys":["update-one"]}
                                """.formatted(confirmDecision, commandId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.confirmed").value(true))
                .andExpect(jsonPath("$.content.resultVersion").value(4));
        mvc.perform(post("/web/itineraries/42/planning/proposals/200/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decisionId\":\"" + rejectDecision + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.confirmed").value(false));

        verify(planning, times(2)).getRequestForItinerary(7, 42);
        verify(planning).listProposals(7, 100);
        verify(planning).confirm(eq(7L), eq(200L), any(PlanningCommands.Confirm.class));
        verify(planning).reject(eq(7L), eq(200L), any(PlanningCommands.Reject.class));
    }

    @Test
    void hidesProposalExistenceWhenThePathItineraryDoesNotMatch() throws Exception {
        when(planning.getProposal(7, 200)).thenReturn(proposalView());

        mvc.perform(get("/web/itineraries/99/planning/proposals/200"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.content.errorCode").value("PROPOSAL_NOT_FOUND"));
    }

    @Test
    void mapsProviderAndContractFailuresToStableSafeErrors() throws Exception {
        when(planning.getRequestForItinerary(7, 42)).thenReturn(requestView());
        when(planning.generate(7, 100, 1)).thenThrow(
                new PlanningException(PlanningError.PROVIDER_RATE_LIMITED, "secret provider details")
        );

        mvc.perform(post("/web/itineraries/42/planning/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.content.errorCode").value("PROVIDER_RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value("AI 规划请求过多，请稍后重试"));
    }

    @Test
    void rejectsMalformedBodiesWithoutLeakingStackOrSupplierData() throws Exception {
        mvc.perform(put("/web/itineraries/42/planning/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.content.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    private ItineraryPlanningApplicationService.PlanningRequestView requestView() {
        return new ItineraryPlanningApplicationService.PlanningRequestView(
                100, 7, 1, PlanningStatus.DRAFT, request()
        );
    }

    private ItineraryPlanningApplicationService.ProposalView proposalView() {
        PlanningModels.UpdateItemOperation update = new PlanningModels.UpdateItemOperation(
                "update-one", "调整安排", 1001,
                new PlanningModels.ItemFields(
                        LocalDate.of(2026, 10, 2), "西湖", "西湖",
                        LocalTime.of(9, 0), LocalTime.of(10, 0), null,
                        new BigDecimal("100.00")
                )
        );
        PlanningModels.CandidateProposal candidate = new PlanningModels.CandidateProposal(
                PlanningModels.REVISION_CONTRACT_V1, "知识库建议", List.of(update),
                List.of("kb:guide:1")
        );
        return new ItineraryPlanningApplicationService.ProposalView(
                200, 100, 42, 3, ProposalStatus.READY,
                new PlanningModels.ValidatedProposal(
                        candidate, new BigDecimal("100.00"), Map.of("update-one", Set.of())
                ),
                null
        );
    }

    private PlanningModels.RequestDraft request() {
        return new PlanningModels.RequestDraft(
                42, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3),
                new BigDecimal("3000.00"), Currency.getInstance("CNY"), 2,
                new PlanningModels.Preferences(
                        PlanningModels.TravelPace.BALANCED,
                        Set.of(PlanningModels.PreferenceTag.CULTURE), "不早起"
                ),
                List.of(new PlanningModels.DestinationInput(
                        "杭州", "CN", ZoneId.of("Asia/Shanghai")
                ))
        );
    }
}
