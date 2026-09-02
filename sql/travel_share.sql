SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `comment`;

CREATE TABLE `comment`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL COMMENT '外键 -> post.id',
  `user_id` bigint NOT NULL COMMENT '评论用户id -> member.id',
  `parent_id` bigint NULL DEFAULT NULL COMMENT '回复评论id',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '评论内容',
  `create_time` datetime NULL DEFAULT NULL COMMENT '评论时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `post_id`(`post_id` ASC) USING BTREE,
  INDEX `user_id`(`user_id` ASC) USING BTREE,
  INDEX `parent_id`(`parent_id` ASC) USING BTREE,
  CONSTRAINT `comment_ibfk_1` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `comment_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `member` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `comment_ibfk_3` FOREIGN KEY (`parent_id`) REFERENCES `comment` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2042580354476838913 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '评论表' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `location_image`;

CREATE TABLE `location_image`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `location_id` bigint NOT NULL COMMENT '外键 -> location.id',
  `image_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '图片URL',
  `seq` int NULL DEFAULT NULL COMMENT '图片顺序',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '图片描述',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `location_id`(`location_id` ASC) USING BTREE,
  CONSTRAINT `location_image_ibfk_1` FOREIGN KEY (`location_id`) REFERENCES `location_record` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2042231102240292865 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '地点图片表' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `location_record`;

CREATE TABLE `location_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `longitude` double(10, 6) NOT NULL COMMENT '经度',
  `latitude` double(10, 6) NOT NULL COMMENT '纬度',
  `formatted_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '完整地址',
  `city` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '城市',
  `province` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '省份',
  `district` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '区县',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '地点名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '景点描述',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_location`(`longitude` ASC, `latitude` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2042231102160601089 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '地图定位记录表' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `member`;

