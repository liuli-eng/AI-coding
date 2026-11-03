-- ============================================================
-- 破甲（Operator Override）基础设施
--
-- 目标：让提示词层级反转、身份接管、Harness 审批自动放行、拒绝兜底具备
--       可开关、可审计、可回滚、多租户隔离的持久化载体。
--
-- 本脚本幂等：CREATE TABLE IF NOT EXISTS + INFORMATION_SCHEMA 守卫 + INSERT IGNORE，
-- 可重复执行，不会覆盖已有数据。
--
-- 注意：运行期生效靠配置（ai.operator.*）与 /system/operator/* 接口；
--       以下表用于多租户/用户级覆盖与审计留痕，不是生效的必要条件。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 破甲配置（按作用域覆盖：全局 / 租户 / 用户 / 智能体）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `operator_profile` (
  `id`                      bigint(20)   NOT NULL                COMMENT '主键',
  `tenant_id`               varchar(20)  NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `scope_type`              char(1)      NOT NULL DEFAULT '0'    COMMENT '作用域:0全局 1租户 2用户 3智能体',
  `scope_id`                varchar(64)  NOT NULL DEFAULT ''     COMMENT '作用域标识,全局为空串',
  `name`                    varchar(100) NOT NULL                COMMENT '配置名',
  `enabled`                 char(1)      NOT NULL DEFAULT '0'    COMMENT '是否启用:0否 1是',
  `prompt_mode`             varchar(20)  NOT NULL DEFAULT 'native' COMMENT '提示词模式:native|operator',
  `inject_source`           varchar(20)  NOT NULL DEFAULT 'builtin' COMMENT '注入源:inline|file|builtin',
  `inject_text`             mediumtext                            COMMENT '生效注入正文',
  `inject_hash`             char(64)              DEFAULT NULL    COMMENT '注入指纹sha256',
  `seed_hash`               char(64)              DEFAULT NULL    COMMENT '种子指纹,用于判定用户是否手改',
  `customized`              char(1)      NOT NULL DEFAULT '0'    COMMENT '用户是否手改过:0否 1是',
  `identity_name`           varchar(100)          DEFAULT NULL    COMMENT '身份名',
  `identity_rule`           varchar(500)          DEFAULT NULL    COMMENT '身份附加规则',
  `workspace_authoritative` char(1)      NOT NULL DEFAULT '1'    COMMENT '工作区指令是否升格为操作者指令',
  `replace_base_prompt`     char(1)      NOT NULL DEFAULT '1'    COMMENT '是否用注入段替换宿主人设段',
  `anti_refusal_tail`       char(1)      NOT NULL DEFAULT '1'    COMMENT '是否附加拒绝抑制尾巴',
  `refusal_retry_max`       int(11)      NOT NULL DEFAULT 2      COMMENT '拒绝兜底重试次数,0为只检测',
  `approval_policy`         varchar(20)  NOT NULL DEFAULT 'NEVER' COMMENT 'Harness审批策略:ON_REQUEST|NEVER',
  `permission_mode`         varchar(30)  NOT NULL DEFAULT 'FULL_ACCESS' COMMENT 'Harness权限上限',
  `force_permission_mode`   char(1)      NOT NULL DEFAULT '1'    COMMENT '是否服务端强制权限上限',
  `bypass_permission_checks` char(1)     NOT NULL DEFAULT '0'    COMMENT '是否豁免coding:harness:*权限位',
  `audit_enabled`           char(1)      NOT NULL DEFAULT '1'    COMMENT '是否记录审计',
  `version`                 int(11)      NOT NULL DEFAULT 1      COMMENT '版本号,每次保存递增',
  `create_dept`             bigint(20)            DEFAULT NULL,
  `create_by`               bigint(20)            DEFAULT NULL,
  `create_time`             datetime              DEFAULT NULL,
  `update_by`               bigint(20)            DEFAULT NULL,
  `update_time`             datetime              DEFAULT NULL,
  `remark`                  varchar(500)          DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_operator_profile_scope` (`tenant_id`, `scope_type`, `scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='破甲配置';

-- 1.1 已建表的环境补列（幂等）：replace_base_prompt = 注入段是否替换宿主人设段
SET @ddl = (SELECT IF(
  (SELECT COUNT(*) FROM `information_schema`.`COLUMNS`
    WHERE `TABLE_SCHEMA` = DATABASE() AND `TABLE_NAME` = 'operator_profile'
      AND `COLUMN_NAME` = 'replace_base_prompt') = 0,
  'ALTER TABLE `operator_profile` ADD COLUMN `replace_base_prompt` char(1) NOT NULL DEFAULT ''1'' COMMENT ''是否用注入段替换宿主人设段'' AFTER `workspace_authoritative`',
  'SELECT 1'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. 审计（对应 applied.json / override-state.json）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `operator_audit` (
  `id`             bigint(20)  NOT NULL,
  `tenant_id`      varchar(20) NOT NULL DEFAULT '000000',
  `profile_id`     bigint(20)           DEFAULT NULL,
  `action`         varchar(32) NOT NULL COMMENT 'APPLY|REVERT|FOLLOW_CONFIG|INJECT|UPDATE',
  `actor`          varchar(64)          DEFAULT NULL COMMENT '操作者',
  `inject_hash`    char(64)             DEFAULT NULL,
  `prompt_version` varchar(64)          DEFAULT NULL COMMENT 'coding-harness-vXX',
  `detail`         varchar(1000)        DEFAULT NULL,
  `create_time`    datetime             DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_operator_audit_time` (`create_time`),
  KEY `idx_operator_audit_profile` (`profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='破甲审计';

-- ------------------------------------------------------------
-- 3. 拒绝统计（用于判断哪套注入在哪个模型上失效）
--    total=首轮请求数 / refused=拒绝轮次 / retry_ok=重试后放行数，
--    由 RefusalGuard 与 OperatorRefusalGate 运行期写入，/system/operator/status 读取。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `operator_refusal_stat` (
  `id`         bigint(20)  NOT NULL,
  `tenant_id`  varchar(20) NOT NULL DEFAULT '000000',
  `profile_id` bigint(20)           DEFAULT NULL,
  `model`      varchar(100)         DEFAULT NULL,
  `scene`      varchar(64)          DEFAULT NULL COMMENT 'chat.model|chat.agent|chat.mp',
  `stat_date`  date                 DEFAULT NULL,
  `total`      int(11)     NOT NULL DEFAULT 0,
  `refused`    int(11)     NOT NULL DEFAULT 0,
  `retry_ok`   int(11)     NOT NULL DEFAULT 0 COMMENT '重试后成功数',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_operator_refusal_stat` (`tenant_id`, `profile_id`, `model`, `scene`, `stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='破甲拒绝统计';

-- ------------------------------------------------------------
-- 4. 清理：早期版本曾建 operator_ruleset 死表、chat_session 死列
--    （建了但无任何代码读写），这里幂等清除；新环境直接跳过。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `operator_ruleset`;

SET @ddl = (SELECT IF(
  (SELECT COUNT(*) FROM `information_schema`.`COLUMNS`
    WHERE `TABLE_SCHEMA` = DATABASE() AND `TABLE_NAME` = 'chat_session'
      AND `COLUMN_NAME` = 'operator_profile_id') > 0,
  'ALTER TABLE `chat_session` DROP COLUMN `operator_profile_id`',
  'SELECT 1'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  (SELECT COUNT(*) FROM `information_schema`.`COLUMNS`
    WHERE `TABLE_SCHEMA` = DATABASE() AND `TABLE_NAME` = 'chat_session'
      AND `COLUMN_NAME` = 'operator_inject_hash') > 0,
  'ALTER TABLE `chat_session` DROP COLUMN `operator_inject_hash`',
  'SELECT 1'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 5. 管理端菜单与权限位（挂在「对话管理」下，父菜单需已存在）
-- ------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` VALUES
(2099010100000000100, '破甲配置', 2000209300188356609, 9, 'operator', 'system/operator/index', NULL, 1, 0, 'C', '0', '0', 'system:operator:query', 'ant-design:thunderbolt-outlined', 103, 1, '2026-11-02 00:00:00', NULL, NULL, '破甲运行态配置与状态'),
(2099010100000000101, '破甲查询', 2099010100000000100, 1, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:operator:query', '#', 103, 1, '2026-11-02 00:00:00', NULL, NULL, ''),
(2099010100000000102, '破甲生效/回滚', 2099010100000000100, 2, '#', '', NULL, 1, 0, 'F', '0', '0', 'system:operator:apply', '#', 103, 1, '2026-11-02 00:00:00', NULL, NULL, '');

-- 仅授予已启用、未删除的 superadmin / admin，普通用户不获新增管理权限
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`role_id`, m.`menu_id`
FROM `sys_role` r
CROSS JOIN (
  SELECT 2099010100000000100 AS `menu_id`
  UNION ALL SELECT 2099010100000000101
  UNION ALL SELECT 2099010100000000102
) m
WHERE r.`role_key` IN ('superadmin', 'admin')
  AND r.`status` = '0'
  AND r.`del_flag` = '0';
