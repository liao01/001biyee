package com.jiawa.lyw.itineraryplanning.application;

import com.jiawa.lyw.itinerary.application.ItineraryApplicationService;
import com.jiawa.lyw.itinerary.application.ItineraryCommands;
import com.jiawa.lyw.itinerary.domain.ItineraryModels;
import com.jiawa.lyw.itinerary.domain.ItineraryStatus;
import com.jiawa.lyw.itineraryplanning.domain.PlanningError;
import com.jiawa.lyw.itineraryplanning.domain.PlanningException;
import com.jiawa.lyw.itineraryplanning.domain.PlanningModels;
import com.jiawa.lyw.itineraryplanning.domain.PlanningStatus;
import com.jiawa.lyw.itineraryplanning.domain.ProposalStatus;
import com.jiawa.lyw.itineraryplanning.domain.RevisionProposalValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItineraryPlanningApplicationServiceTests {
    private final ItineraryApplicationService itineraries = mock(ItineraryApplicationService.class);
    private final ItineraryPlannerGateway gateway = mock(ItineraryPlannerGateway.class);
    private final FakePlanningRepository repository = new FakePlanningRepository();
    private final AtomicLong ids = new AtomicLong(100);
    private DefaultItineraryPlanningApplicationService service;

    @BeforeEach
    void setUp() {
        when(itineraries.get(7, 42)).thenReturn(snapshot(3));
        service = new DefaultItineraryPlanningApplicationService(
                repository,
                gateway,
                new RevisionProposalValidator(PlanningModels.REVISION_CONTRACT_V1, 80),
                itineraries,
                ids::getAndIncrement,
                Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void savesClaimsAndCompletesAKnowledgeGroundedProposalWithoutGivingDifyWriteAccess() {
        var saved = service.saveDraft(7, new PlanningCommands.SaveDraft(null, 0, request()));
        when(gateway.generate(eq(7L), any(), any())).thenReturn(new ItineraryPlannerGateway.Generation(
                candidate(), "run-1", "model", "workflow-v1", 250, 321L
        ));

        var proposal = service.generate(7, saved.id(), saved.version());

        assertEquals(ProposalStatus.READY, proposal.status());
        assertEquals(PlanningStatus.READY, repository.requests.get(saved.id()).status());
        assertEquals(3, repository.requests.get(saved.id()).version());
        assertEquals(List.of("kb:guide:1"),
                proposal.proposal().proposal().knowledgeReferenceIds());
        verify(itineraries, never()).applyRevision(any(Long.class), any(Long.class), any());
    }

    @Test
    void providerFailureIsPersistedAndLeavesTheFormalItineraryUntouched() {
        var saved = service.saveDraft(7, new PlanningCommands.SaveDraft(null, 0, request()));
        when(gateway.generate(eq(7L), any(), any())).thenThrow(
                new PlanningException(PlanningError.PROVIDER_RATE_LIMITED, "rate limited")
        );

        var proposal = service.generate(7, saved.id(), saved.version());

        assertEquals(ProposalStatus.FAILED, proposal.status());
        assertEquals("PROVIDER_RATE_LIMITED", proposal.failureCode());
        assertEquals(PlanningStatus.FAILED, repository.requests.get(saved.id()).status());
        verify(itineraries, never()).applyRevision(any(Long.class), any(Long.class), any());
    }

    @Test
    void confirmationEnforcesDependencyClosureAndReplaysOneAtomicItineraryCommand() {
        long proposalId = readyProposal();
        UUID decisionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID commandId = UUID.fromString("00000000-0000-0000-0000-000000000202");

        PlanningException invalid = assertThrows(PlanningException.class, () -> service.confirm(
                7, proposalId,
                new PlanningCommands.Confirm(decisionId, commandId, 3, List.of("order-day"))
        ));
        assertEquals(PlanningError.INVALID_SELECTION, invalid.error());

        when(itineraries.applyRevision(eq(7L), eq(42L), any())).thenReturn(
                new ItineraryCommands.CommandResult(42, null, 4, false)
        );
        PlanningCommands.Confirm command = new PlanningCommands.Confirm(
                decisionId, commandId, 3, List.of("add-one", "order-day")
        );
        var confirmed = service.confirm(7, proposalId, command);
        var replay = service.confirm(7, proposalId, command);

        assertEquals(4L, confirmed.resultVersion());
        assertTrue(replay.replayed());
        verify(itineraries).applyRevision(eq(7L), eq(42L), any());
    }

    @Test
    void staleProposalExpiresAndRejectNeverChangesTheItinerary() {
        long proposalId = readyProposal();
        when(itineraries.get(7, 42)).thenReturn(snapshot(4));
        PlanningException stale = assertThrows(PlanningException.class, () -> service.confirm(
                7, proposalId,
                new PlanningCommands.Confirm(
                        UUID.randomUUID(), UUID.randomUUID(), 3, List.of("add-one", "order-day")
                )
        ));
        assertEquals(PlanningError.PROPOSAL_EXPIRED, stale.error());
        assertEquals(ProposalStatus.EXPIRED, repository.proposals.get(proposalId).status());

        long second = readyProposal();
        var rejected = service.reject(7, second, new PlanningCommands.Reject(UUID.randomUUID()));
        assertEquals(false, rejected.confirmed());
        verify(itineraries, never()).applyRevision(any(Long.class), any(Long.class), any());
    }

    private long readyProposal() {
        var saved = service.saveDraft(7, new PlanningCommands.SaveDraft(null, 0, request()));
        when(gateway.generate(eq(7L), any(), any())).thenReturn(new ItineraryPlannerGateway.Generation(
                candidate(), "run-ready", null, null, 1, null
        ));
        return service.generate(7, saved.id(), saved.version()).id();
    }

    private PlanningModels.CandidateProposal candidate() {
        PlanningModels.AddItemOperation add = new PlanningModels.AddItemOperation(
                "add-one", "新增安排",
                new PlanningModels.ItemFields(
                        LocalDate.of(2026, 10, 2), "博物馆", "浙江省博物馆",
                        LocalTime.of(11, 0), LocalTime.of(12, 0), null, BigDecimal.ZERO
                )
        );
        return new PlanningModels.CandidateProposal(
                PlanningModels.REVISION_CONTRACT_V1,
                "知识库建议",
                List.of(
                        add,
                        new PlanningModels.ReorderDayItemsOperation(
                                "order-day", "调整顺序", LocalDate.of(2026, 10, 2),
                                List.of(
                                        PlanningModels.ItemReference.existing(1001),
                                        PlanningModels.ItemReference.addedBy("add-one")
                                )
                        )
                ),
                List.of("kb:guide:1")
        );
    }

    private PlanningModels.RequestDraft request() {
        return new PlanningModels.RequestDraft(
                42, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3),
                new BigDecimal("3000.00"), Currency.getInstance("CNY"), 2,
                new PlanningModels.Preferences(
                        PlanningModels.TravelPace.BALANCED,
                        Set.of(PlanningModels.PreferenceTag.CULTURE), null
                ),
                List.of(new PlanningModels.DestinationInput(
                        "杭州", "CN", ZoneId.of("Asia/Shanghai")
                ))
        );
    }

    private ItineraryModels.Snapshot snapshot(long version) {
        ItineraryModels.Item item = new ItineraryModels.Item(
                1001, 501, "西湖", "西湖", LocalTime.of(9, 0), LocalTime.of(10, 0),
                null, new BigDecimal("100.00"), 1024, null
        );
        return new ItineraryModels.Snapshot(
                42, 7, "杭州", LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3),
                ZoneId.of("Asia/Shanghai"), Currency.getInstance("CNY"), ItineraryStatus.DRAFT,
                version,
                List.of(new ItineraryModels.Destination(
                        301, "杭州", "CN", ZoneId.of("Asia/Shanghai"), 1024
                )),
                List.of(
                        new ItineraryModels.Day(501, LocalDate.of(2026, 10, 2), List.of(item)),
                        new ItineraryModels.Day(502, LocalDate.of(2026, 10, 3), List.of())
                )
        );
    }

    private static final class FakePlanningRepository implements PlanningRepository {
        private final Map<Long, RequestRecord> requests = new LinkedHashMap<>();
        private final Map<Long, ProposalRecord> proposals = new LinkedHashMap<>();
        private final Map<UUID, ResolutionRecord> decisions = new LinkedHashMap<>();
        private final Map<Long, ResolutionRecord> proposalDecisions = new LinkedHashMap<>();

        @Override
        public RequestRecord createDraft(long id, long owner, PlanningModels.RequestDraft draft, Instant now) {
            RequestRecord record = new RequestRecord(id, owner, 1, PlanningStatus.DRAFT, draft);
            requests.put(id, record);
            return record;
        }

        @Override
        public Optional<RequestRecord> findRequest(long id) { return Optional.ofNullable(requests.get(id)); }
        @Override
        public Optional<RequestRecord> findLatestRequest(long itineraryId, long owner) {
            return requests.values().stream()
                    .filter(request -> request.ownerMemberId() == owner
                            && request.draft().itineraryId() == itineraryId)
                    .reduce((first, second) -> second);
        }

        @Override
        public Optional<RequestRecord> updateDraft(long id, long owner, long expected, PlanningModels.RequestDraft draft, Instant now) {
            RequestRecord current = requests.get(id);
            if (current == null || current.ownerMemberId() != owner || current.version() != expected) return Optional.empty();
            RequestRecord next = new RequestRecord(id, owner, expected + 1, PlanningStatus.DRAFT, draft);
            requests.put(id, next);
            return Optional.of(next);
        }

        @Override
        public Optional<RequestRecord> claimGeneration(long id, long owner, long expected, Instant now) {
            RequestRecord current = requests.get(id);
            if (current == null || current.ownerMemberId() != owner || current.version() != expected
                    || current.status() == PlanningStatus.GENERATING) return Optional.empty();
            RequestRecord next = new RequestRecord(id, owner, expected + 1, PlanningStatus.GENERATING, current.draft());
            requests.put(id, next);
            return Optional.of(next);
        }

        @Override
        public ProposalRecord saveReadyProposal(ProposalRecord proposal, Instant now) {
            proposals.values().stream().filter(p -> p.requestId() == proposal.requestId() && p.status() == ProposalStatus.READY)
                    .toList().forEach(p -> proposals.put(p.id(), withStatus(p, ProposalStatus.EXPIRED)));
            proposals.put(proposal.id(), proposal);
            finish(proposal, PlanningStatus.READY);
            return proposal;
        }

        @Override
        public ProposalRecord saveFailedProposal(ProposalRecord proposal, Instant now) {
            proposals.put(proposal.id(), proposal);
            finish(proposal, PlanningStatus.FAILED);
            return proposal;
        }

        private void finish(ProposalRecord proposal, PlanningStatus status) {
            RequestRecord request = requests.get(proposal.requestId());
            requests.put(request.id(), new RequestRecord(
                    request.id(), request.ownerMemberId(), request.version() + 1, status, request.draft()
            ));
        }

        @Override public Optional<ProposalRecord> findProposal(long id) { return Optional.ofNullable(proposals.get(id)); }
        @Override public List<ProposalRecord> findProposals(long requestId, long owner) {
            return proposals.values().stream().filter(p -> p.requestId() == requestId && p.ownerMemberId() == owner).toList();
        }
        @Override public void expireProposal(long id, Instant now) {
            proposals.computeIfPresent(id, (ignored, proposal) -> withStatus(proposal, ProposalStatus.EXPIRED));
        }
        @Override public Optional<ResolutionRecord> findResolutionByDecision(UUID id) { return Optional.ofNullable(decisions.get(id)); }
        @Override public Optional<ResolutionRecord> findResolutionByProposal(long id) { return Optional.ofNullable(proposalDecisions.get(id)); }
        @Override public ResolutionRecord saveResolution(ResolutionRecord resolution, ProposalStatus status, Instant now) {
            decisions.put(resolution.decisionId(), resolution);
            proposalDecisions.put(resolution.proposalId(), resolution);
            proposals.computeIfPresent(resolution.proposalId(), (ignored, proposal) -> withStatus(proposal, status));
            return resolution;
        }

        private static ProposalRecord withStatus(ProposalRecord p, ProposalStatus status) {
            return new ProposalRecord(
                    p.id(), p.requestId(), p.itineraryId(), p.ownerMemberId(), p.baseItineraryVersion(),
                    status, p.validatedProposal(), p.provider(), p.providerRunId(), p.modelName(),
                    p.workflowVersion(), p.elapsedMillis(), p.totalTokens(), p.failureCode()
            );
        }
    }
}
