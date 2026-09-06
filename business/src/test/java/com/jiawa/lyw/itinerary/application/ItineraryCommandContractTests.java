package com.jiawa.lyw.itinerary.application;

import com.jiawa.lyw.itinerary.domain.ItineraryError;
import com.jiawa.lyw.itinerary.domain.ItineraryException;
import com.jiawa.lyw.itinerary.domain.ItineraryModels;
import com.jiawa.lyw.itinerary.domain.ItineraryStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItineraryCommandContractTests {
    private static final UUID COMMAND_ID = UUID.fromString("4d8f7c68-3420-45f9-b215-4a665a76fbfd");

    @Test
    void validatesProviderAndManualPlaceIdentity() {
        assertDoesNotThrow(() -> new ItineraryCommands.PutItemPlace(
                ItineraryModels.PlaceSource.AMAP, "B000A", "故宫博物院", "景山前街4号",
                "110101", new BigDecimal("116.3970260"),
                new BigDecimal("39.9180580"), "GCJ02"));
        assertThrows(ItineraryException.class, () -> new ItineraryCommands.PutItemPlace(
                ItineraryModels.PlaceSource.MANUAL, "B000A", "手工地点", null,
                null, BigDecimal.ZERO, BigDecimal.ZERO, "GCJ02"));
        assertThrows(ItineraryException.class, () -> new ItineraryCommands.PutItemPlace(
                ItineraryModels.PlaceSource.AMAP, null, "故宫博物院", null,
                "110101", BigDecimal.ZERO, BigDecimal.ZERO, "GCJ02"));
    }

    @Test
    void normalizesAndValidatesFormalPlaceFields() {
        var place = new ItineraryCommands.PutItemPlace(
                ItineraryModels.PlaceSource.AMAP, " B000A ", " 故宫博物院 ", " 景山前街4号 ",
                " 110101 ", new BigDecimal("116.3970260"),
                new BigDecimal("39.9180580"), " GCJ02 "
        );

        assertEquals("B000A", place.sourcePlaceId());
        assertEquals("故宫博物院", place.name());
        assertEquals("景山前街4号", place.address());
        assertEquals("110101", place.administrativeAreaCode());
        assertEquals("GCJ02", place.coordinateSystem());
        assertNull(new ItineraryCommands.PutItemPlace(
                ItineraryModels.PlaceSource.MANUAL, " ", "手工地点", " ", " ",
                BigDecimal.ZERO, BigDecimal.ZERO, "GCJ02"
        ).sourcePlaceId());

        assertInvalidPlace(() -> new ItineraryCommands.PutItemPlace(
                null, null, "手工地点", null, null, BigDecimal.ZERO, BigDecimal.ZERO, "GCJ02"));
        assertInvalidPlace(() -> new ItineraryCommands.PutItemPlace(
                ItineraryModels.PlaceSource.AMAP, " ", "地点", null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, "GCJ02"));
        assertInvalidPlace(() -> new ItineraryCommands.PutItemPlace(
                ItineraryModels.PlaceSource.MANUAL, null, " ", null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, "GCJ02"));
        assertInvalidPlace(() -> new ItineraryCommands.PutItemPlace(
                ItineraryModels.PlaceSource.MANUAL, null, "x".repeat(201), null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, "GCJ02"));
        assertInvalidPlace(() -> new ItineraryCommands.PutItemPlace(
                ItineraryModels.PlaceSource.MANUAL, null, "地点", "x".repeat(501), null,
                BigDecimal.ZERO, BigDecimal.ZERO, "GCJ02"));
        assertInvalidPlace(() -> new ItineraryCommands.PutItemPlace(
                ItineraryModels.PlaceSource.MANUAL, null, "地点", null, "11010A",
                BigDecimal.ZERO, BigDecimal.ZERO, "GCJ02"));
        assertInvalidPlace(() -> new ItineraryCommands.PutItemPlace(
                ItineraryModels.PlaceSource.MANUAL, null, "地点", null, null,
                new BigDecimal("180.0000001"), BigDecimal.ZERO, "GCJ02"));
        assertInvalidPlace(() -> new ItineraryCommands.PutItemPlace(
                ItineraryModels.PlaceSource.MANUAL, null, "地点", null, null,
                BigDecimal.ZERO, new BigDecimal("-90.0000001"), "GCJ02"));
        assertInvalidPlace(() -> new ItineraryCommands.PutItemPlace(
                ItineraryModels.PlaceSource.MANUAL, null, "地点", null, null,
                null, BigDecimal.ZERO, "GCJ02"));
        assertInvalidPlace(() -> new ItineraryCommands.PutItemPlace(
                ItineraryModels.PlaceSource.MANUAL, null, "地点", null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, "WGS84"));
    }

    @Test
    void placeApplicationBoundaryReusesVersionOwnershipAndIdempotencyProtocol() {
        ItineraryRepository repository = mock(ItineraryRepository.class);
        AtomicLong version = new AtomicLong(7);
        Map<UUID, ItineraryRepository.StoredCommand> commands = new ConcurrentHashMap<>();
        when(repository.findSnapshotForUpdate(701L)).thenAnswer(ignored -> Optional.of(
                placeSnapshot(version.get())
        ));
        when(repository.findCommand(any(UUID.class))).thenAnswer(invocation -> Optional.ofNullable(
                commands.get(invocation.getArgument(0, UUID.class))
        ));
        doAnswer(invocation -> {
            UUID commandId = invocation.getArgument(1, UUID.class);
            commands.put(commandId, new ItineraryRepository.StoredCommand(
                    commandId,
                    invocation.getArgument(2, Long.class),
                    invocation.getArgument(3, String.class),
                    invocation.getArgument(6, String.class),
                    null,
                    null,
                    null
            ));
            return null;
        }).when(repository).reserveCommand(
                anyLong(), any(UUID.class), anyLong(), any(String.class),
                anyLong(), anyLong(), any(String.class), any(Instant.class)
        );
        when(repository.bumpVersion(anyLong(), anyLong(), anyLong(), any(Instant.class)))
                .thenAnswer(invocation -> {
                    long expected = invocation.getArgument(1, Long.class);
                    long next = invocation.getArgument(2, Long.class);
                    if (!version.compareAndSet(expected, next)) {
                        return 0;
                    }
                    return 1;
                });
        when(repository.completeCommand(any(UUID.class), anyLong(), any(), anyLong()))
                .thenAnswer(invocation -> {
                    UUID commandId = invocation.getArgument(0, UUID.class);
                    ItineraryRepository.StoredCommand reserved = commands.get(commandId);
                    commands.put(commandId, new ItineraryRepository.StoredCommand(
                            commandId, reserved.memberId(), reserved.operation(), reserved.requestHash(),
                            invocation.getArgument(1, Long.class), invocation.getArgument(2, Long.class),
                            invocation.getArgument(3, Long.class)
                    ));
                    return 1;
                });

        ItineraryPlaceApplicationService service = new DefaultItineraryApplicationService(
                repository,
                new OwnerOnlyItineraryAccessPolicy(),
                new AtomicLong(900)::incrementAndGet,
                new ItineraryCommandHasher(),
                Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC)
        );
        var firstCommand = new ItineraryCommands.CommandEnvelope<>(
                UUID.fromString("00000000-0000-0000-0000-000000000291"), 7, amapPlace("B000A")
        );
        var firstPut = service.putPlace(42, 701, 801, firstCommand);
        assertEquals(8, firstPut.version());
        assertEquals(801, firstPut.itemId());
        assertEquals(9, service.putPlace(42, 701, 801, new ItineraryCommands.CommandEnvelope<>(
                UUID.fromString("00000000-0000-0000-0000-000000000292"), 8, amapPlace("B000B")
        )).version());
        assertEquals(10, service.removePlace(42, 701, 801, new ItineraryCommands.CommandEnvelope<>(
                UUID.fromString("00000000-0000-0000-0000-000000000293"), 9,
                new ItineraryCommands.RemoveItemPlace()
        )).version());
        var removeAbsent = new ItineraryCommands.CommandEnvelope<>(
                UUID.fromString("00000000-0000-0000-0000-000000000294"), 10,
                new ItineraryCommands.RemoveItemPlace()
        );
        assertEquals(11, service.removePlace(42, 701, 801, removeAbsent).version());
        var replay = service.removePlace(42, 701, 801, removeAbsent);
        assertTrue(replay.replayed());
        assertEquals(11, replay.version());
        assertEquals(801, replay.itemId());
        assertEquals(ItineraryError.IDEMPOTENCY_CONFLICT, assertThrows(
                ItineraryException.class,
                () -> service.putPlace(42, 701, 801, new ItineraryCommands.CommandEnvelope<>(
                        firstCommand.commandId(), 11, amapPlace("DIFFERENT")
                ))
        ).error());
        assertEquals(ItineraryError.VERSION_CONFLICT, assertThrows(
                ItineraryException.class,
                () -> service.putPlace(42, 701, 801, new ItineraryCommands.CommandEnvelope<>(
                        UUID.fromString("00000000-0000-0000-0000-000000000295"), 7,
                        amapPlace("STALE")
                ))
        ).error());
        assertEquals(ItineraryError.ITINERARY_NOT_FOUND, assertThrows(
                ItineraryException.class,
                () -> service.putPlace(84, 701, 801, new ItineraryCommands.CommandEnvelope<>(
                        UUID.fromString("00000000-0000-0000-0000-000000000296"), 11,
                        amapPlace("FOREIGN-OWNER")
                ))
        ).error());
        assertEquals(ItineraryError.ITINERARY_ITEM_NOT_FOUND, assertThrows(
                ItineraryException.class,
                () -> service.putPlace(42, 701, 999, new ItineraryCommands.CommandEnvelope<>(
                        UUID.fromString("00000000-0000-0000-0000-000000000297"), 11,
                        amapPlace("FOREIGN-ITEM")
                ))
        ).error());
    }

    @Test
    void dayMapInputUsesMinimalOwnershipLookupInsteadOfLoadingPrivateSnapshot() {
        ItineraryRepository repository = mock(ItineraryRepository.class);
        var expected = new ItineraryModels.DayMapInput(
                701,
                501,
                LocalDate.of(2026, 9, 5),
                List.of(new ItineraryModels.MapItem(
                        801, "故宫", LocalTime.of(9, 0), LocalTime.of(10, 0),
                        "保留的人工地点名", null, 1024
                ))
        );
        when(repository.findOwnerMemberId(701L)).thenReturn(Optional.of(42L));
        when(repository.findDayMapInput(701L, 501L)).thenReturn(Optional.of(expected));

        ItineraryPlaceApplicationService service = new DefaultItineraryApplicationService(
                repository,
                new OwnerOnlyItineraryAccessPolicy(),
                new AtomicLong(900)::incrementAndGet,
                new ItineraryCommandHasher(),
                Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC)
        );

        assertEquals(expected, service.dayMapInput(42, 701, 501));
    }

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

    @Test
    void revisionCommandSupportsTemporaryReferencesWithoutInventingDatabaseIds() {
        var add = new ItineraryCommands.RevisionAddItem(
                "add-lunch", 501L, "午餐", "餐厅",
                LocalTime.of(11, 30), LocalTime.of(13, 0), null,
                new BigDecimal("120.00")
        );
        var reorder = new ItineraryCommands.RevisionReorderItems(
                "order-day-one",
                501L,
                List.of(
                        ItineraryCommands.RevisionItemReference.existing(1001L),
                        ItineraryCommands.RevisionItemReference.addedBy("add-lunch")
                )
        );

        var revision = new ItineraryCommands.ApplyRevision(List.of(add, reorder));

        assertEquals("add-lunch", revision.operations().get(0).operationKey());
        assertEquals("add-lunch", reorder.itemReferences().get(1).addedByOperationKey());
        assertThrows(UnsupportedOperationException.class, () -> revision.operations().clear());
    }

    @Test
    void revisionCommandRejectsMissingDuplicateAndUnsafeOperationData() {
        var delete = new ItineraryCommands.RevisionDeleteItem("delete-one", 1001L);

        assertInvalidItinerary(() -> new ItineraryCommands.ApplyRevision(List.of()));
        assertInvalidItinerary(() -> new ItineraryCommands.ApplyRevision(List.of(delete, delete)));
        assertInvalidItem(() -> new ItineraryCommands.RevisionDeleteItem("bad key", 1001L));
        assertInvalidItem(() -> new ItineraryCommands.RevisionDeleteItem("delete-one", 0L));
        assertInvalidItem(() -> new ItineraryCommands.RevisionReorderItems(
                "order", 501L,
                List.of(
                        ItineraryCommands.RevisionItemReference.existing(1001L),
                        ItineraryCommands.RevisionItemReference.existing(1001L)
                )
        ));
        assertInvalidItem(() -> new ItineraryCommands.RevisionItemReference(null, null));
        assertInvalidItem(() -> new ItineraryCommands.RevisionItemReference(1001L, "add-one"));
    }

    private static ItineraryCommands.PutItemPlace amapPlace(String sourcePlaceId) {
        return new ItineraryCommands.PutItemPlace(
                ItineraryModels.PlaceSource.AMAP, sourcePlaceId, "故宫博物院", "景山前街4号",
                "110101", new BigDecimal("116.3970260"),
                new BigDecimal("39.9180580"), "GCJ02"
        );
    }

    private static ItineraryModels.Snapshot placeSnapshot(long version) {
        return new ItineraryModels.Snapshot(
                701,
                42,
                "正式地点测试",
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 9, 5),
                ZoneId.of("Asia/Shanghai"),
                Currency.getInstance("CNY"),
                ItineraryStatus.DRAFT,
                version,
                List.of(new ItineraryModels.Destination(
                        601, "北京", "CN", ZoneId.of("Asia/Shanghai"), 1024
                )),
                List.of(new ItineraryModels.Day(
                        501,
                        LocalDate.of(2026, 9, 5),
                        List.of(new ItineraryModels.Item(
                                801, 501, "故宫", "保留地点名", LocalTime.of(9, 0),
                                LocalTime.of(10, 0), null, null, 1024, null, null
                        ))
                ))
        );
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

    private static void assertInvalidPlace(Runnable action) {
        assertError(ItineraryError.INVALID_PLACE, action);
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
