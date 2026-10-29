-- 仅在启用多机构核验的协调端数据库执行；不改写已有业务数据，可重复执行。
CREATE TABLE IF NOT EXISTS relay_confirmation (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 record_id BIGINT NOT NULL,
 committee_version VARCHAR(16) NOT NULL,
 required_votes INT NOT NULL,
 valid_votes INT NOT NULL DEFAULT 0,
 state VARCHAR(32) NOT NULL,
 proof_json MEDIUMTEXT NULL,
 last_error VARCHAR(1024) NULL,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_relay_confirmation_record(record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
