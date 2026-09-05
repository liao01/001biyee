-- 正式行程地点引用迁移。
-- 默认 dry-run；显式设置 @apply_itinerary_daily_map_migration = 1 才收敛缺失结构。
-- 业务表 DDL 前验证已有定义；非空表存在漂移时拒绝执行，避免隐式提交留下部分修改。

SET @itinerary_daily_map_migration_error = NULL;

DROP TEMPORARY TABLE IF EXISTS itinerary_daily_map_expected_columns;
CREATE TEMPORARY TABLE itinerary_daily_map_expected_columns (
    ordinal_position INT NOT NULL,
    column_name VARCHAR(64) NOT NULL PRIMARY KEY,
    column_type VARCHAR(64) NOT NULL,
    is_nullable VARCHAR(3) NOT NULL,
    character_set_name VARCHAR(64) NULL,
    collation_name VARCHAR(64) NULL
);
INSERT INTO itinerary_daily_map_expected_columns VALUES
    (1, 'item_id', 'bigint', 'NO', NULL, NULL),
    (2, 'itinerary_id', 'bigint', 'NO', NULL, NULL),
    (3, 'source', 'varchar(16)', 'NO', 'ascii', 'ascii_bin'),
    (4, 'source_place_id', 'varchar(128)', 'YES', 'utf8mb4', 'utf8mb4_unicode_ci'),
    (5, 'name', 'varchar(200)', 'NO', 'utf8mb4', 'utf8mb4_unicode_ci'),
    (6, 'address', 'varchar(500)', 'YES', 'utf8mb4', 'utf8mb4_unicode_ci'),
    (7, 'administrative_area_code', 'char(6)', 'YES', 'ascii', 'ascii_bin'),
    (8, 'longitude', 'decimal(10,7)', 'NO', NULL, NULL),
    (9, 'latitude', 'decimal(10,7)', 'NO', NULL, NULL),
    (10, 'coordinate_system', 'varchar(16)', 'NO', 'ascii', 'ascii_bin'),
    (11, 'confirmed_at', 'datetime(3)', 'NO', NULL, NULL),
    (12, 'updated_at', 'datetime(3)', 'NO', NULL, NULL);

DROP TEMPORARY TABLE IF EXISTS itinerary_daily_map_expected_indexes;
CREATE TEMPORARY TABLE itinerary_daily_map_expected_indexes (
    index_name VARCHAR(64) NOT NULL PRIMARY KEY,
    non_unique INT NOT NULL,
    column_names VARCHAR(255) NOT NULL
);
INSERT INTO itinerary_daily_map_expected_indexes VALUES
    ('PRIMARY', 0, 'item_id'),
    ('idx_itinerary_item_place_itinerary', 1, 'itinerary_id,item_id');

DROP TEMPORARY TABLE IF EXISTS itinerary_daily_map_expected_foreign_keys;
CREATE TEMPORARY TABLE itinerary_daily_map_expected_foreign_keys (
    constraint_name VARCHAR(64) NOT NULL PRIMARY KEY,
    column_names VARCHAR(255) NOT NULL,
    referenced_table_name VARCHAR(64) NOT NULL,
    referenced_column_names VARCHAR(255) NOT NULL,
    update_rule VARCHAR(16) NOT NULL,
    delete_rule VARCHAR(16) NOT NULL
);
INSERT INTO itinerary_daily_map_expected_foreign_keys VALUES
    ('fk_itinerary_item_place_item', 'item_id', 'itinerary_item', 'id', 'RESTRICT', 'RESTRICT'),
    ('fk_itinerary_item_place_itinerary', 'itinerary_id', 'itinerary', 'id', 'RESTRICT', 'CASCADE');

