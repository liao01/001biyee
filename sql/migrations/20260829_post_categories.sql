-- 帖子正式分类迁移。
-- 默认仅执行结构收敛与 dry-run 报告；调用方设置
-- SET @apply_post_category_migration = 1; 后才会回填历史帖子。

CREATE TABLE IF NOT EXISTS post_category (
    code VARCHAR(32) NOT NULL,
    name VARCHAR(50) NOT NULL,
    sort_order INT NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (code)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '帖子正式分类表';

INSERT INTO post_category (code, name, sort_order, enabled)
VALUES ('CITY_WALK', '城市漫游', 10, 1),
       ('NATURAL_SCENERY', '自然风光', 20, 1),
       ('FOOD', '美食', 30, 1)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    sort_order = VALUES(sort_order),
    enabled = VALUES(enabled),
    update_time = CURRENT_TIMESTAMP;

SET @category_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'post'
      AND column_name = 'category_code'
);
SET @category_column_sql = IF(
    @category_column_exists = 0,
    'ALTER TABLE post ADD COLUMN category_code VARCHAR(32) NULL COMMENT ''正式分类编码'' AFTER status',
    'SELECT 1'
);
PREPARE category_column_statement FROM @category_column_sql;
EXECUTE category_column_statement;
DEALLOCATE PREPARE category_column_statement;

SET @category_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'post'
      AND index_name = 'idx_post_category_code'
);
SET @category_index_sql = IF(
    @category_index_exists = 0,
    'ALTER TABLE post ADD INDEX idx_post_category_code (category_code)',
    'SELECT 1'
);
PREPARE category_index_statement FROM @category_index_sql;
EXECUTE category_index_statement;
DEALLOCATE PREPARE category_index_statement;

SET @category_fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'post'
      AND constraint_name = 'fk_post_category'
      AND constraint_type = 'FOREIGN KEY'
);
SET @category_fk_sql = IF(
    @category_fk_exists = 0,
    'ALTER TABLE post ADD CONSTRAINT fk_post_category FOREIGN KEY (category_code) REFERENCES post_category (code) ON DELETE RESTRICT ON UPDATE RESTRICT',
    'SELECT 1'
);
PREPARE category_fk_statement FROM @category_fk_sql;
EXECUTE category_fk_statement;
DEALLOCATE PREPARE category_fk_statement;

DROP TEMPORARY TABLE IF EXISTS post_category_migration_candidates;
CREATE TEMPORARY TABLE post_category_migration_candidates AS
SELECT
    p.id AS post_id,
    CASE
        WHEN MAX(t.name = '美食') = 1 AND MAX(t.name = '景点') = 1 THEN NULL
        WHEN MAX(t.name = '美食') = 1 THEN 'FOOD'
        WHEN MAX(t.name = '景点') = 1 THEN 'NATURAL_SCENERY'
        WHEN MAX(t.name IN ('旅行', '攻略')) = 1 THEN 'CITY_WALK'
        ELSE NULL
    END AS category_code,
    CASE
        WHEN MAX(t.name = '美食') = 1 AND MAX(t.name = '景点') = 1 THEN 'CONFLICT'
        WHEN MAX(t.name IN ('美食', '景点', '旅行', '攻略')) = 1 THEN 'MAPPED'
        ELSE 'UNMAPPED'
    END AS resolution
FROM post p
LEFT JOIN post_tag pt ON pt.post_id = p.id
LEFT JOIN tag t ON t.id = pt.tag_id
WHERE p.category_code IS NULL
GROUP BY p.id;

SELECT 'mapped_count' AS metric, COUNT(*) AS value
FROM post_category_migration_candidates
WHERE resolution = 'MAPPED';

SELECT 'conflict_count' AS metric, COUNT(*) AS value
FROM post_category_migration_candidates
WHERE resolution = 'CONFLICT';

SELECT 'unmapped_count' AS metric, COUNT(*) AS value
FROM post_category_migration_candidates
WHERE resolution = 'UNMAPPED';

SELECT resolution, post_id
FROM post_category_migration_candidates
WHERE resolution IN ('CONFLICT', 'UNMAPPED')
ORDER BY resolution, post_id;

UPDATE post p
JOIN post_category_migration_candidates candidate ON candidate.post_id = p.id
SET p.category_code = candidate.category_code
WHERE COALESCE(@apply_post_category_migration, 0) = 1
  AND candidate.resolution = 'MAPPED'
  AND p.category_code IS NULL;

SELECT 'category_CITY_WALK_count' AS metric, COUNT(*) AS value
FROM post
WHERE category_code = 'CITY_WALK'
UNION ALL
SELECT 'category_NATURAL_SCENERY_count', COUNT(*)
FROM post
WHERE category_code = 'NATURAL_SCENERY'
UNION ALL
SELECT 'category_FOOD_count', COUNT(*)
FROM post
WHERE category_code = 'FOOD'
UNION ALL
SELECT 'category_null_count', COUNT(*)
FROM post
WHERE category_code IS NULL
UNION ALL
SELECT 'category_invalid_count', COUNT(*)
FROM post p
LEFT JOIN post_category category ON category.code = p.category_code
WHERE p.category_code IS NOT NULL
  AND category.code IS NULL;

DROP TEMPORARY TABLE post_category_migration_candidates;
