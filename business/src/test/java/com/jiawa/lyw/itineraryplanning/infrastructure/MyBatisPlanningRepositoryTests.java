package com.jiawa.lyw.itineraryplanning.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.itineraryplanning.application.PlanningRepository;
import com.jiawa.lyw.itineraryplanning.domain.PlanningModels;
import com.jiawa.lyw.itineraryplanning.domain.ProposalStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyBatisPlanningRepositoryTests {
    @Test
    void duplicateDecisionReplaysTheStoredResolutionDespiteADifferentGeneratedId() {
        PlanningMapper mapper = mock(PlanningMapper.class);
        MyBatisPlanningRepository repository = new MyBatisPlanningRepository(
                mapper,
                () -> 1L,
                new ObjectMapper().findAndRegisterModules(),
                new RevisionContractParser(
                        new ObjectMapper().findAndRegisterModules(),
                        PlanningModels.REVISION_CONTRACT_V1
                )
        );
        UUID decision = UUID.fromString("00000000-0000-0000-0000-000000000311");
        UUID command = UUID.fromString("00000000-0000-0000-0000-000000000312");
        PlanningRepository.ResolutionRecord stored = new PlanningRepository.ResolutionRecord(
                300, 200, 7, decision, true, "b".repeat(64), 3L, command, 4L
        );
        PlanningRepository.ResolutionRecord retry = new PlanningRepository.ResolutionRecord(
                301, 200, 7, decision, true, "b".repeat(64), 3L, command, 4L
        );
        doThrow(new DuplicateKeyException("duplicate decision"))
                .when(mapper).insertResolution(any());
        when(mapper.findResolutionByDecision(decision.toString())).thenReturn(
                new PlanningRows.ResolutionRow(
                        stored.id(), stored.proposalId(), stored.memberId(), decision.toString(),
                        "CONFIRM", stored.selectedOperationsHash(), stored.expectedItineraryVersion(),
                        command.toString(), stored.resultVersion()
                )
        );

        assertEquals(stored, repository.saveResolution(
                retry, ProposalStatus.CONFIRMED, Instant.parse("2026-09-03T00:00:00Z")
        ));
    }
}