DROP TEMPORARY TABLE IF EXISTS itinerary_daily_map_expected_checks;
CREATE TEMPORARY TABLE itinerary_daily_map_expected_checks (
    constraint_name VARCHAR(64) NOT NULL PRIMARY KEY,
    normalized_clause VARCHAR(1000) NOT NULL,
    expected_summary VARCHAR(64) NOT NULL
);
INSERT INTO itinerary_daily_map_expected_checks VALUES
    ('chk_itinerary_item_place_source', '(sourcein(''amap'',''manual''))', 'source IN AMAP,MANUAL'),
    ('chk_itinerary_item_place_source_id', '(((source=''amap'')and(source_place_idisnotnull))or((source=''manual'')and(source_place_idisnull)))', 'source/id pairing'),
    ('chk_itinerary_item_place_area_code', '((administrative_area_codeisnull)orregexp_like(administrative_area_code,''^[0-9]{6}$''))', 'area code NULL or six digits'),
    ('chk_itinerary_item_place_longitude', '(longitudebetween-(180)and180)', 'longitude BETWEEN -180 AND 180'),
    ('chk_itinerary_item_place_latitude', '(latitudebetween-(90)and90)', 'latitude BETWEEN -90 AND 90'),
    ('chk_itinerary_item_place_coordinate_system', '(coordinate_system=''gcj02'')', 'coordinate_system = GCJ02');

DELIMITER $$

