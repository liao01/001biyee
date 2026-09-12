-- 邮箱身份迁移：默认只报告，不修改正式表结构或数据。
-- 执行前备份 member；停止旧注册/修改密码入口后，显式设置
-- SET @apply_member_email_identity_migration = 1; 才执行收敛。
SET @identity_apply = COALESCE(@apply_member_email_identity_migration, 0) = 1;
SET @identity_email_pattern = '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$';

SET @identity_has_email = EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'member' AND column_name = 'email'
);
SET @identity_email_projection = 'CASE
    WHEN CONVERT(TRIM(mobile) USING utf8mb4) COLLATE utf8mb4_unicode_ci REGEXP @identity_email_pattern
    THEN LOWER(CONVERT(TRIM(mobile) USING utf8mb4)) COLLATE utf8mb4_unicode_ci
    ELSE NULL END';

-- 临时表只存候选邮箱，不存凭据；正常结束时删除，连接退出也会自动清理。
DROP TEMPORARY TABLE IF EXISTS identity_migration_candidates;
SET @identity_sql = CONCAT(
    'CREATE TEMPORARY TABLE identity_migration_candidates AS SELECT id, ',
    @identity_email_projection, ' AS candidate_email FROM member',
    IF(@identity_has_email, ' WHERE email IS NULL', '')
);
PREPARE identity_statement FROM @identity_sql;
EXECUTE identity_statement;
DEALLOCATE PREPARE identity_statement;

DROP TEMPORARY TABLE IF EXISTS identity_migration_email_counts;
SET @identity_sql = CONCAT(
    'CREATE TEMPORARY TABLE identity_migration_email_counts AS ',
    'SELECT candidate_email, COUNT(*) AS owners FROM (SELECT ',
    IF(@identity_has_email, CONCAT('COALESCE(email, ', @identity_email_projection, ')'), @identity_email_projection),
    ' AS candidate_email FROM member) identities ',
    'WHERE candidate_email IS NOT NULL GROUP BY candidate_email'
);
PREPARE identity_statement FROM @identity_sql;
EXECUTE identity_statement;
DEALLOCATE PREPARE identity_statement;

SELECT 'eligible_email_rows' AS metric, COUNT(*) AS value
FROM identity_migration_candidates c
JOIN identity_migration_email_counts n ON n.candidate_email = c.candidate_email
WHERE n.owners = 1;
SELECT 'manual_email_binding_rows' AS metric, COUNT(*) AS value
FROM identity_migration_candidates c
LEFT JOIN identity_migration_email_counts n ON n.candidate_email = c.candidate_email
WHERE c.candidate_email IS NULL OR n.owners > 1;
SELECT 'conflicting_email_rows' AS metric, COUNT(*) AS value
FROM identity_migration_candidates c
JOIN identity_migration_email_counts n ON n.candidate_email = c.candidate_email
WHERE n.owners > 1;

-- 所有 DDL 均位于显式 apply 分支，重复执行只补齐缺失结构。
SET @identity_sql = IF(
    @identity_apply AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'member' AND column_name = 'email'
    ),
    'ALTER TABLE member ADD COLUMN email VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''规范化邮箱''',
    'DO 0'
);
PREPARE identity_statement FROM @identity_sql;
EXECUTE identity_statement;
DEALLOCATE PREPARE identity_statement;

SET @identity_sql = IF(
    @identity_apply AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'member' AND column_name = 'email_verified_at'
    ),
    'ALTER TABLE member ADD COLUMN email_verified_at DATETIME(3) NULL COMMENT ''邮箱验证时间''',
    'DO 0'
);
PREPARE identity_statement FROM @identity_sql;
EXECUTE identity_statement;
DEALLOCATE PREPARE identity_statement;

SET @identity_sql = IF(
    @identity_apply AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'member' AND column_name = 'password_hash'
    ),
    'ALTER TABLE member ADD COLUMN password_hash VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''身份凭据摘要''',
    'DO 0'
);
PREPARE identity_statement FROM @identity_sql;
EXECUTE identity_statement;
DEALLOCATE PREPARE identity_statement;

SET @identity_sql = IF(
    @identity_apply AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'member' AND column_name = 'password_algorithm'
    ),
    'ALTER TABLE member ADD COLUMN password_algorithm VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''凭据验证算法''',
    'DO 0'
);
PREPARE identity_statement FROM @identity_sql;
EXECUTE identity_statement;
DEALLOCATE PREPARE identity_statement;

SET @identity_sql = IF(
    @identity_apply AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'member' AND column_name = 'account_status'
    ),
    'ALTER TABLE member ADD COLUMN account_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''账户状态''',
    'DO 0'
);
PREPARE identity_statement FROM @identity_sql;
EXECUTE identity_statement;
DEALLOCATE PREPARE identity_statement;

