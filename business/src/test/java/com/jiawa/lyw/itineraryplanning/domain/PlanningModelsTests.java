package com.jiawa.lyw.itineraryplanning.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanningModelsTests {
    @Test
    void structuredRequestNormalizesAndCopiesEveryPlanningConstraint() {
        PlanningModels.RequestDraft draft = new PlanningModels.RequestDraft(
                42L,
                LocalDate.of(2026, 10, 2),
                LocalDate.of(2026, 10, 4),
                new BigDecimal("3000.00"),
                Currency.getInstance("CNY"),
                3,
                new PlanningModels.Preferences(
                        PlanningModels.TravelPace.BALANCED,
                        Set.of(PlanningModels.PreferenceTag.CULTURE, PlanningModels.PreferenceTag.FOOD),
                        "  不安排太早  "
                ),
                List.of(new PlanningModels.DestinationInput(
                        "  杭州  ", "cn", ZoneId.of("Asia/Shanghai")
                ))
        );

        assertEquals("杭州", draft.destinations().get(0).name());
        assertEquals("CN", draft.destinations().get(0).countryCode());
        assertEquals("不安排太早", draft.preferences().notes());
        assertEquals(PlanningModels.REQUEST_SCHEMA_V1, draft.schemaVersion());
    }

    @Test
    void structuredRequestRejectsMissingOrUnsafeConstraints() {
        PlanningModels.Preferences preferences = new PlanningModels.Preferences(
                PlanningModels.TravelPace.RELAXED,
                Set.of(PlanningModels.PreferenceTag.NATURE),
                null
        );
        PlanningModels.DestinationInput destination = new PlanningModels.DestinationInput(
                "杭州", "CN", ZoneId.of("Asia/Shanghai")
        );

        assertThrows(PlanningException.class, () -> request(
                0, new BigDecimal("100"), 2, preferences, List.of(destination)
        ));
        assertThrows(PlanningException.class, () -> request(
                42, new BigDecimal("-0.01"), 2, preferences, List.of(destination)
        ));
        assertThrows(PlanningException.class, () -> request(
                42, new BigDecimal("1.001"), 2, preferences, List.of(destination)
        ));
        assertThrows(PlanningException.class, () -> request(
                42, new BigDecimal("100"), 0, preferences, List.of(destination)
        ));
        assertThrows(PlanningException.class, () -> request(
                42, new BigDecimal("100"), 2, preferences, List.of()
        ));
        assertThrows(PlanningException.class, () -> new PlanningModels.DestinationInput(
                " ", "CN", ZoneId.of("Asia/Shanghai")
        ));
        assertThrows(PlanningException.class, () -> new PlanningModels.Preferences(
                PlanningModels.TravelPace.BALANCED, Set.of(), "x".repeat(1001)
        ));
    }

    private PlanningModels.RequestDraft request(
            long itineraryId,
            BigDecimal budget,
            int partySize,
            PlanningModels.Preferences preferences,
            List<PlanningModels.DestinationInput> destinations
    ) {
        return new PlanningModels.RequestDraft(
                itineraryId,
                LocalDate.of(2026, 10, 2),
                LocalDate.of(2026, 10, 4),
                budget,
                Currency.getInstance("CNY"),
                partySize,
                preferences,
                destinations
        );
    }
}
