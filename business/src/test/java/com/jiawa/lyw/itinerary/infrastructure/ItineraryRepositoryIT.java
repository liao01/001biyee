package com.jiawa.lyw.itinerary.infrastructure;

import com.jiawa.lyw.itinerary.application.ItineraryApplicationService;
import com.jiawa.lyw.itinerary.application.ItineraryCommands;
import com.jiawa.lyw.itinerary.application.ItineraryIdGenerator;
import com.jiawa.lyw.itinerary.domain.ItineraryError;
import com.jiawa.lyw.itinerary.domain.ItineraryException;
import com.jiawa.lyw.support.MySqlIntegrationDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ItineraryRepositoryIT.Config.class)
class ItineraryRepositoryIT {
    private static final MySqlIntegrationDatabase DATABASE = new MySqlIntegrationDatabase();
    private static final String TEST_PREFIX = "IT-TEST-#17-";

    @Autowired
    private ItineraryApplicationService itineraries;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private TestIds ids;
    private JdbcTemplate jdbc;

    @BeforeEach
    void prepareBatch() {
        jdbc = new JdbcTemplate(dataSource);
        cleanupRows();
        ids.reset();
        jdbc.update(
                "INSERT INTO member (id, name, email, email_verified_at, account_status) "
                        + "VALUES (42, ?, 'itinerary-owner@example.com', CURRENT_TIMESTAMP(3), 'ACTIVE'), "
                        + "(84, ?, 'itinerary-other@example.com', CURRENT_TIMESTAMP(3), 'ACTIVE')",
                TEST_PREFIX + "owner",
                TEST_PREFIX + "other"
        );
    }

