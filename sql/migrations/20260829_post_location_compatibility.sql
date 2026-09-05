-- 修复 post 领域模型与历史数据库结构之间的 location_id 缺失。
-- 迁移可重复执行；已有帖子保持 location_id 为 NULL。

SET @location_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'post'
      AND column_name = 'location_id'
);
SET @location_column_sql = IF(
    @apply_post_location_compatibility_migration = 1 AND @location_column_exists = 0,
    'ALTER TABLE post ADD COLUMN location_id BIGINT NULL COMMENT ''关联地点ID'' AFTER title',
    'SELECT 1'
);
PREPARE location_column_statement FROM @location_column_sql;
EXECUTE location_column_statement;
DEALLOCATE PREPARE location_column_statement;

SET @location_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'post'
      AND index_name = 'idx_post_location_id'
);
SET @location_index_sql = IF(
    @apply_post_location_compatibility_migration = 1 AND @location_index_exists = 0,
    'ALTER TABLE post ADD INDEX idx_post_location_id (location_id)',
    'SELECT 1'
);
PREPARE location_index_statement FROM @location_index_sql;
EXECUTE location_index_statement;
DEALLOCATE PREPARE location_index_statement;

SET @location_fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'post'
      AND constraint_name = 'fk_post_location_record'
      AND constraint_type = 'FOREIGN KEY'
);
SET @location_fk_sql = IF(
    @apply_post_location_compatibility_migration = 1 AND @location_fk_exists = 0,
    'ALTER TABLE post ADD CONSTRAINT fk_post_location_record FOREIGN KEY (location_id) REFERENCES location_record (id) ON DELETE SET NULL ON UPDATE RESTRICT',
    'SELECT 1'
);
PREPARE location_fk_statement FROM @location_fk_sql;
EXECUTE location_fk_statement;
DEALLOCATE PREPARE location_fk_statement;

SELECT 'location_column_count' AS metric, COUNT(*) AS value
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'post'
  AND column_name = 'location_id';

SELECT 'location_index_count' AS metric, COUNT(*) AS value
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'post'
  AND index_name = 'idx_post_location_id';

SELECT 'location_fk_count' AS metric, COUNT(*) AS value
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND table_name = 'post'
  AND constraint_name = 'fk_post_location_record'
  AND constraint_type = 'FOREIGN KEY';
