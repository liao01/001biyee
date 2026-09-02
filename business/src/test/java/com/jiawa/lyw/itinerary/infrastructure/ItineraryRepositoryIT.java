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
import java.time.ZoneOffset;
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