DROP PROCEDURE IF EXISTS apply_itinerary_daily_map_migration$$
CREATE PROCEDURE apply_itinerary_daily_map_migration()
migration: BEGIN
    DECLARE existing_count INT DEFAULT 0;
    DECLARE row_count BIGINT DEFAULT 0;
    DECLARE failure_message VARCHAR(128) DEFAULT NULL;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 @itinerary_daily_map_migration_error = MESSAGE_TEXT;
    END;

    IF COALESCE(@apply_itinerary_daily_map_migration, 0) <> 1 THEN
        LEAVE migration;
    END IF;

    SELECT COUNT(*) INTO existing_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place';

    IF existing_count = 1 THEN
        SELECT COUNT(*) INTO row_count FROM itinerary_item_place;

        SET failure_message = NULL;
        SELECT CONCAT('column ', expected.column_name, ' expected ', expected.column_type,
                      '/', expected.is_nullable, '/', COALESCE(expected.character_set_name, '-'),
                      '/', COALESCE(expected.collation_name, '-'))
        INTO failure_message
        FROM itinerary_daily_map_expected_columns expected
        JOIN information_schema.columns actual
          ON actual.table_schema = DATABASE()
         AND actual.table_name = 'itinerary_item_place'
         AND actual.column_name = expected.column_name
        WHERE LOWER(actual.column_type) <> expected.column_type
           OR actual.is_nullable <> expected.is_nullable
           OR NOT (actual.character_set_name <=> expected.character_set_name)
           OR NOT (actual.collation_name <=> expected.collation_name)
        ORDER BY expected.ordinal_position
        LIMIT 1;
        IF failure_message IS NOT NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = failure_message;
        END IF;

        IF row_count > 0 THEN
            SET failure_message = NULL;
            SELECT CONCAT('non-empty itinerary_item_place missing ', expected.column_name)
            INTO failure_message
            FROM itinerary_daily_map_expected_columns expected
            LEFT JOIN information_schema.columns actual
              ON actual.table_schema = DATABASE()
             AND actual.table_name = 'itinerary_item_place'
             AND actual.column_name = expected.column_name
            WHERE actual.column_name IS NULL
            ORDER BY expected.ordinal_position
            LIMIT 1;
            IF failure_message IS NOT NULL THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = failure_message;
            END IF;
        END IF;

        SET failure_message = NULL;
        SELECT CONCAT(expected.index_name, ' ', expected.column_names)
        INTO failure_message
        FROM itinerary_daily_map_expected_indexes expected
        JOIN (
            SELECT index_name, MIN(non_unique) AS non_unique,
                   GROUP_CONCAT(column_name ORDER BY seq_in_index) AS column_names,
                   SUM(sub_part IS NOT NULL) AS prefix_column_count
            FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place'
            GROUP BY index_name
        ) actual ON actual.index_name = expected.index_name
        WHERE actual.non_unique <> expected.non_unique
           OR actual.column_names <> expected.column_names
           OR actual.prefix_column_count <> 0
        ORDER BY expected.index_name
        LIMIT 1;
        IF failure_message IS NOT NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = failure_message;
        END IF;

        SET failure_message = NULL;
        SELECT CONCAT(expected.constraint_name, ' ', expected.update_rule, '/', expected.delete_rule,
                      ' ', expected.referenced_table_name)
        INTO failure_message
        FROM itinerary_daily_map_expected_foreign_keys expected
        JOIN information_schema.table_constraints named
          ON named.constraint_schema = DATABASE()
         AND named.table_name = 'itinerary_item_place'
         AND named.constraint_name = expected.constraint_name
        LEFT JOIN (
            SELECT rc.constraint_name,
                   GROUP_CONCAT(kcu.column_name ORDER BY kcu.ordinal_position) AS column_names,
                   MIN(kcu.referenced_table_name) AS referenced_table_name,
                   GROUP_CONCAT(kcu.referenced_column_name ORDER BY kcu.ordinal_position) AS referenced_column_names,
                   MIN(rc.update_rule) AS update_rule,
                   MIN(rc.delete_rule) AS delete_rule
            FROM information_schema.referential_constraints rc
            JOIN information_schema.key_column_usage kcu
              ON kcu.constraint_schema = rc.constraint_schema
             AND kcu.table_name = rc.table_name
             AND kcu.constraint_name = rc.constraint_name
            WHERE rc.constraint_schema = DATABASE() AND rc.table_name = 'itinerary_item_place'
            GROUP BY rc.constraint_name
        ) actual ON actual.constraint_name = expected.constraint_name
        WHERE named.constraint_type <> 'FOREIGN KEY'
           OR actual.constraint_name IS NULL
           OR actual.column_names <> expected.column_names
           OR actual.referenced_table_name <> expected.referenced_table_name
           OR actual.referenced_column_names <> expected.referenced_column_names
           OR actual.update_rule <> expected.update_rule
           OR actual.delete_rule <> expected.delete_rule
        ORDER BY expected.constraint_name
        LIMIT 1;
        IF failure_message IS NOT NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = failure_message;
        END IF;

        SET failure_message = NULL;
        SELECT CONCAT(expected.constraint_name, ' ', expected.expected_summary)
        INTO failure_message
        FROM itinerary_daily_map_expected_checks expected
        JOIN information_schema.table_constraints named
          ON named.constraint_schema = DATABASE()
         AND named.table_name = 'itinerary_item_place'
         AND named.constraint_name = expected.constraint_name
        LEFT JOIN information_schema.check_constraints actual
          ON actual.constraint_schema = named.constraint_schema
         AND actual.constraint_name = named.constraint_name
        WHERE named.constraint_type <> 'CHECK'
           OR actual.constraint_name IS NULL
           OR LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                  actual.check_clause, '`', ''), ' ', ''), '_utf8mb4', ''), '_ascii', ''),
                  CHAR(92), '')) <> expected.normalized_clause
        ORDER BY expected.constraint_name
        LIMIT 1;
        IF failure_message IS NOT NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = failure_message;
        END IF;

        IF row_count > 0 THEN
            SET failure_message = NULL;
            SELECT CONCAT('non-empty itinerary_item_place missing index ', expected.index_name)
            INTO failure_message
            FROM itinerary_daily_map_expected_indexes expected
            LEFT JOIN information_schema.statistics actual
              ON actual.table_schema = DATABASE()
             AND actual.table_name = 'itinerary_item_place'
             AND actual.index_name = expected.index_name
            WHERE actual.index_name IS NULL
            ORDER BY expected.index_name
            LIMIT 1;
            IF failure_message IS NOT NULL THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = failure_message;
            END IF;

            SET failure_message = NULL;
            SELECT CONCAT('non-empty itinerary_item_place missing foreign key ', expected.constraint_name)
            INTO failure_message
            FROM itinerary_daily_map_expected_foreign_keys expected
            LEFT JOIN information_schema.referential_constraints actual
              ON actual.constraint_schema = DATABASE()
             AND actual.table_name = 'itinerary_item_place'
             AND actual.constraint_name = expected.constraint_name
            WHERE actual.constraint_name IS NULL
            ORDER BY expected.constraint_name
            LIMIT 1;
            IF failure_message IS NOT NULL THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = failure_message;
            END IF;

            SET failure_message = NULL;
            SELECT CONCAT('non-empty itinerary_item_place missing check ', expected.constraint_name)
            INTO failure_message
            FROM itinerary_daily_map_expected_checks expected
            LEFT JOIN information_schema.check_constraints actual
              ON actual.constraint_schema = DATABASE()
             AND actual.constraint_name = expected.constraint_name
            WHERE actual.constraint_name IS NULL
            ORDER BY expected.constraint_name
            LIMIT 1;
            IF failure_message IS NOT NULL THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = failure_message;
            END IF;
        END IF;
    END IF;

    CREATE TABLE IF NOT EXISTS itinerary_item_place (
        item_id BIGINT NOT NULL,
        itinerary_id BIGINT NOT NULL,
        source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
        source_place_id VARCHAR(128) NULL,
        name VARCHAR(200) NOT NULL,
        address VARCHAR(500) NULL,
        administrative_area_code CHAR(6) CHARACTER SET ascii COLLATE ascii_bin NULL,
        longitude DECIMAL(10, 7) NOT NULL,
        latitude DECIMAL(10, 7) NOT NULL,
        coordinate_system VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
        confirmed_at DATETIME(3) NOT NULL,
        updated_at DATETIME(3) NOT NULL,
        PRIMARY KEY (item_id),
        INDEX idx_itinerary_item_place_itinerary (itinerary_id, item_id),
        CONSTRAINT fk_itinerary_item_place_item FOREIGN KEY (item_id)
            REFERENCES itinerary_item (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
        CONSTRAINT fk_itinerary_item_place_itinerary FOREIGN KEY (itinerary_id)
            REFERENCES itinerary (id) ON DELETE CASCADE ON UPDATE RESTRICT,
        CONSTRAINT chk_itinerary_item_place_source CHECK (source IN ('AMAP', 'MANUAL')),
        CONSTRAINT chk_itinerary_item_place_source_id CHECK (
            (source = 'AMAP' AND source_place_id IS NOT NULL)
            OR (source = 'MANUAL' AND source_place_id IS NULL)
        ),
        CONSTRAINT chk_itinerary_item_place_area_code CHECK (
            administrative_area_code IS NULL OR administrative_area_code REGEXP '^[0-9]{6}$'
        ),
        CONSTRAINT chk_itinerary_item_place_longitude CHECK (longitude BETWEEN -180 AND 180),
        CONSTRAINT chk_itinerary_item_place_latitude CHECK (latitude BETWEEN -90 AND 90),
        CONSTRAINT chk_itinerary_item_place_coordinate_system CHECK (coordinate_system = 'GCJ02')
    ) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci
      COMMENT = '行程安排项的正式地点引用';

    SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'item_id';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD COLUMN item_id BIGINT NOT NULL FIRST; END IF;
    SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'itinerary_id';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD COLUMN itinerary_id BIGINT NOT NULL AFTER item_id; END IF;
    SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'source';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD COLUMN source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL AFTER itinerary_id; END IF;
    SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'source_place_id';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD COLUMN source_place_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL AFTER source; END IF;
    SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'name';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD COLUMN name VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL AFTER source_place_id; END IF;
    SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'address';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD COLUMN address VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL AFTER name; END IF;
    SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'administrative_area_code';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD COLUMN administrative_area_code CHAR(6) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER address; END IF;
    SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'longitude';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD COLUMN longitude DECIMAL(10, 7) NOT NULL AFTER administrative_area_code; END IF;
    SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'latitude';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD COLUMN latitude DECIMAL(10, 7) NOT NULL AFTER longitude; END IF;
    SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'coordinate_system';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD COLUMN coordinate_system VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL AFTER latitude; END IF;
    SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'confirmed_at';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD COLUMN confirmed_at DATETIME(3) NOT NULL AFTER coordinate_system; END IF;
    SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'updated_at';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD COLUMN updated_at DATETIME(3) NOT NULL AFTER confirmed_at; END IF;

    SELECT COUNT(*) INTO existing_count FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND index_name = 'PRIMARY';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD PRIMARY KEY (item_id); END IF;
    SELECT COUNT(*) INTO existing_count FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND index_name = 'idx_itinerary_item_place_itinerary';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD INDEX idx_itinerary_item_place_itinerary (itinerary_id, item_id); END IF;

    SELECT COUNT(*) INTO existing_count FROM information_schema.referential_constraints WHERE constraint_schema = DATABASE() AND table_name = 'itinerary_item_place' AND constraint_name = 'fk_itinerary_item_place_item';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD CONSTRAINT fk_itinerary_item_place_item FOREIGN KEY (item_id) REFERENCES itinerary_item (id) ON DELETE RESTRICT ON UPDATE RESTRICT; END IF;
    SELECT COUNT(*) INTO existing_count FROM information_schema.referential_constraints WHERE constraint_schema = DATABASE() AND table_name = 'itinerary_item_place' AND constraint_name = 'fk_itinerary_item_place_itinerary';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD CONSTRAINT fk_itinerary_item_place_itinerary FOREIGN KEY (itinerary_id) REFERENCES itinerary (id) ON DELETE CASCADE ON UPDATE RESTRICT; END IF;

    SELECT COUNT(*) INTO existing_count FROM information_schema.check_constraints WHERE constraint_schema = DATABASE() AND constraint_name = 'chk_itinerary_item_place_source';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD CONSTRAINT chk_itinerary_item_place_source CHECK (source IN ('AMAP', 'MANUAL')); END IF;
    SELECT COUNT(*) INTO existing_count FROM information_schema.check_constraints WHERE constraint_schema = DATABASE() AND constraint_name = 'chk_itinerary_item_place_source_id';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD CONSTRAINT chk_itinerary_item_place_source_id CHECK ((source = 'AMAP' AND source_place_id IS NOT NULL) OR (source = 'MANUAL' AND source_place_id IS NULL)); END IF;
    SELECT COUNT(*) INTO existing_count FROM information_schema.check_constraints WHERE constraint_schema = DATABASE() AND constraint_name = 'chk_itinerary_item_place_area_code';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD CONSTRAINT chk_itinerary_item_place_area_code CHECK (administrative_area_code IS NULL OR administrative_area_code REGEXP '^[0-9]{6}$'); END IF;
    SELECT COUNT(*) INTO existing_count FROM information_schema.check_constraints WHERE constraint_schema = DATABASE() AND constraint_name = 'chk_itinerary_item_place_longitude';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD CONSTRAINT chk_itinerary_item_place_longitude CHECK (longitude BETWEEN -180 AND 180); END IF;
    SELECT COUNT(*) INTO existing_count FROM information_schema.check_constraints WHERE constraint_schema = DATABASE() AND constraint_name = 'chk_itinerary_item_place_latitude';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD CONSTRAINT chk_itinerary_item_place_latitude CHECK (latitude BETWEEN -90 AND 90); END IF;
    SELECT COUNT(*) INTO existing_count FROM information_schema.check_constraints WHERE constraint_schema = DATABASE() AND constraint_name = 'chk_itinerary_item_place_coordinate_system';
    IF existing_count = 0 THEN ALTER TABLE itinerary_item_place ADD CONSTRAINT chk_itinerary_item_place_coordinate_system CHECK (coordinate_system = 'GCJ02'); END IF;
END$$

CALL apply_itinerary_daily_map_migration()$$
DROP PROCEDURE apply_itinerary_daily_map_migration$$

DELIMITER ;

-- SIGNAL 的精确差异先被捕获以清理例程，再用同一差异名中止客户端批次。
SET @itinerary_daily_map_abort_sql = IF(
    @itinerary_daily_map_migration_error IS NULL,
    'DO 0',
    CONCAT('CALL `', LEFT(REPLACE(@itinerary_daily_map_migration_error, '`', ''), 64), '`()')
);
PREPARE itinerary_daily_map_abort FROM @itinerary_daily_map_abort_sql;
EXECUTE itinerary_daily_map_abort;
DEALLOCATE PREPARE itinerary_daily_map_abort;

DROP TEMPORARY TABLE IF EXISTS itinerary_daily_map_expected_tables;
CREATE TEMPORARY TABLE itinerary_daily_map_expected_tables AS
SELECT 'itinerary_item_place' AS table_name;

SELECT 'missing_table_count' AS metric, COUNT(*) AS value
FROM itinerary_daily_map_expected_tables expected
LEFT JOIN information_schema.tables actual
    ON actual.table_schema = DATABASE() AND actual.table_name = expected.table_name
WHERE actual.table_name IS NULL;

SELECT 'extra_table_count' AS metric, COUNT(*) AS value
FROM information_schema.tables actual
LEFT JOIN itinerary_daily_map_expected_tables expected ON expected.table_name = actual.table_name
WHERE actual.table_schema = DATABASE()
  AND actual.table_name LIKE 'itinerary_item_place%'
  AND expected.table_name IS NULL;

SELECT 'missing_column_count' AS metric, COUNT(*) AS value
FROM itinerary_daily_map_expected_columns expected
LEFT JOIN information_schema.columns actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = 'itinerary_item_place'
 AND actual.column_name = expected.column_name
WHERE actual.column_name IS NULL
   OR LOWER(actual.column_type) <> expected.column_type
   OR actual.is_nullable <> expected.is_nullable
   OR NOT (actual.character_set_name <=> expected.character_set_name)
   OR NOT (actual.collation_name <=> expected.collation_name);

SELECT 'missing_index_count' AS metric, COUNT(*) AS value
FROM itinerary_daily_map_expected_indexes expected
LEFT JOIN (
    SELECT index_name, MIN(non_unique) AS non_unique,
           GROUP_CONCAT(column_name ORDER BY seq_in_index) AS column_names,
           SUM(sub_part IS NOT NULL) AS prefix_column_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place'
    GROUP BY index_name
) actual ON actual.index_name = expected.index_name
WHERE actual.index_name IS NULL
   OR actual.non_unique <> expected.non_unique
   OR actual.column_names <> expected.column_names
   OR actual.prefix_column_count <> 0;

SELECT 'missing_foreign_key_count' AS metric, COUNT(*) AS value
FROM itinerary_daily_map_expected_foreign_keys expected
LEFT JOIN (
    SELECT rc.constraint_name,
           GROUP_CONCAT(kcu.column_name ORDER BY kcu.ordinal_position) AS column_names,
           MIN(kcu.referenced_table_name) AS referenced_table_name,
           GROUP_CONCAT(kcu.referenced_column_name ORDER BY kcu.ordinal_position) AS referenced_column_names,
           MIN(rc.update_rule) AS update_rule,
           MIN(rc.delete_rule) AS delete_rule
    FROM information_schema.referential_constraints rc
    JOIN information_schema.key_column_usage kcu
      ON kcu.constraint_schema = rc.constraint_schema
     AND kcu.table_name = rc.table_name
     AND kcu.constraint_name = rc.constraint_name
    WHERE rc.constraint_schema = DATABASE() AND rc.table_name = 'itinerary_item_place'
    GROUP BY rc.constraint_name
) actual ON actual.constraint_name = expected.constraint_name
WHERE actual.constraint_name IS NULL
   OR actual.column_names <> expected.column_names
   OR actual.referenced_table_name <> expected.referenced_table_name
   OR actual.referenced_column_names <> expected.referenced_column_names
   OR actual.update_rule <> expected.update_rule
   OR actual.delete_rule <> expected.delete_rule;

SELECT 'missing_check_count' AS metric, COUNT(*) AS value
FROM itinerary_daily_map_expected_checks expected
LEFT JOIN information_schema.check_constraints actual
  ON actual.constraint_schema = DATABASE()
 AND actual.constraint_name = expected.constraint_name
WHERE actual.constraint_name IS NULL
   OR LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
          actual.check_clause, '`', ''), ' ', ''), '_utf8mb4', ''), '_ascii', ''),
          CHAR(92), '')) <> expected.normalized_clause;

SELECT 'legacy_column_count' AS metric, COUNT(*) AS value
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'itinerary_item_place'
  AND column_name IN ('provider_json', 'amap_json', 'lng', 'lat', 'route_json');

DROP TEMPORARY TABLE itinerary_daily_map_expected_tables;
DROP TEMPORARY TABLE itinerary_daily_map_expected_checks;
DROP TEMPORARY TABLE itinerary_daily_map_expected_foreign_keys;
DROP TEMPORARY TABLE itinerary_daily_map_expected_indexes;
DROP TEMPORARY TABLE itinerary_daily_map_expected_columns;
