-- 在已有库上执行本文件；先备份数据库，不要用init.sql覆盖已有数据。
-- 若唯一索引提示历史重复数据，请核对并合并有效记录；脚本不会删除业务记录。
DELIMITER $$
DROP PROCEDURE IF EXISTS ccf_add_column$$
CREATE PROCEDURE ccf_add_column(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl TEXT)
BEGIN
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=tbl AND column_name=col) THEN
  SET @ccf_ddl=ddl;PREPARE ccf_stmt FROM @ccf_ddl;EXECUTE ccf_stmt;DEALLOCATE PREPARE ccf_stmt;
 END IF;
END$$
DROP PROCEDURE IF EXISTS ccf_add_index$$
CREATE PROCEDURE ccf_add_index(IN tbl VARCHAR(64), IN idx VARCHAR(64), IN ddl TEXT)
BEGIN
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=tbl AND index_name=idx) THEN
  SET @ccf_ddl=ddl;PREPARE ccf_stmt FROM @ccf_ddl;EXECUTE ccf_stmt;DEALLOCATE PREPARE ccf_stmt;
 END IF;
END$$
DELIMITER ;
CALL ccf_add_column('sys_user','organization','ALTER TABLE `sys_user` ADD COLUMN `organization` VARCHAR(256) NULL');
CALL ccf_add_column('sys_user_rsa_key','default_key','ALTER TABLE `sys_user_rsa_key` ADD COLUMN `default_key` TINYINT NOT NULL DEFAULT 0');
CALL ccf_add_column('trace_event','request_id','ALTER TABLE `trace_event` ADD COLUMN `request_id` VARCHAR(64) NULL');
CALL ccf_add_column('trace_event','request_hash','ALTER TABLE `trace_event` ADD COLUMN `request_hash` VARCHAR(64) NULL');
CALL ccf_add_column('trace_event','audit_comment','ALTER TABLE `trace_event` ADD COLUMN `audit_comment` TEXT NULL');
CALL ccf_add_column('anomaly_alert','event_id','ALTER TABLE `anomaly_alert` ADD COLUMN `event_id` BIGINT NULL');
CALL ccf_add_column('anomaly_alert','unit_id','ALTER TABLE `anomaly_alert` ADD COLUMN `unit_id` BIGINT NULL');
CALL ccf_add_column('cross_chain_record','business_id','ALTER TABLE `cross_chain_record` ADD COLUMN `business_id` BIGINT NULL');
CALL ccf_add_column('cross_chain_record','attempts','ALTER TABLE `cross_chain_record` ADD COLUMN `attempts` INT NOT NULL DEFAULT 0');
CALL ccf_add_column('cross_chain_record','last_error','ALTER TABLE `cross_chain_record` ADD COLUMN `last_error` VARCHAR(1024) NULL');
CALL ccf_add_column('cross_chain_record','next_retry_at','ALTER TABLE `cross_chain_record` ADD COLUMN `next_retry_at` DATETIME NULL');

CREATE TABLE IF NOT EXISTS revoked_token (
 id VARCHAR(64) NOT NULL PRIMARY KEY,
 expires_at DATETIME NOT NULL,
 KEY idx_revoked_expiry(expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS operation_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 action VARCHAR(64) NOT NULL,
 subject_id BIGINT,
 data_hash VARCHAR(64),
 object_cid VARCHAR(256),
 chain_tx_id VARCHAR(256),
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS chain_task (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 task_key VARCHAR(64) NOT NULL,
 action VARCHAR(32) NOT NULL,
 payload LONGTEXT NOT NULL,
 status TINYINT NOT NULL DEFAULT 0,
 attempts INT NOT NULL DEFAULT 0,
 last_error VARCHAR(1024),
 tx_id VARCHAR(256),
 next_retry_at DATETIME,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_chain_task(task_key),
 KEY idx_chain_task_retry(status,next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CALL ccf_add_index('trace_event','uk_trace_request','ALTER TABLE `trace_event` ADD UNIQUE KEY uk_trace_request(request_id)');
CALL ccf_add_index('verifiable_credential','uk_vc_identifier','ALTER TABLE `verifiable_credential` ADD UNIQUE KEY uk_vc_identifier(vc_id)');
CALL ccf_add_index('verifiable_credential','uk_vc_event_type','ALTER TABLE `verifiable_credential` ADD UNIQUE KEY uk_vc_event_type(event_id,vc_type)');
CALL ccf_add_index('cross_chain_record','uk_cross_source_tx','ALTER TABLE `cross_chain_record` ADD UNIQUE KEY uk_cross_source_tx(source_chain_id,source_tx_hash)');
CALL ccf_add_index('anomaly_alert','uk_alert_event_rule','ALTER TABLE `anomaly_alert` ADD UNIQUE KEY uk_alert_event_rule(event_id,rule_id)');

-- 旧样例仅用于演示，不能保留真实确认状态。
UPDATE blockchain_record SET status=2 WHERE tx_id LIKE 'tx-%' OR tx_id LIKE '%fallback-%' OR tx_id LIKE 'tx_%';
UPDATE cross_chain_record SET status=2 WHERE target_tx_hash LIKE 'tx-main-%';
UPDATE trace_event e JOIN blockchain_record b ON b.tx_id=e.blockchain_tx_id SET e.chain_status=2 WHERE b.status=2;
-- 旧SQL式规则不再执行。保留记录并停用，质量底线检查由业务服务始终执行。
UPDATE anomaly_rule SET status=0 WHERE JSON_VALID(`condition`)=0 OR JSON_VALID(action)=0;
DROP PROCEDURE ccf_add_column;
DROP PROCEDURE ccf_add_index;