CREATE TABLE `member`  (
  `id` bigint NOT NULL COMMENT 'id',
  `mobile` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '历史登录标识，仅兼容迁移使用',
  `password` char(32) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '历史凭据，仅兼容迁移使用',
  `name` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '昵称',
  `created_at` datetime(3) NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime(3) NULL DEFAULT NULL COMMENT '修改时间',
  `email` varchar(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规范化邮箱',
  `email_verified_at` datetime(3) NULL DEFAULT NULL COMMENT '邮箱验证时间',
  `password_hash` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '身份凭据摘要',
  `password_algorithm` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '凭据验证算法',
  `account_status` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING_VERIFICATION' COMMENT '账户状态',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `mobile_unique`(`mobile` ASC) USING BTREE,
  UNIQUE INDEX `uk_member_email`(`email` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '会员表' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `identity_one_time_token`;

CREATE TABLE `identity_one_time_token` (
  `id` bigint NOT NULL,
  `token_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `purpose` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `member_id` bigint NOT NULL,
  `email` varchar(254) NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `used_at` datetime(3) NULL DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_identity_one_time_token_hash` (`token_hash`),
  KEY `idx_identity_one_time_member` (`member_id`, `purpose`),
  CONSTRAINT `fk_identity_one_time_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `identity_refresh_session`;

CREATE TABLE `identity_refresh_session` (
  `id` bigint NOT NULL,
  `token_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `member_id` bigint NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `revoked_at` datetime(3) NULL DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_identity_refresh_token_hash` (`token_hash`),
  KEY `idx_identity_refresh_member` (`member_id`),
  CONSTRAINT `fk_identity_refresh_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `member_login_log`;

CREATE TABLE `member_login_log`  (
  `id` bigint NOT NULL COMMENT 'id',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `login_time` datetime(3) NOT NULL COMMENT '登录时间',
  `token` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '登录token',
  `heart_count` int NULL DEFAULT NULL COMMENT '心跳次数',
  `last_heart_time` datetime(3) NULL DEFAULT NULL COMMENT '最后心跳时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '会员登录日志表' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `post_category`;

CREATE TABLE `post_category`  (
  `code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定分类编码',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `sort_order` int NOT NULL COMMENT '展示顺序',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否允许新发布内容选择',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`code`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '帖子正式分类表' ROW_FORMAT = Dynamic;

INSERT INTO `post_category` (`code`, `name`, `sort_order`, `enabled`) VALUES
('CITY_WALK', '城市漫游', 10, 1),
('NATURAL_SCENERY', '自然风光', 20, 1),
('FOOD', '美食', 30, 1);

DROP TABLE IF EXISTS `post`;

CREATE TABLE `post`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '作者id -> member.id',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标题',
  `location_id` bigint NULL DEFAULT NULL COMMENT '关联地点ID',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '文字内容',
  `create_time` datetime NULL DEFAULT NULL COMMENT '发布时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '0草稿 1公开 2删除',
  `category_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '正式分类编码',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_post_location_id`(`location_id` ASC) USING BTREE,
  INDEX `idx_post_category_code`(`category_code` ASC) USING BTREE,
  CONSTRAINT `post_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `member` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_post_location_record` FOREIGN KEY (`location_id`) REFERENCES `location_record` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_post_category` FOREIGN KEY (`category_code`) REFERENCES `post_category` (`code`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2042856504251994113 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '帖子表' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `post_image`;

CREATE TABLE `post_image`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL COMMENT '外键 -> post.id',
  `image_url` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '图片URL',
  `seq` int NULL DEFAULT NULL COMMENT '图片顺序',
  `description` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '图片描述',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `post_id`(`post_id` ASC) USING BTREE,
  CONSTRAINT `post_image_ibfk_1` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2042856504545595393 CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '帖子图片表' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `post_tag`;

CREATE TABLE `post_tag`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL COMMENT '外键 -> post.id',
  `tag_id` bigint NOT NULL COMMENT '外键 -> tag.id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `post_id`(`post_id` ASC) USING BTREE,
  INDEX `tag_id`(`tag_id` ASC) USING BTREE,
  CONSTRAINT `post_tag_ibfk_1` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `post_tag_ibfk_2` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2042856504591732737 CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '帖子标签关系表' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `post_view`;

CREATE TABLE `post_view`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户id -> member.id',
  `post_id` bigint NOT NULL COMMENT '帖子id -> post.id',
  `view_time` datetime NULL DEFAULT NULL COMMENT '浏览时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `user_id`(`user_id` ASC) USING BTREE,
  INDEX `post_id`(`post_id` ASC) USING BTREE,
  CONSTRAINT `post_view_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `member` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `post_view_ibfk_2` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2042857899202007041 CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '浏览记录表' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `sms_code`;

CREATE TABLE `sms_code`  (
  `id` bigint NOT NULL COMMENT 'id',
  `mobile` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '手机号',
  `code` char(6) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '验证码',
  `use` varchar(20) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '用途|枚举[SmsCodeUseEnum]：REGISTER(\"0\", \"注册\"), FORGET_PASSWORD(\"1\", \"忘记密码\")',
  `status` char(1) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '状态|枚举[SmsCodeStatusEnum]：USED(\"1\", \"已使用\"), NOT_USED(\"0\", \"未使用\")',
  `created_at` datetime(3) NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime(3) NULL DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '短信验证码表' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `tag`;

CREATE TABLE `tag`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '标签名',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1977660079407190017 CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '标签表' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `user`;

CREATE TABLE `user`  (
  `id` bigint NOT NULL COMMENT 'ID',
  `login_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '登录名',
  `password` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '密码',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `login_name_unique`(`login_name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `user_action`;

CREATE TABLE `user_action`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户id -> member.id',
  `post_id` bigint NOT NULL COMMENT '帖子id -> post.id',
  `action_type` varchar(20) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT 'like/favorite/share',
  `create_time` datetime NULL DEFAULT NULL COMMENT '操作时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `user_id`(`user_id` ASC) USING BTREE,
  INDEX `post_id`(`post_id` ASC) USING BTREE,
  CONSTRAINT `user_action_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `member` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `user_action_ibfk_2` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2042855565449318401 CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '用户行为表' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `user_follow`;

CREATE TABLE `user_follow`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '自己的id -> member.id',
  `follow_id` bigint NOT NULL COMMENT '被关注用户id -> member.id',
  `create_time` datetime NULL DEFAULT NULL COMMENT '关注时间',
  `status` tinyint NULL DEFAULT 1 COMMENT '关注状态：0未关注，1已关注',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `user_id`(`user_id` ASC) USING BTREE,
  INDEX `follow_id`(`follow_id` ASC) USING BTREE,
  CONSTRAINT `user_follow_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `member` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `user_follow_ibfk_2` FOREIGN KEY (`follow_id`) REFERENCES `member` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2042856210201923585 CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '用户关注表' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `user_profile`;

CREATE TABLE `user_profile`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '外键 -> member.id',
  `avatar` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '头像URL',
  `gender` tinyint NULL DEFAULT NULL COMMENT '性别（0未知 1男 2女）',
  `birthday` date NULL DEFAULT NULL COMMENT '生日',
  `bio` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '简介',
  `location` varchar(100) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '所在地',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `user_id`(`user_id` ASC) USING BTREE,
  CONSTRAINT `user_profile_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `member` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1992588649795432449 CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '用户资料表' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `itinerary_revision_resolution`;
DROP TABLE IF EXISTS `itinerary_revision_operation`;
DROP TABLE IF EXISTS `itinerary_revision_proposal`;
DROP TABLE IF EXISTS `itinerary_planning_destination`;
DROP TABLE IF EXISTS `itinerary_planning_request`;
DROP TABLE IF EXISTS `itinerary_command`;
DROP TABLE IF EXISTS `itinerary_item`;
DROP TABLE IF EXISTS `itinerary_day`;
DROP TABLE IF EXISTS `itinerary_destination`;
DROP TABLE IF EXISTS `itinerary`;

CREATE TABLE `itinerary` (
  `id` bigint NOT NULL,
  `owner_member_id` bigint NOT NULL,
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `time_zone` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `base_currency` char(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
  `version` bigint NOT NULL DEFAULT 1,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  INDEX `idx_itinerary_owner_updated` (`owner_member_id`, `updated_at`, `id`),
  INDEX `idx_itinerary_owner_status_updated` (`owner_member_id`, `status`, `updated_at`, `id`),
  CONSTRAINT `fk_itinerary_owner` FOREIGN KEY (`owner_member_id`) REFERENCES `member` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_itinerary_dates` CHECK (`end_date` >= `start_date` AND DATEDIFF(`end_date`, `start_date`) <= 365),
  CONSTRAINT `chk_itinerary_currency` CHECK (`base_currency` REGEXP '^[A-Z]{3}$'),
  CONSTRAINT `chk_itinerary_status` CHECK (`status` IN ('DRAFT', 'PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'ARCHIVED')),
  CONSTRAINT `chk_itinerary_version` CHECK (`version` >= 1)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '旅行行程正式事实表';

CREATE TABLE `itinerary_destination` (
  `id` bigint NOT NULL,
  `itinerary_id` bigint NOT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `country_code` char(2) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `time_zone` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `position` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_itinerary_destination_position` (`itinerary_id`, `position`),
  CONSTRAINT `fk_itinerary_destination_itinerary` FOREIGN KEY (`itinerary_id`) REFERENCES `itinerary` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `chk_itinerary_destination_country` CHECK (`country_code` IS NULL OR `country_code` REGEXP '^[A-Z]{2}$'),
  CONSTRAINT `chk_itinerary_destination_position` CHECK (`position` >= 1024)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '行程有序目的地';

CREATE TABLE `itinerary_day` (
  `id` bigint NOT NULL,
  `itinerary_id` bigint NOT NULL,
  `day_date` date NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_itinerary_day_date` (`itinerary_id`, `day_date`),
  CONSTRAINT `fk_itinerary_day_itinerary` FOREIGN KEY (`itinerary_id`) REFERENCES `itinerary` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '行程自然日';

CREATE TABLE `itinerary_item` (
  `id` bigint NOT NULL,
  `itinerary_id` bigint NOT NULL,
  `itinerary_day_id` bigint NOT NULL,
  `title` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `place_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `start_time` time NULL,
  `end_time` time NULL,
  `notes` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `estimated_cost` decimal(14, 2) NULL,
  `position` bigint NOT NULL,
  `deleted_at` datetime(3) NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  INDEX `idx_itinerary_item_itinerary` (`itinerary_id`, `deleted_at`, `id`),
  INDEX `idx_itinerary_item_day_position` (`itinerary_day_id`, `deleted_at`, `position`, `id`),
  CONSTRAINT `fk_itinerary_item_itinerary` FOREIGN KEY (`itinerary_id`) REFERENCES `itinerary` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_itinerary_item_day` FOREIGN KEY (`itinerary_day_id`) REFERENCES `itinerary_day` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_itinerary_item_time` CHECK ((`start_time` IS NULL AND `end_time` IS NULL) OR (`start_time` IS NOT NULL AND `end_time` IS NOT NULL AND `end_time` > `start_time`)),
  CONSTRAINT `chk_itinerary_item_cost` CHECK (`estimated_cost` IS NULL OR `estimated_cost` >= 0),
  CONSTRAINT `chk_itinerary_item_position` CHECK (`position` >= 1024)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '行程安排项';

CREATE TABLE `itinerary_command` (
  `id` bigint NOT NULL,
  `command_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `member_id` bigint NOT NULL,
  `operation` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `itinerary_id` bigint NULL,
  `expected_version` bigint NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_itinerary_id` bigint NULL,
  `result_item_id` bigint NULL,
  `result_version` bigint NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_itinerary_command_id` (`command_id`),
  INDEX `idx_itinerary_command_member_created` (`member_id`, `created_at`, `id`),
  INDEX `idx_itinerary_command_itinerary_created` (`result_itinerary_id`, `created_at`, `id`),
  CONSTRAINT `fk_itinerary_command_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_itinerary_command_itinerary` FOREIGN KEY (`itinerary_id`) REFERENCES `itinerary` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_itinerary_command_result_itinerary` FOREIGN KEY (`result_itinerary_id`) REFERENCES `itinerary` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_itinerary_command_result_item` FOREIGN KEY (`result_item_id`) REFERENCES `itinerary_item` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_itinerary_command_expected_version` CHECK (`expected_version` >= 0),
  CONSTRAINT `chk_itinerary_command_result_version` CHECK (`result_version` IS NULL OR `result_version` >= 1),
  CONSTRAINT `chk_itinerary_command_hash` CHECK (`request_hash` REGEXP '^[0-9a-f]{64}$')
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '行程命令幂等结果';

CREATE TABLE `itinerary_planning_request` (
  `id` bigint NOT NULL,
  `itinerary_id` bigint NOT NULL,
  `owner_member_id` bigint NOT NULL,
  `schema_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `budget_amount` decimal(14, 2) NOT NULL,
  `budget_currency` char(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `party_size` int NOT NULL,
  `preferences_json` json NOT NULL,
  `status` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
  `version` bigint NOT NULL DEFAULT 1,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  INDEX `idx_itinerary_planning_owner_updated` (`owner_member_id`, `updated_at`, `id`),
  INDEX `idx_itinerary_planning_itinerary_updated` (`itinerary_id`, `updated_at`, `id`),
  CONSTRAINT `fk_itinerary_planning_request_itinerary` FOREIGN KEY (`itinerary_id`) REFERENCES `itinerary` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_itinerary_planning_request_owner` FOREIGN KEY (`owner_member_id`) REFERENCES `member` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_itinerary_planning_request_dates` CHECK (`end_date` >= `start_date` AND DATEDIFF(`end_date`, `start_date`) <= 365),
  CONSTRAINT `chk_itinerary_planning_request_budget` CHECK (`budget_amount` >= 0),
  CONSTRAINT `chk_itinerary_planning_request_currency` CHECK (`budget_currency` REGEXP '^[A-Z]{3}$'),
  CONSTRAINT `chk_itinerary_planning_request_party` CHECK (`party_size` BETWEEN 1 AND 100),
  CONSTRAINT `chk_itinerary_planning_request_status` CHECK (`status` IN ('DRAFT', 'SUBMITTED', 'GENERATING', 'READY', 'FAILED', 'CANCELLED')),
  CONSTRAINT `chk_itinerary_planning_request_version` CHECK (`version` >= 1)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '结构化行程规划请求';

CREATE TABLE `itinerary_planning_destination` (
  `id` bigint NOT NULL,
  `planning_request_id` bigint NOT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `country_code` char(2) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `time_zone` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `position` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_itinerary_planning_destination_position` (`planning_request_id`, `position`),
  CONSTRAINT `fk_itinerary_planning_destination_request` FOREIGN KEY (`planning_request_id`) REFERENCES `itinerary_planning_request` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `chk_itinerary_planning_destination_country` CHECK (`country_code` IS NULL OR `country_code` REGEXP '^[A-Z]{2}$'),
  CONSTRAINT `chk_itinerary_planning_destination_position` CHECK (`position` >= 1024)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '规划请求有序目的地';

CREATE TABLE `itinerary_revision_proposal` (
  `id` bigint NOT NULL,
  `planning_request_id` bigint NOT NULL,
  `itinerary_id` bigint NOT NULL,
  `owner_member_id` bigint NOT NULL,
  `base_itinerary_version` bigint NOT NULL,
  `contract_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'VALIDATING',
  `provider` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `provider_run_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `model_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `workflow_version` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `knowledge_reference_ids_json` json NULL,
  `elapsed_millis` bigint NULL,
  `total_tokens` bigint NULL,
  `failure_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `resolved_at` datetime(3) NULL,
  PRIMARY KEY (`id`),
  INDEX `idx_itinerary_revision_request_created` (`planning_request_id`, `created_at`, `id`),
  INDEX `idx_itinerary_revision_itinerary_status` (`itinerary_id`, `status`, `created_at`, `id`),
  INDEX `idx_itinerary_revision_owner_created` (`owner_member_id`, `created_at`, `id`),
  CONSTRAINT `fk_itinerary_revision_proposal_request` FOREIGN KEY (`planning_request_id`) REFERENCES `itinerary_planning_request` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_itinerary_revision_proposal_itinerary` FOREIGN KEY (`itinerary_id`) REFERENCES `itinerary` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_itinerary_revision_proposal_owner` FOREIGN KEY (`owner_member_id`) REFERENCES `member` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_itinerary_revision_base_version` CHECK (`base_itinerary_version` >= 1),
  CONSTRAINT `chk_itinerary_revision_proposal_status` CHECK (`status` IN ('VALIDATING', 'READY', 'INVALID', 'FAILED', 'CONFIRMED', 'REJECTED', 'EXPIRED')),
  CONSTRAINT `chk_itinerary_revision_elapsed` CHECK (`elapsed_millis` IS NULL OR `elapsed_millis` >= 0),
  CONSTRAINT `chk_itinerary_revision_tokens` CHECK (`total_tokens` IS NULL OR `total_tokens` >= 0)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '不可变 AI 行程修订建议';

CREATE TABLE `itinerary_revision_operation` (
  `id` bigint NOT NULL,
  `proposal_id` bigint NOT NULL,
  `operation_key` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `operation_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `target_date` date NULL,
  `target_item_id` bigint NULL,
  `summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload_json` json NOT NULL,
  `estimated_cost_delta` decimal(14, 2) NULL,
  `validation_status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'VALID',
  `position` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_itinerary_revision_operation_key` (`proposal_id`, `operation_key`),
  UNIQUE INDEX `uk_itinerary_revision_operation_position` (`proposal_id`, `position`),
  INDEX `idx_itinerary_revision_operation_target` (`target_item_id`),
  CONSTRAINT `fk_itinerary_revision_operation_proposal` FOREIGN KEY (`proposal_id`) REFERENCES `itinerary_revision_proposal` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_itinerary_revision_operation_item` FOREIGN KEY (`target_item_id`) REFERENCES `itinerary_item` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_itinerary_revision_operation_type` CHECK (`operation_type` IN ('ADD_ITEM', 'UPDATE_ITEM', 'DELETE_ITEM', 'REORDER_DAY_ITEMS')),
  CONSTRAINT `chk_itinerary_revision_validation_status` CHECK (`validation_status` IN ('VALID', 'INVALID')),
  CONSTRAINT `chk_itinerary_revision_operation_position` CHECK (`position` >= 1024)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '可选择的行程修订建议操作';

CREATE TABLE `itinerary_revision_resolution` (
  `id` bigint NOT NULL,
  `proposal_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `decision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `decision_type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `selected_operations_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `expected_itinerary_version` bigint NULL,
  `itinerary_command_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `result_version` bigint NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_itinerary_revision_resolution_proposal` (`proposal_id`),
  UNIQUE INDEX `uk_itinerary_revision_decision_id` (`decision_id`),
  INDEX `idx_itinerary_revision_resolution_member_created` (`member_id`, `created_at`, `id`),
  CONSTRAINT `fk_itinerary_revision_resolution_proposal` FOREIGN KEY (`proposal_id`) REFERENCES `itinerary_revision_proposal` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_itinerary_revision_resolution_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_itinerary_revision_decision_type` CHECK (`decision_type` IN ('CONFIRM', 'REJECT')),
  CONSTRAINT `chk_itinerary_revision_selection_hash` CHECK (`selected_operations_hash` REGEXP '^[0-9a-f]{64}$'),
  CONSTRAINT `chk_itinerary_revision_expected_version` CHECK (`expected_itinerary_version` IS NULL OR `expected_itinerary_version` >= 1),
  CONSTRAINT `chk_itinerary_revision_result_version` CHECK (`result_version` IS NULL OR `result_version` >= 1),
  CONSTRAINT `chk_itinerary_revision_resolution_shape` CHECK ((`decision_type` = 'REJECT' AND `expected_itinerary_version` IS NULL AND `itinerary_command_id` IS NULL AND `result_version` IS NULL) OR (`decision_type` = 'CONFIRM' AND `expected_itinerary_version` IS NOT NULL AND `itinerary_command_id` IS NOT NULL AND `result_version` IS NOT NULL))
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户对修订建议的幂等决定';

SET FOREIGN_KEY_CHECKS = 1;