    @AfterEach
    void removeBatchAndVerify() {
        cleanupRows();
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM member WHERE name LIKE 'IT-TEST-#17-%'", Integer.class
        ));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM itinerary", Integer.class));
    }

    @AfterAll
    static void removeIsolatedDatabase() {
        DATABASE.close();
    }

    @Test
    void createsAndReopensNormalizedAggregate() {
        var result = itineraries.create(42, createCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000101"), "上海行程"
        ));

        assertEquals(1, result.version());
        assertFalse(result.replayed());
        var snapshot = itineraries.get(42, result.itineraryId());
        assertEquals(TEST_PREFIX + "上海行程", snapshot.title());
        assertEquals(LocalDate.of(2026, 9, 1), snapshot.startDate());
        assertEquals(LocalDate.of(2026, 9, 3), snapshot.endDate());
        assertEquals("Asia/Shanghai", snapshot.timeZone().getId());
        assertEquals("CNY", snapshot.baseCurrency().getCurrencyCode());
        assertEquals(List.of("上海", "苏州"),
                snapshot.destinations().stream().map(destination -> destination.name()).toList());
        assertEquals(List.of(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 2),
                        LocalDate.of(2026, 9, 3)),
                snapshot.days().stream().map(day -> day.date()).toList());
        assertTrue(snapshot.days().stream().allMatch(day -> day.items().isEmpty()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM itinerary_command", Integer.class));
    }

    @Test
    void sameCommandReplaysButChangedPayloadConflicts() {
        UUID commandId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        var first = itineraries.create(42, createCommand(commandId, "幂等行程"));
        var replay = itineraries.create(42, createCommand(commandId, "幂等行程"));

        assertEquals(first.itineraryId(), replay.itineraryId());
        assertEquals(first.version(), replay.version());
        assertTrue(replay.replayed());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM itinerary", Integer.class));

        var conflict = assertThrows(
                ItineraryException.class,
                () -> itineraries.create(42, createCommand(commandId, "不同载荷"))
        );
        assertEquals(ItineraryError.IDEMPOTENCY_CONFLICT, conflict.error());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM itinerary", Integer.class));
    }

    @Test
    void concurrentDuplicateCreateHasOneAggregateAndOneReplay() throws Exception {
        UUID commandId = UUID.fromString("00000000-0000-0000-0000-000000000103");
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        java.util.concurrent.Callable<ItineraryCommands.CommandResult> attempt = () -> {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS));
            return itineraries.create(42, createCommand(commandId, "并发行程"));
        };
        try {
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            var results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(results.get(0).itineraryId(), results.get(1).itineraryId());
            assertEquals(1, results.stream().filter(ItineraryCommands.CommandResult::replayed).count());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM itinerary", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM itinerary_command", Integer.class));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void ownerIsolationAndStableCursorPaginationUseTheFormalReadPath() {
        long firstId = itineraries.create(42, createCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000104"), "第一程"
        )).itineraryId();
        long secondId = itineraries.create(42, createCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000105"), "第二程"
        )).itineraryId();
        long thirdId = itineraries.create(42, createCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000106"), "第三程"
        )).itineraryId();

        var firstPage = itineraries.list(42, Set.of(), null, 2);
        assertEquals(List.of(thirdId, secondId),
                firstPage.items().stream().map(summary -> summary.id()).toList());
        assertNotNull(firstPage.nextCursor());
        var secondPage = itineraries.list(42, Set.of(), firstPage.nextCursor(), 2);
        assertEquals(List.of(firstId), secondPage.items().stream().map(summary -> summary.id()).toList());
        assertEquals(null, secondPage.nextCursor());

        assertTrue(itineraries.list(84, Set.of(), null, 20).items().isEmpty());
        var missing = assertThrows(ItineraryException.class, () -> itineraries.get(84, firstId));
        assertEquals(ItineraryError.ITINERARY_NOT_FOUND, missing.error());
        assertEquals(
                missing.error(),
                assertThrows(ItineraryException.class, () -> itineraries.get(42, Long.MAX_VALUE)).error()
        );
    }

    @Test
    void detailAssemblyExcludesSoftDeletedItemsWithoutCartesianDuplication() {
        long itineraryId = itineraries.create(42, createCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000107"), "详情行程"
        )).itineraryId();
        Long dayId = jdbc.queryForObject(
                "SELECT id FROM itinerary_day WHERE itinerary_id = ? ORDER BY day_date LIMIT 1",
                Long.class,
                itineraryId
        );
        jdbc.update(
                "INSERT INTO itinerary_item "
                        + "(id, itinerary_id, itinerary_day_id, title, start_time, end_time, position) "
                        + "VALUES (7001, ?, ?, 'IT-TEST-#17-live', '09:00', '10:00', 1024), "
                        + "(7002, ?, ?, 'IT-TEST-#17-deleted', NULL, NULL, 2048)",
                itineraryId, dayId, itineraryId, dayId
        );
        jdbc.update("UPDATE itinerary_item SET deleted_at = CURRENT_TIMESTAMP(3) WHERE id = 7002");

        var snapshot = itineraries.get(42, itineraryId);

        assertEquals(1, snapshot.days().get(0).items().size());
        assertEquals("IT-TEST-#17-live", snapshot.days().get(0).items().get(0).title());
        assertEquals(2, snapshot.destinations().size());
    }

    @Test
    void overviewUpdateExtendsAndSafelyShrinksDaysWithOneVersionPerCommand() {
        long itineraryId = itineraries.create(42, createCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000108"), "日期调整"
        )).itineraryId();
        List<Long> originalDayIds = itineraries.get(42, itineraryId).days().stream()
                .map(day -> day.id()).toList();

        var extended = itineraries.updateOverview(
                42,
                itineraryId,
                envelope(
                        "00000000-0000-0000-0000-000000000109",
                        1,
                        new ItineraryCommands.UpdateOverview(
                                " IT-TEST-#17-延长后 ",
                                LocalDate.of(2026, 9, 1),
                                LocalDate.of(2026, 9, 5),
                                "Asia/Tokyo",
                                "JPY"
                        )
                )
        );
        assertEquals(2, extended.version());
        var extendedSnapshot = itineraries.get(42, itineraryId);
        assertEquals(TEST_PREFIX + "延长后", extendedSnapshot.title());
        assertEquals("Asia/Tokyo", extendedSnapshot.timeZone().getId());
        assertEquals("JPY", extendedSnapshot.baseCurrency().getCurrencyCode());
        assertEquals(5, extendedSnapshot.days().size());
        assertEquals(originalDayIds, extendedSnapshot.days().subList(0, 3).stream()
                .map(day -> day.id()).toList());

        var shrunk = itineraries.updateOverview(
                42,
                itineraryId,
                envelope(
                        "00000000-0000-0000-0000-000000000110",
                        2,
                        new ItineraryCommands.UpdateOverview(
                                null,
                                LocalDate.of(2026, 9, 1),
                                LocalDate.of(2026, 9, 2),
                                null,
                                null
                        )
                )
        );
        assertEquals(3, shrunk.version());
        assertEquals(originalDayIds.subList(0, 2), itineraries.get(42, itineraryId).days().stream()
                .map(day -> day.id()).toList());
    }

    @Test
    void shrinkingAcrossLiveItemsAndStaleVersionsRollsBackEverything() {
        long itineraryId = itineraries.create(42, createCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000111"), "回滚验证"
        )).itineraryId();
        Long lastDayId = jdbc.queryForObject(
                "SELECT id FROM itinerary_day WHERE itinerary_id = ? ORDER BY day_date DESC LIMIT 1",
                Long.class,
                itineraryId
        );
        jdbc.update(
                "INSERT INTO itinerary_item "
                        + "(id, itinerary_id, itinerary_day_id, title, position) "
                        + "VALUES (7101, ?, ?, 'IT-TEST-#17-protected', 1024)",
                itineraryId,
                lastDayId
        );

        var protectedRange = assertThrows(
                ItineraryException.class,
                () -> itineraries.updateOverview(
                        42,
                        itineraryId,
                        envelope(
                                "00000000-0000-0000-0000-000000000112",
                                1,
                                new ItineraryCommands.UpdateOverview(
                                        "不应保存",
                                        LocalDate.of(2026, 9, 1),
                                        LocalDate.of(2026, 9, 2),
                                        null,
                                        null
                                )
                        )
                )
        );
        assertEquals(ItineraryError.DATE_RANGE_CONTAINS_ITEMS, protectedRange.error());
        assertEquals(1, itineraries.get(42, itineraryId).version());
        assertEquals(3, itineraries.get(42, itineraryId).days().size());

        itineraries.updateOverview(
                42,
                itineraryId,
                envelope(
                        "00000000-0000-0000-0000-000000000113",
                        1,
                        new ItineraryCommands.UpdateOverview("正式标题", null, null, null, null)
                )
        );
        var stale = assertThrows(
                ItineraryException.class,
                () -> itineraries.updateOverview(
                        42,
                        itineraryId,
                        envelope(
                                "00000000-0000-0000-0000-000000000114",
                                1,
                                new ItineraryCommands.UpdateOverview("过期标题", null, null, null, null)
                        )
                )
        );
        assertEquals(ItineraryError.VERSION_CONFLICT, stale.error());
        assertEquals("正式标题", itineraries.get(42, itineraryId).title());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM itinerary_command", Integer.class));
    }

    @Test
    void destinationReplacementIsAtomicOrderedAndIdempotent() {
        long itineraryId = itineraries.create(42, createCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000115"), "目的地调整"
        )).itineraryId();
        long retainedId = itineraries.get(42, itineraryId).destinations().get(1).id();
        var command = envelope(
                "00000000-0000-0000-0000-000000000116",
                1,
                new ItineraryCommands.ReplaceDestinations(List.of(
                        new ItineraryCommands.DestinationInput(
                                retainedId, "苏州", "CN", "Asia/Shanghai"
                        ),
                        new ItineraryCommands.DestinationInput(
                                null, "杭州", "CN", "Asia/Shanghai"
                        )
                ))
        );

        var first = itineraries.replaceDestinations(42, itineraryId, command);
        var replay = itineraries.replaceDestinations(42, itineraryId, command);

        assertEquals(2, first.version());
        assertTrue(replay.replayed());
        var destinations = itineraries.get(42, itineraryId).destinations();
        assertEquals(List.of("苏州", "杭州"), destinations.stream().map(destination -> destination.name()).toList());
        assertEquals(List.of(1024L, 2048L), destinations.stream().map(destination -> destination.position()).toList());
        assertEquals(retainedId, destinations.get(0).id());
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM itinerary_destination WHERE itinerary_id = ?",
                Integer.class,
                itineraryId
        ));
    }

    @Test
    void itemCommandsAddUntimedAndAdjacentTimedItemsButRejectOverlap() {
        long itineraryId = itineraries.create(42, createCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000117"), "安排新增"
        )).itineraryId();
        long dayId = itineraries.get(42, itineraryId).days().get(0).id();

        var untimed = itineraries.addItem(
                42,
                itineraryId,
                envelope(
                        "00000000-0000-0000-0000-000000000118",
                        1,
                        new ItineraryCommands.AddItem(
                                dayId, "自由活动", null, null, null, null, null
                        )
                )
        );
        assertNotNull(untimed.itemId());
        assertEquals(2, untimed.version());
        var morning = itineraries.addItem(
                42,
                itineraryId,
                envelope(
                        "00000000-0000-0000-0000-000000000119",
                        2,
                        new ItineraryCommands.AddItem(
                                dayId, "早餐", "餐厅", java.time.LocalTime.of(9, 0),
                                java.time.LocalTime.of(10, 0), null, new java.math.BigDecimal("28.00")
                        )
                )
        );
        var adjacent = itineraries.addItem(
                42,
                itineraryId,
                envelope(
                        "00000000-0000-0000-0000-000000000120",
                        3,
                        new ItineraryCommands.AddItem(
                                dayId, "散步", "街区", java.time.LocalTime.of(10, 0),
                                java.time.LocalTime.of(11, 0), null, null
                        )
                )
        );
        assertEquals(4, adjacent.version());

        var overlap = assertThrows(
                ItineraryException.class,
                () -> itineraries.addItem(
                        42,
                        itineraryId,
                        envelope(
                                "00000000-0000-0000-0000-000000000121",
                                4,
                                new ItineraryCommands.AddItem(
                                        dayId, "冲突安排", null, java.time.LocalTime.of(9, 30),
                                        java.time.LocalTime.of(10, 30), null, null
                                )
                        )
                )
        );
        assertEquals(ItineraryError.TIME_CONFLICT, overlap.error());
        assertEquals(4, itineraries.get(42, itineraryId).version());
        assertEquals(3, itineraries.get(42, itineraryId).days().get(0).items().size());
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM itinerary_item WHERE itinerary_id = ?", Integer.class, itineraryId
        ));
        assertTrue(jdbc.queryForObject(
                "SELECT position FROM itinerary_item WHERE id = ?", Long.class, morning.itemId()
        ) > 0);
    }

    @Test
    void itemUpdateCanMoveDaysIgnoresItselfAndRejectsForeignDay() {
        long itineraryId = itineraries.create(42, createCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000122"), "安排移动"
        )).itineraryId();
        var days = itineraries.get(42, itineraryId).days();
        long firstDay = days.get(0).id();
        long secondDay = days.get(1).id();
        var created = itineraries.addItem(
                42,
                itineraryId,
                envelope(
                        "00000000-0000-0000-0000-000000000123",
                        1,
                        new ItineraryCommands.AddItem(
                                firstDay, "上午行程", null, java.time.LocalTime.of(9, 0),
                                java.time.LocalTime.of(10, 0), null, null
                        )
                )
        );

        var updated = itineraries.updateItem(
                42,
                itineraryId,
                created.itemId(),
                envelope(
                        "00000000-0000-0000-0000-000000000124",
                        2,
                        new ItineraryCommands.UpdateItem(
                                secondDay, "移动后", "新地点", java.time.LocalTime.of(9, 0),
                                java.time.LocalTime.of(10, 0), "新备注", null
                        )
                )
        );
        assertEquals(3, updated.version());
        var snapshot = itineraries.get(42, itineraryId);
        assertTrue(snapshot.days().get(0).items().isEmpty());
        assertEquals("移动后", snapshot.days().get(1).items().get(0).title());

        long otherItinerary = itineraries.create(42, createCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000125"), "其他行程"
        )).itineraryId();
        long foreignDay = itineraries.get(42, otherItinerary).days().get(0).id();
        var foreign = assertThrows(
                ItineraryException.class,
                () -> itineraries.updateItem(
                        42,
                        itineraryId,
                        created.itemId(),
                        envelope(
                                "00000000-0000-0000-0000-000000000126",
                                3,
                                new ItineraryCommands.UpdateItem(
                                        foreignDay, "非法移动", null, null, null, null, null
                                )
                        )
                )
        );
        assertEquals(ItineraryError.INVALID_ITEM, foreign.error());
        assertEquals(3, itineraries.get(42, itineraryId).version());
    }

    @Test
    void deleteIsSoftIdempotentAndReorderRequiresTheCompleteLivePermutation() {
        long itineraryId = itineraries.create(42, createCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000127"), "删除排序"
        )).itineraryId();
        long dayId = itineraries.get(42, itineraryId).days().get(0).id();
        var first = itineraries.addItem(42, itineraryId, envelope(
                "00000000-0000-0000-0000-000000000128", 1,
                new ItineraryCommands.AddItem(dayId, "第一项", null, null, null, null, null)
        ));
        var second = itineraries.addItem(42, itineraryId, envelope(
                "00000000-0000-0000-0000-000000000129", 2,
                new ItineraryCommands.AddItem(dayId, "第二项", null, null, null, null, null)
        ));
        var third = itineraries.addItem(42, itineraryId, envelope(
                "00000000-0000-0000-0000-000000000130", 3,
                new ItineraryCommands.AddItem(dayId, "第三项", null, null, null, null, null)
        ));

        var incomplete = assertThrows(
                ItineraryException.class,
                () -> itineraries.reorderItems(42, itineraryId, envelope(
                        "00000000-0000-0000-0000-000000000131", 4,
                        new ItineraryCommands.ReorderItems(dayId, List.of(first.itemId(), second.itemId()))
                ))
        );
        assertEquals(ItineraryError.INVALID_ITEM, incomplete.error());
        var reordered = itineraries.reorderItems(42, itineraryId, envelope(
                "00000000-0000-0000-0000-000000000132", 4,
                new ItineraryCommands.ReorderItems(
                        dayId, List.of(third.itemId(), first.itemId(), second.itemId())
                )
        ));
        assertEquals(5, reordered.version());
        assertEquals(List.of("第三项", "第一项", "第二项"),
                itineraries.get(42, itineraryId).days().get(0).items().stream()
                        .map(item -> item.title()).toList());

        var deleteCommand = envelope(
                "00000000-0000-0000-0000-000000000133", 5, new ItineraryCommands.DeleteItem()
        );
        var deleted = itineraries.deleteItem(42, itineraryId, first.itemId(), deleteCommand);
        var replay = itineraries.deleteItem(42, itineraryId, first.itemId(), deleteCommand);
        assertEquals(6, deleted.version());
        assertTrue(replay.replayed());
        assertEquals(List.of("第三项", "第二项"),
                itineraries.get(42, itineraryId).days().get(0).items().stream()
                        .map(item -> item.title()).toList());
        assertNotNull(jdbc.queryForObject(
                "SELECT deleted_at FROM itinerary_item WHERE id = ?",
                java.sql.Timestamp.class,
                first.itemId()
        ));
    }

    @Test
    void revisionAppliesAtomicallyOnceAndReplaysWithTemporaryItemReferences() {
        long itineraryId = itineraries.create(42, createCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000142"),
                "IT-TEST-#18-批量修订"
        )).itineraryId();
        var initial = itineraries.get(42, itineraryId);
        long dayOne = initial.days().get(0).id();
        long dayTwo = initial.days().get(1).id();
        var existing = itineraries.addItem(42, itineraryId, envelope(
                "00000000-0000-0000-0000-000000000143", 1,
                new ItineraryCommands.AddItem(
                        dayOne, "原安排", "西湖", LocalTime.of(9, 0), LocalTime.of(10, 0),
                        null, new BigDecimal("100.00")
                )
        ));
        var revision = envelope(
                "00000000-0000-0000-0000-000000000144",
                2,
                new ItineraryCommands.ApplyRevision(List.of(
                        new ItineraryCommands.RevisionUpdateItem(
                                "move-existing", existing.itemId(), dayTwo, "移动后的安排", "西湖",
                                LocalTime.of(8, 0), LocalTime.of(9, 0), null,
                                new BigDecimal("80.00")
                        ),
                        new ItineraryCommands.RevisionAddItem(
                                "add-replacement", dayOne, "新增安排", "博物馆",
                                LocalTime.of(9, 0), LocalTime.of(10, 0), null,
                                new BigDecimal("20.00")
                        ),
                        new ItineraryCommands.RevisionReorderItems(
                                "order-day-one", dayOne,
                                List.of(ItineraryCommands.RevisionItemReference.addedBy("add-replacement"))
                        )
                ))
        );

        var applied = itineraries.applyRevision(42, itineraryId, revision);
        var replay = itineraries.applyRevision(42, itineraryId, revision);

        assertEquals(3, applied.version());
        assertTrue(replay.replayed());
        var revised = itineraries.get(42, itineraryId);
        assertEquals(List.of("新增安排"),
                revised.days().get(0).items().stream().map(item -> item.title()).toList());
        assertEquals(List.of("移动后的安排"),
                revised.days().get(1).items().stream().map(item -> item.title()).toList());
        assertEquals(3, revised.version());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM itinerary_command", Integer.class));

        var conflict = assertThrows(
                ItineraryException.class,
                () -> itineraries.applyRevision(42, itineraryId, envelope(
                        revision.commandId().toString(),
                        3,
                        new ItineraryCommands.ApplyRevision(List.of(
                                new ItineraryCommands.RevisionDeleteItem("different", existing.itemId())
                        ))
                ))
        );
        assertEquals(ItineraryError.IDEMPOTENCY_CONFLICT, conflict.error());
    }

    @Test
    void invalidRevisionRollsBackEveryMutationAndCommandReservation() {
        long itineraryId = itineraries.create(42, createCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000145"),
                "IT-TEST-#18-回滚"
        )).itineraryId();
        long dayId = itineraries.get(42, itineraryId).days().get(0).id();
        itineraries.addItem(42, itineraryId, envelope(
                "00000000-0000-0000-0000-000000000146", 1,
                new ItineraryCommands.AddItem(
                        dayId, "原安排", "外滩", LocalTime.of(9, 0), LocalTime.of(10, 0),
                        null, BigDecimal.ZERO
                )
        ));
        var command = envelope(
                "00000000-0000-0000-0000-000000000147",
                2,
                new ItineraryCommands.ApplyRevision(List.of(
                        new ItineraryCommands.RevisionAddItem(
                                "add-conflict", dayId, "冲突安排", "外滩",
                                LocalTime.of(9, 30), LocalTime.of(10, 30), null, BigDecimal.ZERO
                        )
                ))
        );

        var conflict = assertThrows(
                ItineraryException.class,
                () -> itineraries.applyRevision(42, itineraryId, command)
        );

        assertEquals(ItineraryError.TIME_CONFLICT, conflict.error());
        var unchanged = itineraries.get(42, itineraryId);
        assertEquals(2, unchanged.version());
        assertEquals(List.of("原安排"),
                unchanged.days().get(0).items().stream().map(item -> item.title()).toList());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM itinerary_command WHERE command_id = ?",
                Integer.class,
                command.commandId().toString()
        ));
    }

    @Test
    void revisionRequiresOwnerAndAnEditableLifecycleState() {
        long itineraryId = itineraries.create(42, createCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000148"),
                "IT-TEST-#18-权限状态"
        )).itineraryId();
        long dayId = itineraries.get(42, itineraryId).days().get(0).id();
        var revision = envelope(
                "00000000-0000-0000-0000-000000000149",
                1,
                new ItineraryCommands.ApplyRevision(List.of(
                        new ItineraryCommands.RevisionAddItem(
                                "owner-only", dayId, "安排", "地点",
                                null, null, null, BigDecimal.ZERO
                        )
                ))
        );

        var foreign = assertThrows(
                ItineraryException.class,
                () -> itineraries.applyRevision(84, itineraryId, revision)
        );
        assertEquals(ItineraryError.ITINERARY_NOT_FOUND, foreign.error());

        itineraries.transition(42, itineraryId, envelope(
                "00000000-0000-0000-0000-000000000150",
                1,
                new ItineraryCommands.TransitionStatus(
                        com.jiawa.lyw.itinerary.domain.ItineraryStatus.CANCELLED
                )
        ));
        var cancelledRevision = envelope(
                "00000000-0000-0000-0000-000000000151",
                2,
                revision.payload()
        );
        var cancelled = assertThrows(
                ItineraryException.class,
                () -> itineraries.applyRevision(42, itineraryId, cancelledRevision)
        );
        assertEquals(ItineraryError.INVALID_STATUS_TRANSITION, cancelled.error());
        assertEquals(2, itineraries.get(42, itineraryId).version());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM itinerary_command WHERE command_id IN (?, ?)",
                Integer.class,
                revision.commandId().toString(),
                cancelledRevision.commandId().toString()
        ));
    }

    @Test
    void lifecycleTransitionsFollowTheStateGraphAndReplayWithoutAnotherVersion() {
        long itineraryId = itineraries.create(42, createCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000134"), "状态流转"
        )).itineraryId();
        long dayId = itineraries.get(42, itineraryId).days().get(0).id();
        itineraries.addItem(42, itineraryId, envelope(
                "00000000-0000-0000-0000-000000000135", 1,
                new ItineraryCommands.AddItem(dayId, "有效安排", null, null, null, null, null)
        ));

        var plannedCommand = envelope(
                "00000000-0000-0000-0000-000000000136", 2,
                new ItineraryCommands.TransitionStatus(com.jiawa.lyw.itinerary.domain.ItineraryStatus.PLANNED)
        );
        var planned = itineraries.transition(42, itineraryId, plannedCommand);
        var replay = itineraries.transition(42, itineraryId, plannedCommand);
        assertEquals(3, planned.version());
        assertTrue(replay.replayed());

        itineraries.transition(42, itineraryId, envelope(
                "00000000-0000-0000-0000-000000000137", 3,
                new ItineraryCommands.TransitionStatus(
                        com.jiawa.lyw.itinerary.domain.ItineraryStatus.IN_PROGRESS
                )
        ));
        itineraries.transition(42, itineraryId, envelope(
                "00000000-0000-0000-0000-000000000138", 4,
                new ItineraryCommands.TransitionStatus(
                        com.jiawa.lyw.itinerary.domain.ItineraryStatus.COMPLETED
                )
        ));
        itineraries.transition(42, itineraryId, envelope(
                "00000000-0000-0000-0000-000000000139", 5,
                new ItineraryCommands.TransitionStatus(
                        com.jiawa.lyw.itinerary.domain.ItineraryStatus.ARCHIVED
                )
        ));
        assertEquals(com.jiawa.lyw.itinerary.domain.ItineraryStatus.ARCHIVED,
                itineraries.get(42, itineraryId).status());
        assertEquals(6, itineraries.get(42, itineraryId).version());
    }

    @Test
    void lifecycleRejectsPlanningWithoutItemsAndRollsBackTheCommand() {
        long itineraryId = itineraries.create(42, createCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000140"), "无安排草稿"
        )).itineraryId();
        var command = envelope(
                "00000000-0000-0000-0000-000000000141", 1,
                new ItineraryCommands.TransitionStatus(
                        com.jiawa.lyw.itinerary.domain.ItineraryStatus.PLANNED
                )
        );
        var invalid = assertThrows(
                ItineraryException.class,
                () -> itineraries.transition(42, itineraryId, command)
        );
        assertEquals(ItineraryError.INVALID_STATUS_TRANSITION, invalid.error());
        assertEquals(1, itineraries.get(42, itineraryId).version());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM itinerary_command WHERE command_id = ?",
                Integer.class,
                command.commandId().toString()
        ));
    }

    private static ItineraryCommands.CommandEnvelope<ItineraryCommands.CreateItinerary> createCommand(
            UUID commandId,
            String title
    ) {
        return new ItineraryCommands.CommandEnvelope<>(
                commandId,
                0,
                new ItineraryCommands.CreateItinerary(
                        TEST_PREFIX + title,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 3),
                        "Asia/Shanghai",
                        "CNY",
                        List.of(
                                new ItineraryCommands.DestinationInput(null, "上海", "CN", "Asia/Shanghai"),
                                new ItineraryCommands.DestinationInput(null, "苏州", "CN", "Asia/Shanghai")
                        )
                )
        );
    }

    private static <T> ItineraryCommands.CommandEnvelope<T> envelope(
            String commandId,
            long expectedVersion,
            T payload
    ) {
        return new ItineraryCommands.CommandEnvelope<>(
                UUID.fromString(commandId), expectedVersion, payload
        );
    }

    private void cleanupRows() {
        jdbc.update("DELETE FROM itinerary_command");
        jdbc.update("DELETE FROM itinerary_item");
        jdbc.update("DELETE FROM itinerary_day");
        jdbc.update("DELETE FROM itinerary_destination");
        jdbc.update("DELETE FROM itinerary");
        jdbc.update("DELETE FROM identity_one_time_token");
        jdbc.update("DELETE FROM identity_refresh_session");
        jdbc.update("DELETE FROM member_login_log");
        jdbc.update("DELETE FROM member WHERE name LIKE 'IT-TEST-#17-%'");
    }

    @Configuration
    @EnableTransactionManagement
    @Import(ItineraryConfiguration.class)
    static class Config {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password());
        }

        @Bean
        SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResources("classpath:mapper/itinerary/*.xml"));
            org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            return factory;
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        @Primary
        TestIds testIds() {
            return new TestIds();
        }
    }

    static final class TestIds implements ItineraryIdGenerator {
        private final AtomicLong sequence = new AtomicLong(1000);

        void reset() {
            sequence.set(1000);
        }

        @Override
        public long nextId() {
            return sequence.incrementAndGet();
        }
    }
}
