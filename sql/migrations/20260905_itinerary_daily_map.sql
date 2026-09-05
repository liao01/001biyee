-- 正式行程地点引用迁移。
-- 默认 dry-run；执行方显式设置 @apply_itinerary_daily_map_migration = 1 才收敛缺失结构。
-- 每个 ALTER 先检查 information_schema，因此已创建但不完整的表也能安全恢复。

DELIMITER $$

DROP PROCEDURE IF EXISTS apply_itinerary_daily_map_migration$$
CREATE PROCEDURE apply_itinerary_daily_map_migration()
BEGIN
    DECLARE existing_count INT DEFAULT 0;

    IF COALESCE(@apply_itinerary_daily_map_migration, 0) = 1 THEN
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
        ) ENGINE = InnoDB
          DEFAULT CHARACTER SET = utf8mb4
          COLLATE = utf8mb4_unicode_ci
          COMMENT = '行程安排项的正式地点引用';

        SELECT COUNT(*) INTO existing_count
        FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'itinerary_id';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD COLUMN itinerary_id BIGINT NOT NULL AFTER item_id;
        END IF;
        SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'source';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD COLUMN source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;
        END IF;
        SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'source_place_id';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD COLUMN source_place_id VARCHAR(128) NULL;
        END IF;
        SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'name';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD COLUMN name VARCHAR(200) NOT NULL;
        END IF;
        SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'address';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD COLUMN address VARCHAR(500) NULL;
        END IF;
        SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'administrative_area_code';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD COLUMN administrative_area_code CHAR(6) CHARACTER SET ascii COLLATE ascii_bin NULL;
        END IF;
        SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'longitude';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD COLUMN longitude DECIMAL(10, 7) NOT NULL;
        END IF;
        SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'latitude';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD COLUMN latitude DECIMAL(10, 7) NOT NULL;
        END IF;
        SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'coordinate_system';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD COLUMN coordinate_system VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;
        END IF;
        SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'confirmed_at';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD COLUMN confirmed_at DATETIME(3) NOT NULL;
        END IF;
        SELECT COUNT(*) INTO existing_count FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND column_name = 'updated_at';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD COLUMN updated_at DATETIME(3) NOT NULL;
        END IF;

        SELECT COUNT(*) INTO existing_count FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' AND index_name = 'idx_itinerary_item_place_itinerary';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD INDEX idx_itinerary_item_place_itinerary (itinerary_id, item_id);
        END IF;

        SELECT COUNT(*) INTO existing_count FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'itinerary_item_place' AND constraint_name = 'fk_itinerary_item_place_item' AND constraint_type = 'FOREIGN KEY';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD CONSTRAINT fk_itinerary_item_place_item FOREIGN KEY (item_id) REFERENCES itinerary_item (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
        END IF;
        SELECT COUNT(*) INTO existing_count FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'itinerary_item_place' AND constraint_name = 'fk_itinerary_item_place_itinerary' AND constraint_type = 'FOREIGN KEY';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD CONSTRAINT fk_itinerary_item_place_itinerary FOREIGN KEY (itinerary_id) REFERENCES itinerary (id) ON DELETE CASCADE ON UPDATE RESTRICT;
        END IF;

        SELECT COUNT(*) INTO existing_count FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'itinerary_item_place' AND constraint_name = 'chk_itinerary_item_place_source' AND constraint_type = 'CHECK';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD CONSTRAINT chk_itinerary_item_place_source CHECK (source IN ('AMAP', 'MANUAL'));
        END IF;
        SELECT COUNT(*) INTO existing_count FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'itinerary_item_place' AND constraint_name = 'chk_itinerary_item_place_source_id' AND constraint_type = 'CHECK';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD CONSTRAINT chk_itinerary_item_place_source_id CHECK ((source = 'AMAP' AND source_place_id IS NOT NULL) OR (source = 'MANUAL' AND source_place_id IS NULL));
        END IF;
        SELECT COUNT(*) INTO existing_count FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'itinerary_item_place' AND constraint_name = 'chk_itinerary_item_place_area_code' AND constraint_type = 'CHECK';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD CONSTRAINT chk_itinerary_item_place_area_code CHECK (administrative_area_code IS NULL OR administrative_area_code REGEXP '^[0-9]{6}$');
        END IF;
        SELECT COUNT(*) INTO existing_count FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'itinerary_item_place' AND constraint_name = 'chk_itinerary_item_place_longitude' AND constraint_type = 'CHECK';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD CONSTRAINT chk_itinerary_item_place_longitude CHECK (longitude BETWEEN -180 AND 180);
        END IF;
        SELECT COUNT(*) INTO existing_count FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'itinerary_item_place' AND constraint_name = 'chk_itinerary_item_place_latitude' AND constraint_type = 'CHECK';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD CONSTRAINT chk_itinerary_item_place_latitude CHECK (latitude BETWEEN -90 AND 90);
        END IF;
        SELECT COUNT(*) INTO existing_count FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'itinerary_item_place' AND constraint_name = 'chk_itinerary_item_place_coordinate_system' AND constraint_type = 'CHECK';
        IF existing_count = 0 THEN
            ALTER TABLE itinerary_item_place ADD CONSTRAINT chk_itinerary_item_place_coordinate_system CHECK (coordinate_system = 'GCJ02');
        END IF;
    END IF;
END$$

CALL apply_itinerary_daily_map_migration()$$
DROP PROCEDURE apply_itinerary_daily_map_migration$$

DELIMITER ;

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

DROP TEMPORARY TABLE IF EXISTS itinerary_daily_map_expected_columns;
CREATE TEMPORARY TABLE itinerary_daily_map_expected_columns AS
SELECT 'itinerary_item_place' AS table_name, 'item_id' AS column_name
UNION ALL SELECT 'itinerary_item_place', 'itinerary_id'
UNION ALL SELECT 'itinerary_item_place', 'source'
UNION ALL SELECT 'itinerary_item_place', 'source_place_id'
UNION ALL SELECT 'itinerary_item_place', 'name'
UNION ALL SELECT 'itinerary_item_place', 'address'
UNION ALL SELECT 'itinerary_item_place', 'administrative_area_code'
UNION ALL SELECT 'itinerary_item_place', 'longitude'
UNION ALL SELECT 'itinerary_item_place', 'latitude'
UNION ALL SELECT 'itinerary_item_place', 'coordinate_system'
UNION ALL SELECT 'itinerary_item_place', 'confirmed_at'
UNION ALL SELECT 'itinerary_item_place', 'updated_at';

SELECT 'missing_column_count' AS metric, COUNT(*) AS value
FROM itinerary_daily_map_expected_columns expected
LEFT JOIN information_schema.columns actual
    ON actual.table_schema = DATABASE()
   AND actual.table_name = expected.table_name
   AND actual.column_name = expected.column_name
WHERE actual.column_name IS NULL;

DROP TEMPORARY TABLE IF EXISTS itinerary_daily_map_expected_indexes;
CREATE TEMPORARY TABLE itinerary_daily_map_expected_indexes AS
SELECT 'itinerary_item_place' AS table_name, 'idx_itinerary_item_place_itinerary' AS index_name;

SELECT 'missing_index_count' AS metric, COUNT(*) AS value
FROM itinerary_daily_map_expected_indexes expected
LEFT JOIN information_schema.statistics actual
    ON actual.table_schema = DATABASE()
   AND actual.table_name = expected.table_name
   AND actual.index_name = expected.index_name
WHERE actual.index_name IS NULL;

DROP TEMPORARY TABLE IF EXISTS itinerary_daily_map_expected_foreign_keys;
CREATE TEMPORARY TABLE itinerary_daily_map_expected_foreign_keys AS
SELECT 'itinerary_item_place' AS table_name, 'fk_itinerary_item_place_item' AS constraint_name
UNION ALL SELECT 'itinerary_item_place', 'fk_itinerary_item_place_itinerary';

SELECT 'missing_foreign_key_count' AS metric, COUNT(*) AS value
FROM itinerary_daily_map_expected_foreign_keys expected
LEFT JOIN information_schema.table_constraints actual
    ON actual.constraint_schema = DATABASE()
   AND actual.table_name = expected.table_name
   AND actual.constraint_name = expected.constraint_name
   AND actual.constraint_type = 'FOREIGN KEY'
WHERE actual.constraint_name IS NULL;

SELECT 'legacy_column_count' AS metric, COUNT(*) AS value
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'itinerary_item_place'
  AND column_name IN ('provider_json', 'amap_json', 'lng', 'lat', 'route_json');

DROP TEMPORARY TABLE itinerary_daily_map_expected_foreign_keys;
DROP TEMPORARY TABLE itinerary_daily_map_expected_indexes;
DROP TEMPORARY TABLE itinerary_daily_map_expected_columns;
DROP TEMPORARY TABLE itinerary_daily_map_expected_tables;