SET @identity_sql = IF(
    @identity_apply AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'member'
          AND column_name IN ('mobile', 'password') AND is_nullable = 'NO'
    ),
    'ALTER TABLE member MODIFY mobile VARCHAR(50) NULL COMMENT ''历史登录标识，仅兼容迁移使用'', MODIFY password CHAR(32) NULL COMMENT ''历史凭据，仅兼容迁移使用''',
    'DO 0'
);
PREPARE identity_statement FROM @identity_sql;
EXECUTE identity_statement;
DEALLOCATE PREPARE identity_statement;

SET @identity_sql = IF(
    @identity_apply AND NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'member' AND index_name = 'uk_member_email'
    ),
    'ALTER TABLE member ADD UNIQUE INDEX uk_member_email (email)',
    'DO 0'
);
PREPARE identity_statement FROM @identity_sql;
EXECUTE identity_statement;
DEALLOCATE PREPARE identity_statement;

SET @identity_sql = IF(
    @identity_apply,
    'CREATE TABLE IF NOT EXISTS identity_one_time_token (
    id BIGINT NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    purpose VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    member_id BIGINT NOT NULL,
    email VARCHAR(254) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    used_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_identity_one_time_token_hash (token_hash),
    KEY idx_identity_one_time_member (member_id, purpose),
    CONSTRAINT fk_identity_one_time_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci',
    'DO 0'
);
PREPARE identity_statement FROM @identity_sql;
EXECUTE identity_statement;
DEALLOCATE PREPARE identity_statement;

SET @identity_sql = IF(
    @identity_apply,
    'CREATE TABLE IF NOT EXISTS identity_refresh_session (
    id BIGINT NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    member_id BIGINT NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_identity_refresh_token_hash (token_hash),
    KEY idx_identity_refresh_member (member_id),
    CONSTRAINT fk_identity_refresh_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci',
    'DO 0'
);
PREPARE identity_statement FROM @identity_sql;
EXECUTE identity_statement;
DEALLOCATE PREPARE identity_statement;

-- 不覆盖已升级的密码、不重新激活停用账户；历史字段暂留给显式兼容层。
SET @identity_sql = IF(
    @identity_apply,
    'UPDATE member m
JOIN identity_migration_candidates c ON c.id = m.id
JOIN identity_migration_email_counts n ON n.candidate_email = c.candidate_email AND n.owners = 1
SET m.email = c.candidate_email,
    m.email_verified_at = COALESCE(m.email_verified_at, m.created_at, CURRENT_TIMESTAMP(3)),
    m.password_hash = COALESCE(m.password_hash, m.password),
    m.password_algorithm = COALESCE(m.password_algorithm, ''LEGACY_DOUBLE_MD5''),
    m.account_status = CASE
        WHEN m.account_status IS NULL OR m.account_status IN (''EMAIL_BINDING_REQUIRED'', ''PENDING_VERIFICATION'') THEN ''ACTIVE''
        ELSE m.account_status END
WHERE m.email IS NULL',
    'DO 0'
);
PREPARE identity_statement FROM @identity_sql;
EXECUTE identity_statement;
DEALLOCATE PREPARE identity_statement;

SET @identity_sql = IF(
    @identity_apply,
    'UPDATE member
SET password_hash = COALESCE(password_hash, password),
    password_algorithm = COALESCE(password_algorithm, ''LEGACY_DOUBLE_MD5''),
    account_status = CASE
        WHEN account_status IS NULL OR account_status = ''PENDING_VERIFICATION'' THEN ''EMAIL_BINDING_REQUIRED''
        ELSE account_status END
WHERE email IS NULL',
    'DO 0'
);
PREPARE identity_statement FROM @identity_sql;
EXECUTE identity_statement;
DEALLOCATE PREPARE identity_statement;

SET @identity_sql = IF(
    @identity_apply AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'member' AND column_name = 'account_status'
          AND (is_nullable = 'YES' OR column_default IS NULL OR column_default <> 'PENDING_VERIFICATION')
    ),
    'ALTER TABLE member MODIFY account_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT ''PENDING_VERIFICATION'' COMMENT ''账户状态''',
    'DO 0'
);
PREPARE identity_statement FROM @identity_sql;
EXECUTE identity_statement;
DEALLOCATE PREPARE identity_statement;

SET @identity_sql = IF(
    @identity_apply,
    'SELECT ''identified_account_rows'' AS metric, COUNT(*) AS value FROM member WHERE email IS NOT NULL
UNION ALL SELECT ''email_binding_required_rows'', COUNT(*) FROM member WHERE account_status = ''EMAIL_BINDING_REQUIRED''
UNION ALL SELECT ''legacy_password_rows'', COUNT(*) FROM member WHERE password_algorithm = ''LEGACY_DOUBLE_MD5''
UNION ALL SELECT ''missing_identity_tables'', 2 - COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name IN (''identity_one_time_token'', ''identity_refresh_session'')',
    'DO 0'
);
PREPARE identity_statement FROM @identity_sql;
EXECUTE identity_statement;
DEALLOCATE PREPARE identity_statement;

DROP TEMPORARY TABLE identity_migration_candidates;
DROP TEMPORARY TABLE identity_migration_email_counts;
