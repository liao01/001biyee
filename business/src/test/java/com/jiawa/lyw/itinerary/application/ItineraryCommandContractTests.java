package com.jiawa.lyw.itinerary.application;

import com.jiawa.lyw.itinerary.domain.ItineraryError;
import com.jiawa.lyw.itinerary.domain.ItineraryException;
import com.jiawa.lyw.itinerary.domain.ItineraryStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItineraryCommandContractTests {
    private static final UUID COMMAND_ID = UUID.fromString("4d8f7c68-3420-45f9-b215-4a665a76fbfd");

    @Test
    void envelopeRequiresUuidPayloadAndOperationAppropriateVersion() {
        var create = new ItineraryCommands.CommandEnvelope<>(COMMAND_ID, 0, validCreate());
        ItineraryCommands.assertCreateEnvelope(create);

        assertInvalidItinerary(() -> new ItineraryCommands.CommandEnvelope<>(null, 0, validCreate()));
        assertInvalidItinerary(() -> new ItineraryCommands.CommandEnvelope<>(COMMAND_ID, -1, validCreate()));
        assertInvalidItinerary(() -> new ItineraryCommands.CommandEnvelope<>(COMMAND_ID, 0, null));
        assertInvalidItinerary(() -> ItineraryCommands.assertCreateEnvelope(
                new ItineraryCommands.CommandEnvelope<>(COMMAND_ID, 1, validCreate())
        ));
        assertInvalidItinerary(() -> ItineraryCommands.assertExistingEnvelope(
                new ItineraryCommands.CommandEnvelope<>(COMMAND_ID, 0, new ItineraryCommands.DeleteItem())
        ));
    }

    @Test
    void createNormalizesTextAndValidatesLengthsDatesAndDestinations() {
        var command = validCreate();

        assertEquals("上海三日游", command.title());
        assertEquals("上海", command.destinations().get(0).name());
        assertNull(command.destinations().get(0).countryCode());
        assertThrows(UnsupportedOperationException.class, () -> command.destinations().clear());

        assertInvalidItinerary(() -> new ItineraryCommands.CreateItinerary(
                " ", command.startDate(), command.endDate(), command.timeZone(),
                command.baseCurrency(), command.destinations()
        ));
        assertInvalidItinerary(() -> new ItineraryCommands.CreateItinerary(
                "x".repeat(101), command.startDate(), command.endDate(), command.timeZone(),
                command.baseCurrency(), command.destinations()
        ));
        assertInvalidItinerary(() -> new ItineraryCommands.CreateItinerary(
                command.title(), command.endDate(), command.startDate(), command.timeZone(),
                command.baseCurrency(), command.destinations()
        ));
        assertInvalidDestination(() -> new ItineraryCommands.CreateItinerary(
                command.title(), command.startDate(), command.endDate(), command.timeZone(),
                command.baseCurrency(), List.of()
        ));
    }

    @Test
    void itemCommandsValidateTextMoneyTimeAndOrdering() {
        var item = new ItineraryCommands.AddItem(
                10, " 外滩散步 ", " 外滩 ", LocalTime.of(9, 0), LocalTime.of(10, 0),
                " 看日出 ", new BigDecimal("12.30")
        );
        assertEquals("外滩散步", item.title());
        assertEquals("外滩", item.placeName());
        assertEquals("看日出", item.notes());

        assertInvalidItem(() -> new ItineraryCommands.AddItem(
                10, "安排", null, LocalTime.of(9, 0), null, null, null
        ));
        assertInvalidItem(() -> new ItineraryCommands.AddItem(
                10, "安排", null, null, null, null, new BigDecimal("-0.01")
        ));
        assertInvalidItem(() -> new ItineraryCommands.AddItem(
                10, "安排", null, null, null, null, new BigDecimal("1.001")
        ));
        assertInvalidItem(() -> new ItineraryCommands.AddItem(
                10, "x".repeat(121), null, null, null, null, null
        ));
        assertInvalidItem(() -> new ItineraryCommands.ReorderItems(10, List.of(1L, 1L)));
    }

    @Test
    void destinationAndOverviewCommandsUseTheSameFormalRules() {
        var destination = new ItineraryCommands.DestinationInput(
                10L, " 杭州 ", "CN", "Asia/Shanghai"
        );
        assertEquals("杭州", destination.name());
        assertInvalidDestination(() -> new ItineraryCommands.DestinationInput(null, "杭州", "cn", "Asia/Shanghai"));
        assertInvalidDestination(() -> new ItineraryCommands.ReplaceDestinations(List.of()));
        assertInvalidDestination(() -> new ItineraryCommands.ReplaceDestinations(List.of(destination, destination)));
        assertInvalidItinerary(() -> new ItineraryCommands.UpdateOverview(null, null, null, null, null));
        assertInvalidItinerary(() -> new ItineraryCommands.UpdateOverview(
                null, LocalDate.of(2026, 9, 2), null, null, null
        ));
    }

    @Test
    void canonicalHashIsStableForEquivalentNormalizedCommands() {
        var hasher = new ItineraryCommandHasher();
        var first = new ItineraryCommands.CommandEnvelope<>(COMMAND_ID, 0, validCreate());
        var equivalent = new ItineraryCommands.CommandEnvelope<>(
                COMMAND_ID,
                0,
                new ItineraryCommands.CreateItinerary(
                        " 上海三日游 ", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3),
                        "Asia/Shanghai", "CNY",
                        List.of(new ItineraryCommands.DestinationInput(
                                null, " 上海 ", null, "Asia/Shanghai"
                        ))
                )
        );

        assertEquals(hasher.hash("CREATE", first), hasher.hash("CREATE", equivalent));
        assertNotEquals(hasher.hash("CREATE", first), hasher.hash("UPDATE", equivalent));
        assertNotEquals(
                hasher.hash("CREATE", first),
                hasher.hash("CREATE", new ItineraryCommands.CommandEnvelope<>(
                        COMMAND_ID, 0,
                        new ItineraryCommands.CreateItinerary(
                                "不同标题", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3),
                                "Asia/Shanghai", "CNY", equivalent.payload().destinations()
                        )
                ))
        );
    }

    @Test
    void ownerOnlyPolicyUsesNotFoundForReadAndWriteIsolation() {
        ItineraryAccessPolicy policy = new OwnerOnlyItineraryAccessPolicy();
        policy.assertCanRead(42, 42);
        policy.assertCanEdit(42, 42);

        assertNotFound(() -> policy.assertCanRead(7, 42));
        assertNotFound(() -> policy.assertCanEdit(7, 42));
    }

    @Test
    void transitionPayloadRequiresTargetStatus() {
        assertEquals(
                ItineraryStatus.PLANNED,
                new ItineraryCommands.TransitionStatus(ItineraryStatus.PLANNED).toStatus()
        );
        assertInvalidItinerary(() -> new ItineraryCommands.TransitionStatus(null));
    }

    private static ItineraryCommands.CreateItinerary validCreate() {
        return new ItineraryCommands.CreateItinerary(
                " 上海三日游 ", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3),
                "Asia/Shanghai", "CNY",
                List.of(new ItineraryCommands.DestinationInput(
                        null, " 上海 ", null, "Asia/Shanghai"
                ))
        );
    }

    private static void assertInvalidItinerary(Runnable action) {
        assertError(ItineraryError.INVALID_ITINERARY, action);
    }

    private static void assertInvalidDestination(Runnable action) {
        assertError(ItineraryError.INVALID_DESTINATION, action);
    }

    private static void assertInvalidItem(Runnable action) {
        assertError(ItineraryError.INVALID_ITEM, action);
    }

    private static void assertError(ItineraryError expected, Runnable action) {
        assertEquals(expected, assertThrows(ItineraryException.class, action::run).error());
    }

    private static void assertNotFound(Runnable action) {
        assertEquals(
                ItineraryError.ITINERARY_NOT_FOUND,
                assertThrows(ItineraryException.class, action::run).error()
        );
    }
}
