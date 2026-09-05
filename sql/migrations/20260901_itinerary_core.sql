-- 手工旅行行程核心迁移。
-- 默认 dry-run；执行方显式设置 @apply_itinerary_core_migration = 1 才创建缺失表。
-- CREATE TABLE IF NOT EXISTS 使脚本可在前次执行中断于任意表之后安全重试。

DELIMITER $$

DROP PROCEDURE IF EXISTS apply_itinerary_core_migration$$
CREATE PROCEDURE apply_itinerary_core_migration()
BEGIN
    IF COALESCE(@apply_itinerary_core_migration, 0) = 1 THEN
        CREATE TABLE IF NOT EXISTS itinerary (
            id BIGINT NOT NULL,
            owner_member_id BIGINT NOT NULL,
            title VARCHAR(100) NOT NULL,
            start_date DATE NOT NULL,
            end_date DATE NOT NULL,
            time_zone VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            base_currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
            version BIGINT NOT NULL DEFAULT 1,
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
            PRIMARY KEY (id),
            INDEX idx_itinerary_owner_updated (owner_member_id, updated_at, id),
            INDEX idx_itinerary_owner_status_updated (owner_member_id, status, updated_at, id),
            CONSTRAINT fk_itinerary_owner FOREIGN KEY (owner_member_id)
                REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
            CONSTRAINT chk_itinerary_dates CHECK (
                end_date >= start_date AND DATEDIFF(end_date, start_date) <= 365
            ),
            CONSTRAINT chk_itinerary_currency CHECK (base_currency REGEXP '^[A-Z]{3}$'),
            CONSTRAINT chk_itinerary_status CHECK (
                status IN ('DRAFT', 'PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'ARCHIVED')
            ),
            CONSTRAINT chk_itinerary_version CHECK (version >= 1)
        ) ENGINE = InnoDB
          DEFAULT CHARACTER SET = utf8mb4
          COLLATE = utf8mb4_unicode_ci
          COMMENT = '旅行行程正式事实表';

        CREATE TABLE IF NOT EXISTS itinerary_destination (
            id BIGINT NOT NULL,
            itinerary_id BIGINT NOT NULL,
            name VARCHAR(100) NOT NULL,
            country_code CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NULL,
            time_zone VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            position BIGINT NOT NULL,
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
            PRIMARY KEY (id),
            UNIQUE INDEX uk_itinerary_destination_position (itinerary_id, position),
            CONSTRAINT fk_itinerary_destination_itinerary FOREIGN KEY (itinerary_id)
                REFERENCES itinerary (id) ON DELETE CASCADE ON UPDATE RESTRICT,
            CONSTRAINT chk_itinerary_destination_country CHECK (
                country_code IS NULL OR country_code REGEXP '^[A-Z]{2}$'
            ),
            CONSTRAINT chk_itinerary_destination_position CHECK (position >= 1024)
        ) ENGINE = InnoDB
          DEFAULT CHARACTER SET = utf8mb4
          COLLATE = utf8mb4_unicode_ci
          COMMENT = '行程有序目的地';

        CREATE TABLE IF NOT EXISTS itinerary_day (
            id BIGINT NOT NULL,
            itinerary_id BIGINT NOT NULL,
            day_date DATE NOT NULL,
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
            PRIMARY KEY (id),
            UNIQUE INDEX uk_itinerary_day_date (itinerary_id, day_date),
            CONSTRAINT fk_itinerary_day_itinerary FOREIGN KEY (itinerary_id)
                REFERENCES itinerary (id) ON DELETE CASCADE ON UPDATE RESTRICT
        ) ENGINE = InnoDB
          DEFAULT CHARACTER SET = utf8mb4
          COLLATE = utf8mb4_unicode_ci
          COMMENT = '行程自然日';

        CREATE TABLE IF NOT EXISTS itinerary_item (
            id BIGINT NOT NULL,
            itinerary_id BIGINT NOT NULL,
            itinerary_day_id BIGINT NOT NULL,
            title VARCHAR(120) NOT NULL,
            place_name VARCHAR(200) NULL,
            start_time TIME NULL,
            end_time TIME NULL,
            notes VARCHAR(2000) NULL,
            estimated_cost DECIMAL(14, 2) NULL,
            position BIGINT NOT NULL,
            deleted_at DATETIME(3) NULL,
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
            PRIMARY KEY (id),
            INDEX idx_itinerary_item_itinerary (itinerary_id, deleted_at, id),
            INDEX idx_itinerary_item_day_position (itinerary_day_id, deleted_at, position, id),
            CONSTRAINT fk_itinerary_item_itinerary FOREIGN KEY (itinerary_id)
                REFERENCES itinerary (id) ON DELETE CASCADE ON UPDATE RESTRICT,
            CONSTRAINT fk_itinerary_item_day FOREIGN KEY (itinerary_day_id)
                REFERENCES itinerary_day (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
            CONSTRAINT chk_itinerary_item_time CHECK (
                (start_time IS NULL AND end_time IS NULL)
                OR (start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time)
            ),
            CONSTRAINT chk_itinerary_item_cost CHECK (
                estimated_cost IS NULL OR estimated_cost >= 0
            ),
            CONSTRAINT chk_itinerary_item_position CHECK (position >= 1024)
        ) ENGINE = InnoDB
          DEFAULT CHARACTER SET = utf8mb4
          COLLATE = utf8mb4_unicode_ci
          COMMENT = '行程安排项';

        CREATE TABLE IF NOT EXISTS itinerary_command (
            id BIGINT NOT NULL,
            command_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            member_id BIGINT NOT NULL,
            operation VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            itinerary_id BIGINT NULL,
            expected_version BIGINT NOT NULL,
            request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            result_itinerary_id BIGINT NULL,
            result_item_id BIGINT NULL,
            result_version BIGINT NULL,
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (id),
            UNIQUE INDEX uk_itinerary_command_id (command_id),
            INDEX idx_itinerary_command_member_created (member_id, created_at, id),
            INDEX idx_itinerary_command_itinerary_created (result_itinerary_id, created_at, id),
            CONSTRAINT fk_itinerary_command_member FOREIGN KEY (member_id)
                REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
            CONSTRAINT fk_itinerary_command_itinerary FOREIGN KEY (itinerary_id)
                REFERENCES itinerary (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
            CONSTRAINT fk_itinerary_command_result_itinerary FOREIGN KEY (result_itinerary_id)
                REFERENCES itinerary (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
            CONSTRAINT fk_itinerary_command_result_item FOREIGN KEY (result_item_id)
                REFERENCES itinerary_item (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
            CONSTRAINT chk_itinerary_command_expected_version CHECK (expected_version >= 0),
            CONSTRAINT chk_itinerary_command_result_version CHECK (
                result_version IS NULL OR result_version >= 1
            ),
            CONSTRAINT chk_itinerary_command_hash CHECK (request_hash REGEXP '^[0-9a-f]{64}$')
        ) ENGINE = InnoDB
          DEFAULT CHARACTER SET = utf8mb4
          COLLATE = utf8mb4_unicode_ci
          COMMENT = '行程命令幂等结果';
    END IF;
END$$

CALL apply_itinerary_core_migration()$$
DROP PROCEDURE apply_itinerary_core_migration$$

DELIMITER ;

DROP TEMPORARY TABLE IF EXISTS itinerary_expected_tables;
CREATE TEMPORARY TABLE itinerary_expected_tables AS
SELECT 'itinerary' AS table_name
UNION ALL SELECT 'itinerary_destination'
UNION ALL SELECT 'itinerary_day'
UNION ALL SELECT 'itinerary_item'
UNION ALL SELECT 'itinerary_command';

SELECT 'missing_table_count' AS metric, COUNT(*) AS value
FROM itinerary_expected_tables expected
LEFT JOIN information_schema.tables actual
    ON actual.table_schema = DATABASE() AND actual.table_name = expected.table_name
WHERE actual.table_name IS NULL;

SELECT 'extra_table_count' AS metric, COUNT(*) AS value
FROM information_schema.tables actual
LEFT JOIN itinerary_expected_tables expected ON expected.table_name = actual.table_name
WHERE actual.table_schema = DATABASE()
  AND actual.table_name LIKE 'itinerary%'
  AND expected.table_name IS NULL;

DROP TEMPORARY TABLE IF EXISTS itinerary_expected_columns;
CREATE TEMPORARY TABLE itinerary_expected_columns AS
SELECT 'itinerary' AS table_name, 'id' AS column_name
UNION ALL SELECT 'itinerary', 'owner_member_id'
UNION ALL SELECT 'itinerary', 'title'
UNION ALL SELECT 'itinerary', 'start_date'
UNION ALL SELECT 'itinerary', 'end_date'
UNION ALL SELECT 'itinerary', 'time_zone'
UNION ALL SELECT 'itinerary', 'base_currency'
UNION ALL SELECT 'itinerary', 'status'
UNION ALL SELECT 'itinerary', 'version'
UNION ALL SELECT 'itinerary', 'created_at'
UNION ALL SELECT 'itinerary', 'updated_at'
UNION ALL SELECT 'itinerary_destination', 'id'
UNION ALL SELECT 'itinerary_destination', 'itinerary_id'
UNION ALL SELECT 'itinerary_destination', 'name'
UNION ALL SELECT 'itinerary_destination', 'country_code'
UNION ALL SELECT 'itinerary_destination', 'time_zone'
UNION ALL SELECT 'itinerary_destination', 'position'
UNION ALL SELECT 'itinerary_destination', 'created_at'
UNION ALL SELECT 'itinerary_destination', 'updated_at'
UNION ALL SELECT 'itinerary_day', 'id'
UNION ALL SELECT 'itinerary_day', 'itinerary_id'
UNION ALL SELECT 'itinerary_day', 'day_date'
UNION ALL SELECT 'itinerary_day', 'created_at'
UNION ALL SELECT 'itinerary_day', 'updated_at'
UNION ALL SELECT 'itinerary_item', 'id'
UNION ALL SELECT 'itinerary_item', 'itinerary_id'
UNION ALL SELECT 'itinerary_item', 'itinerary_day_id'
UNION ALL SELECT 'itinerary_item', 'title'
UNION ALL SELECT 'itinerary_item', 'place_name'
UNION ALL SELECT 'itinerary_item', 'start_time'
UNION ALL SELECT 'itinerary_item', 'end_time'
UNION ALL SELECT 'itinerary_item', 'notes'
UNION ALL SELECT 'itinerary_item', 'estimated_cost'
UNION ALL SELECT 'itinerary_item', 'position'
UNION ALL SELECT 'itinerary_item', 'deleted_at'
UNION ALL SELECT 'itinerary_item', 'created_at'
UNION ALL SELECT 'itinerary_item', 'updated_at'
UNION ALL SELECT 'itinerary_command', 'id'
UNION ALL SELECT 'itinerary_command', 'command_id'
UNION ALL SELECT 'itinerary_command', 'member_id'
UNION ALL SELECT 'itinerary_command', 'operation'
UNION ALL SELECT 'itinerary_command', 'itinerary_id'
UNION ALL SELECT 'itinerary_command', 'expected_version'
UNION ALL SELECT 'itinerary_command', 'request_hash'
UNION ALL SELECT 'itinerary_command', 'result_itinerary_id'
UNION ALL SELECT 'itinerary_command', 'result_item_id'
UNION ALL SELECT 'itinerary_command', 'result_version'
UNION ALL SELECT 'itinerary_command', 'created_at';

SELECT 'missing_column_count' AS metric, COUNT(*) AS value
FROM itinerary_expected_columns expected
LEFT JOIN information_schema.columns actual
    ON actual.table_schema = DATABASE()
   AND actual.table_name = expected.table_name
   AND actual.column_name = expected.column_name
WHERE actual.column_name IS NULL;

DROP TEMPORARY TABLE IF EXISTS itinerary_expected_indexes;
CREATE TEMPORARY TABLE itinerary_expected_indexes AS
SELECT 'itinerary' AS table_name, 'idx_itinerary_owner_updated' AS index_name
UNION ALL SELECT 'itinerary', 'idx_itinerary_owner_status_updated'
UNION ALL SELECT 'itinerary_destination', 'uk_itinerary_destination_position'
UNION ALL SELECT 'itinerary_day', 'uk_itinerary_day_date'
UNION ALL SELECT 'itinerary_item', 'idx_itinerary_item_itinerary'
UNION ALL SELECT 'itinerary_item', 'idx_itinerary_item_day_position'
UNION ALL SELECT 'itinerary_command', 'uk_itinerary_command_id'
UNION ALL SELECT 'itinerary_command', 'idx_itinerary_command_member_created'
UNION ALL SELECT 'itinerary_command', 'idx_itinerary_command_itinerary_created';

SELECT 'missing_index_count' AS metric, COUNT(*) AS value
FROM itinerary_expected_indexes expected
LEFT JOIN information_schema.statistics actual
    ON actual.table_schema = DATABASE()
   AND actual.table_name = expected.table_name
   AND actual.index_name = expected.index_name
WHERE actual.index_name IS NULL;

DROP TEMPORARY TABLE IF EXISTS itinerary_expected_foreign_keys;
CREATE TEMPORARY TABLE itinerary_expected_foreign_keys AS
SELECT 'itinerary' AS table_name, 'fk_itinerary_owner' AS constraint_name
UNION ALL SELECT 'itinerary_destination', 'fk_itinerary_destination_itinerary'
UNION ALL SELECT 'itinerary_day', 'fk_itinerary_day_itinerary'
UNION ALL SELECT 'itinerary_item', 'fk_itinerary_item_itinerary'
UNION ALL SELECT 'itinerary_item', 'fk_itinerary_item_day'
UNION ALL SELECT 'itinerary_command', 'fk_itinerary_command_member'
UNION ALL SELECT 'itinerary_command', 'fk_itinerary_command_itinerary'
UNION ALL SELECT 'itinerary_command', 'fk_itinerary_command_result_itinerary'
UNION ALL SELECT 'itinerary_command', 'fk_itinerary_command_result_item';

SELECT 'missing_foreign_key_count' AS metric, COUNT(*) AS value
FROM itinerary_expected_foreign_keys expected
LEFT JOIN information_schema.table_constraints actual
    ON actual.constraint_schema = DATABASE()
   AND actual.table_name = expected.table_name
   AND actual.constraint_name = expected.constraint_name
   AND actual.constraint_type = 'FOREIGN KEY'
WHERE actual.constraint_name IS NULL;

SELECT 'legacy_column_count' AS metric, COUNT(*) AS value
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('itinerary', 'itinerary_destination', 'itinerary_day', 'itinerary_item', 'itinerary_command')
  AND column_name IN ('timezone', 'sort_order', 'day_id', 'destination_id', 'item_type', 'currency', 'result_json');

DROP TEMPORARY TABLE itinerary_expected_foreign_keys;
DROP TEMPORARY TABLE itinerary_expected_indexes;
DROP TEMPORARY TABLE itinerary_expected_columns;
DROP TEMPORARY TABLE itinerary_expected_tables;
