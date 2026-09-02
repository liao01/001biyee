-- 结构化 AI 行程规划迁移。
-- 默认 dry-run；执行方显式设置 @apply_itinerary_planning_migration = 1 才创建缺失表。
-- 迁移只建立规划模块事实，不生成合成规划请求或 AI 建议。

DELIMITER $$

DROP PROCEDURE IF EXISTS apply_itinerary_planning_migration$$
CREATE PROCEDURE apply_itinerary_planning_migration()
BEGIN
    IF COALESCE(@apply_itinerary_planning_migration, 0) = 1 THEN
        CREATE TABLE IF NOT EXISTS itinerary_planning_request (
            id BIGINT NOT NULL,
            itinerary_id BIGINT NOT NULL,
            owner_member_id BIGINT NOT NULL,
            schema_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            start_date DATE NOT NULL,
            end_date DATE NOT NULL,
            budget_amount DECIMAL(14, 2) NOT NULL,
            budget_currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            party_size INT NOT NULL,
            preferences_json JSON NOT NULL,
            status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
            version BIGINT NOT NULL DEFAULT 1,
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
            PRIMARY KEY (id),
            INDEX idx_itinerary_planning_owner_updated (owner_member_id, updated_at, id),
            INDEX idx_itinerary_planning_itinerary_updated (itinerary_id, updated_at, id),
            CONSTRAINT fk_itinerary_planning_request_itinerary FOREIGN KEY (itinerary_id)
                REFERENCES itinerary (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
            CONSTRAINT fk_itinerary_planning_request_owner FOREIGN KEY (owner_member_id)
                REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
            CONSTRAINT chk_itinerary_planning_request_dates CHECK (
                end_date >= start_date AND DATEDIFF(end_date, start_date) <= 365
            ),
            CONSTRAINT chk_itinerary_planning_request_budget CHECK (budget_amount >= 0),
            CONSTRAINT chk_itinerary_planning_request_currency CHECK (
                budget_currency REGEXP '^[A-Z]{3}$'
            ),
            CONSTRAINT chk_itinerary_planning_request_party CHECK (party_size BETWEEN 1 AND 100),
            CONSTRAINT chk_itinerary_planning_request_status CHECK (
                status IN ('DRAFT', 'SUBMITTED', 'GENERATING', 'READY', 'FAILED', 'CANCELLED')
            ),
            CONSTRAINT chk_itinerary_planning_request_version CHECK (version >= 1)
        ) ENGINE = InnoDB
          DEFAULT CHARACTER SET = utf8mb4
          COLLATE = utf8mb4_unicode_ci
          COMMENT = '结构化行程规划请求';

        CREATE TABLE IF NOT EXISTS itinerary_planning_destination (
            id BIGINT NOT NULL,
            planning_request_id BIGINT NOT NULL,
            name VARCHAR(100) NOT NULL,
            country_code CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NULL,
            time_zone VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            position BIGINT NOT NULL,
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (id),
            UNIQUE INDEX uk_itinerary_planning_destination_position (planning_request_id, position),
            CONSTRAINT fk_itinerary_planning_destination_request FOREIGN KEY (planning_request_id)
                REFERENCES itinerary_planning_request (id) ON DELETE CASCADE ON UPDATE RESTRICT,
            CONSTRAINT chk_itinerary_planning_destination_country CHECK (
                country_code IS NULL OR country_code REGEXP '^[A-Z]{2}$'
            ),
            CONSTRAINT chk_itinerary_planning_destination_position CHECK (position >= 1024)
        ) ENGINE = InnoDB
          DEFAULT CHARACTER SET = utf8mb4
          COLLATE = utf8mb4_unicode_ci
          COMMENT = '规划请求有序目的地';

        CREATE TABLE IF NOT EXISTS itinerary_revision_proposal (
            id BIGINT NOT NULL,
            planning_request_id BIGINT NOT NULL,
            itinerary_id BIGINT NOT NULL,
            owner_member_id BIGINT NOT NULL,
            base_itinerary_version BIGINT NOT NULL,
            contract_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'VALIDATING',
            provider VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            provider_run_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
            model_name VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
            workflow_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
            knowledge_reference_ids_json JSON NULL,
            elapsed_millis BIGINT NULL,
            total_tokens BIGINT NULL,
            failure_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            resolved_at DATETIME(3) NULL,
            PRIMARY KEY (id),
            INDEX idx_itinerary_revision_request_created (planning_request_id, created_at, id),
            INDEX idx_itinerary_revision_itinerary_status (itinerary_id, status, created_at, id),
            INDEX idx_itinerary_revision_owner_created (owner_member_id, created_at, id),
            CONSTRAINT fk_itinerary_revision_proposal_request FOREIGN KEY (planning_request_id)
                REFERENCES itinerary_planning_request (id) ON DELETE CASCADE ON UPDATE RESTRICT,
            CONSTRAINT fk_itinerary_revision_proposal_itinerary FOREIGN KEY (itinerary_id)
                REFERENCES itinerary (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
            CONSTRAINT fk_itinerary_revision_proposal_owner FOREIGN KEY (owner_member_id)
                REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
            CONSTRAINT chk_itinerary_revision_base_version CHECK (base_itinerary_version >= 1),
            CONSTRAINT chk_itinerary_revision_proposal_status CHECK (
                status IN ('VALIDATING', 'READY', 'INVALID', 'FAILED', 'CONFIRMED', 'REJECTED', 'EXPIRED')
            ),
            CONSTRAINT chk_itinerary_revision_elapsed CHECK (
                elapsed_millis IS NULL OR elapsed_millis >= 0
            ),
            CONSTRAINT chk_itinerary_revision_tokens CHECK (total_tokens IS NULL OR total_tokens >= 0)
        ) ENGINE = InnoDB
          DEFAULT CHARACTER SET = utf8mb4
          COLLATE = utf8mb4_unicode_ci
          COMMENT = '不可变 AI 行程修订建议';

        CREATE TABLE IF NOT EXISTS itinerary_revision_operation (
            id BIGINT NOT NULL,
            proposal_id BIGINT NOT NULL,
            operation_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            operation_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            target_date DATE NULL,
            target_item_id BIGINT NULL,
            summary VARCHAR(500) NOT NULL,
            payload_json JSON NOT NULL,
            estimated_cost_delta DECIMAL(14, 2) NULL,
            validation_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'VALID',
            position BIGINT NOT NULL,
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (id),
            UNIQUE INDEX uk_itinerary_revision_operation_key (proposal_id, operation_key),
            UNIQUE INDEX uk_itinerary_revision_operation_position (proposal_id, position),
            INDEX idx_itinerary_revision_operation_target (target_item_id),
            CONSTRAINT fk_itinerary_revision_operation_proposal FOREIGN KEY (proposal_id)
                REFERENCES itinerary_revision_proposal (id) ON DELETE CASCADE ON UPDATE RESTRICT,
            CONSTRAINT fk_itinerary_revision_operation_item FOREIGN KEY (target_item_id)
                REFERENCES itinerary_item (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
            CONSTRAINT chk_itinerary_revision_operation_type CHECK (
                operation_type IN ('ADD_ITEM', 'UPDATE_ITEM', 'DELETE_ITEM', 'REORDER_DAY_ITEMS')
            ),
            CONSTRAINT chk_itinerary_revision_validation_status CHECK (
                validation_status IN ('VALID', 'INVALID')
            ),
            CONSTRAINT chk_itinerary_revision_operation_position CHECK (position >= 1024)
        ) ENGINE = InnoDB
          DEFAULT CHARACTER SET = utf8mb4
          COLLATE = utf8mb4_unicode_ci
          COMMENT = '可选择的行程修订建议操作';

        CREATE TABLE IF NOT EXISTS itinerary_revision_resolution (
            id BIGINT NOT NULL,
            proposal_id BIGINT NOT NULL,
            member_id BIGINT NOT NULL,
            decision_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            decision_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            selected_operations_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            expected_itinerary_version BIGINT NULL,
            itinerary_command_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
            result_version BIGINT NULL,
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (id),
            UNIQUE INDEX uk_itinerary_revision_resolution_proposal (proposal_id),
            UNIQUE INDEX uk_itinerary_revision_decision_id (decision_id),
            INDEX idx_itinerary_revision_resolution_member_created (member_id, created_at, id),
            CONSTRAINT fk_itinerary_revision_resolution_proposal FOREIGN KEY (proposal_id)
                REFERENCES itinerary_revision_proposal (id) ON DELETE CASCADE ON UPDATE RESTRICT,
            CONSTRAINT fk_itinerary_revision_resolution_member FOREIGN KEY (member_id)
                REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
            CONSTRAINT chk_itinerary_revision_decision_type CHECK (
                decision_type IN ('CONFIRM', 'REJECT')
            ),
            CONSTRAINT chk_itinerary_revision_selection_hash CHECK (
                selected_operations_hash REGEXP '^[0-9a-f]{64}$'
            ),
            CONSTRAINT chk_itinerary_revision_expected_version CHECK (
                expected_itinerary_version IS NULL OR expected_itinerary_version >= 1
            ),
            CONSTRAINT chk_itinerary_revision_result_version CHECK (
                result_version IS NULL OR result_version >= 1
            ),
            CONSTRAINT chk_itinerary_revision_resolution_shape CHECK (
                (decision_type = 'REJECT' AND expected_itinerary_version IS NULL
                    AND itinerary_command_id IS NULL AND result_version IS NULL)
                OR
                (decision_type = 'CONFIRM' AND expected_itinerary_version IS NOT NULL
                    AND itinerary_command_id IS NOT NULL AND result_version IS NOT NULL)
            )
        ) ENGINE = InnoDB
          DEFAULT CHARACTER SET = utf8mb4
          COLLATE = utf8mb4_unicode_ci
          COMMENT = '用户对修订建议的幂等决定';
    END IF;
END$$

CALL apply_itinerary_planning_migration()$$
DROP PROCEDURE apply_itinerary_planning_migration$$

DELIMITER ;

DROP TEMPORARY TABLE IF EXISTS itinerary_planning_expected_tables;
CREATE TEMPORARY TABLE itinerary_planning_expected_tables AS
SELECT 'itinerary_planning_request' AS table_name
UNION ALL SELECT 'itinerary_planning_destination'
UNION ALL SELECT 'itinerary_revision_proposal'
UNION ALL SELECT 'itinerary_revision_operation'
UNION ALL SELECT 'itinerary_revision_resolution';

SELECT 'missing_table_count' AS metric, COUNT(*) AS value
FROM itinerary_planning_expected_tables expected
LEFT JOIN information_schema.tables actual
    ON actual.table_schema = DATABASE() AND actual.table_name = expected.table_name
WHERE actual.table_name IS NULL;

SELECT 'extra_table_count' AS metric, COUNT(*) AS value
FROM information_schema.tables actual
LEFT JOIN itinerary_planning_expected_tables expected ON expected.table_name = actual.table_name
WHERE actual.table_schema = DATABASE()
  AND (actual.table_name LIKE 'itinerary_planning_%' OR actual.table_name LIKE 'itinerary_revision_%')
  AND expected.table_name IS NULL;

DROP TEMPORARY TABLE IF EXISTS itinerary_planning_expected_columns;
CREATE TEMPORARY TABLE itinerary_planning_expected_columns AS
SELECT 'itinerary_planning_request' AS table_name, 'id' AS column_name
UNION ALL SELECT 'itinerary_planning_request', 'itinerary_id'
UNION ALL SELECT 'itinerary_planning_request', 'owner_member_id'
UNION ALL SELECT 'itinerary_planning_request', 'schema_version'
UNION ALL SELECT 'itinerary_planning_request', 'start_date'
UNION ALL SELECT 'itinerary_planning_request', 'end_date'
UNION ALL SELECT 'itinerary_planning_request', 'budget_amount'
UNION ALL SELECT 'itinerary_planning_request', 'budget_currency'
UNION ALL SELECT 'itinerary_planning_request', 'party_size'
UNION ALL SELECT 'itinerary_planning_request', 'preferences_json'
UNION ALL SELECT 'itinerary_planning_request', 'status'
UNION ALL SELECT 'itinerary_planning_request', 'version'
UNION ALL SELECT 'itinerary_planning_request', 'created_at'
UNION ALL SELECT 'itinerary_planning_request', 'updated_at'
UNION ALL SELECT 'itinerary_planning_destination', 'id'
UNION ALL SELECT 'itinerary_planning_destination', 'planning_request_id'
UNION ALL SELECT 'itinerary_planning_destination', 'name'
UNION ALL SELECT 'itinerary_planning_destination', 'country_code'
UNION ALL SELECT 'itinerary_planning_destination', 'time_zone'
UNION ALL SELECT 'itinerary_planning_destination', 'position'
UNION ALL SELECT 'itinerary_planning_destination', 'created_at'
UNION ALL SELECT 'itinerary_revision_proposal', 'id'
UNION ALL SELECT 'itinerary_revision_proposal', 'planning_request_id'
UNION ALL SELECT 'itinerary_revision_proposal', 'itinerary_id'
UNION ALL SELECT 'itinerary_revision_proposal', 'owner_member_id'
UNION ALL SELECT 'itinerary_revision_proposal', 'base_itinerary_version'
UNION ALL SELECT 'itinerary_revision_proposal', 'contract_version'
UNION ALL SELECT 'itinerary_revision_proposal', 'status'
UNION ALL SELECT 'itinerary_revision_proposal', 'provider'
UNION ALL SELECT 'itinerary_revision_proposal', 'provider_run_id'
UNION ALL SELECT 'itinerary_revision_proposal', 'model_name'
UNION ALL SELECT 'itinerary_revision_proposal', 'workflow_version'
UNION ALL SELECT 'itinerary_revision_proposal', 'knowledge_reference_ids_json'
UNION ALL SELECT 'itinerary_revision_proposal', 'elapsed_millis'
UNION ALL SELECT 'itinerary_revision_proposal', 'total_tokens'
UNION ALL SELECT 'itinerary_revision_proposal', 'failure_code'
UNION ALL SELECT 'itinerary_revision_proposal', 'created_at'
UNION ALL SELECT 'itinerary_revision_proposal', 'resolved_at'
UNION ALL SELECT 'itinerary_revision_operation', 'id'
UNION ALL SELECT 'itinerary_revision_operation', 'proposal_id'
UNION ALL SELECT 'itinerary_revision_operation', 'operation_key'
UNION ALL SELECT 'itinerary_revision_operation', 'operation_type'
UNION ALL SELECT 'itinerary_revision_operation', 'target_date'
UNION ALL SELECT 'itinerary_revision_operation', 'target_item_id'
UNION ALL SELECT 'itinerary_revision_operation', 'summary'
UNION ALL SELECT 'itinerary_revision_operation', 'payload_json'
UNION ALL SELECT 'itinerary_revision_operation', 'estimated_cost_delta'
UNION ALL SELECT 'itinerary_revision_operation', 'validation_status'
UNION ALL SELECT 'itinerary_revision_operation', 'position'
UNION ALL SELECT 'itinerary_revision_operation', 'created_at'
UNION ALL SELECT 'itinerary_revision_resolution', 'id'
UNION ALL SELECT 'itinerary_revision_resolution', 'proposal_id'
UNION ALL SELECT 'itinerary_revision_resolution', 'member_id'
UNION ALL SELECT 'itinerary_revision_resolution', 'decision_id'
UNION ALL SELECT 'itinerary_revision_resolution', 'decision_type'
UNION ALL SELECT 'itinerary_revision_resolution', 'selected_operations_hash'
UNION ALL SELECT 'itinerary_revision_resolution', 'expected_itinerary_version'
UNION ALL SELECT 'itinerary_revision_resolution', 'itinerary_command_id'
UNION ALL SELECT 'itinerary_revision_resolution', 'result_version'
UNION ALL SELECT 'itinerary_revision_resolution', 'created_at';

SELECT 'missing_column_count' AS metric, COUNT(*) AS value
FROM itinerary_planning_expected_columns expected
LEFT JOIN information_schema.columns actual
    ON actual.table_schema = DATABASE()
   AND actual.table_name = expected.table_name
   AND actual.column_name = expected.column_name
WHERE actual.column_name IS NULL;

DROP TEMPORARY TABLE IF EXISTS itinerary_planning_expected_indexes;
CREATE TEMPORARY TABLE itinerary_planning_expected_indexes AS
SELECT 'itinerary_planning_request' AS table_name, 'idx_itinerary_planning_owner_updated' AS index_name
UNION ALL SELECT 'itinerary_planning_request', 'idx_itinerary_planning_itinerary_updated'
UNION ALL SELECT 'itinerary_planning_destination', 'uk_itinerary_planning_destination_position'
UNION ALL SELECT 'itinerary_revision_proposal', 'idx_itinerary_revision_request_created'
UNION ALL SELECT 'itinerary_revision_proposal', 'idx_itinerary_revision_itinerary_status'
UNION ALL SELECT 'itinerary_revision_proposal', 'idx_itinerary_revision_owner_created'
UNION ALL SELECT 'itinerary_revision_operation', 'uk_itinerary_revision_operation_key'
UNION ALL SELECT 'itinerary_revision_operation', 'uk_itinerary_revision_operation_position'
UNION ALL SELECT 'itinerary_revision_operation', 'idx_itinerary_revision_operation_target'
UNION ALL SELECT 'itinerary_revision_resolution', 'uk_itinerary_revision_resolution_proposal'
UNION ALL SELECT 'itinerary_revision_resolution', 'uk_itinerary_revision_decision_id'
UNION ALL SELECT 'itinerary_revision_resolution', 'idx_itinerary_revision_resolution_member_created';

SELECT 'missing_index_count' AS metric, COUNT(*) AS value
FROM itinerary_planning_expected_indexes expected
LEFT JOIN information_schema.statistics actual
    ON actual.table_schema = DATABASE()
   AND actual.table_name = expected.table_name
   AND actual.index_name = expected.index_name
WHERE actual.index_name IS NULL;

DROP TEMPORARY TABLE IF EXISTS itinerary_planning_expected_foreign_keys;
CREATE TEMPORARY TABLE itinerary_planning_expected_foreign_keys AS
SELECT 'itinerary_planning_request' AS table_name, 'fk_itinerary_planning_request_itinerary' AS constraint_name
UNION ALL SELECT 'itinerary_planning_request', 'fk_itinerary_planning_request_owner'
UNION ALL SELECT 'itinerary_planning_destination', 'fk_itinerary_planning_destination_request'
UNION ALL SELECT 'itinerary_revision_proposal', 'fk_itinerary_revision_proposal_request'
UNION ALL SELECT 'itinerary_revision_proposal', 'fk_itinerary_revision_proposal_itinerary'
UNION ALL SELECT 'itinerary_revision_proposal', 'fk_itinerary_revision_proposal_owner'
UNION ALL SELECT 'itinerary_revision_operation', 'fk_itinerary_revision_operation_proposal'
UNION ALL SELECT 'itinerary_revision_operation', 'fk_itinerary_revision_operation_item'
UNION ALL SELECT 'itinerary_revision_resolution', 'fk_itinerary_revision_resolution_proposal'
UNION ALL SELECT 'itinerary_revision_resolution', 'fk_itinerary_revision_resolution_member';

SELECT 'missing_foreign_key_count' AS metric, COUNT(*) AS value
FROM itinerary_planning_expected_foreign_keys expected
LEFT JOIN information_schema.table_constraints actual
    ON actual.constraint_schema = DATABASE()
   AND actual.table_name = expected.table_name
   AND actual.constraint_name = expected.constraint_name
   AND actual.constraint_type = 'FOREIGN KEY'
WHERE actual.constraint_name IS NULL;

SELECT 'legacy_column_count' AS metric, COUNT(*) AS value
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN (
      'itinerary_planning_request',
      'itinerary_planning_destination',
      'itinerary_revision_proposal',
      'itinerary_revision_operation',
      'itinerary_revision_resolution'
  )
  AND column_name IN (
      'prompt', 'raw_prompt', 'raw_response', 'conversation_id', 'dify_api_key',
      'itinerary_json', 'result_json', 'member_email', 'member_token'
  );

DROP TEMPORARY TABLE itinerary_planning_expected_foreign_keys;
DROP TEMPORARY TABLE itinerary_planning_expected_indexes;
DROP TEMPORARY TABLE itinerary_planning_expected_columns;
DROP TEMPORARY TABLE itinerary_planning_expected_tables;
