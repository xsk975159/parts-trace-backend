-- 跨链拓扑增量建表脚本（已有数据库执行，不影响既有数据）
-- 用途：数据库模拟「1 主监管链 + 3 业务子链」跨链存证，见 docs/论文与实现对照-5.1-5.2.md 第 5.2 节
-- 执行：mysql -uroot -p parts_trace < cross_chain_migration.sql

CREATE TABLE IF NOT EXISTS chain_info (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    chain_code  VARCHAR(64)  NOT NULL,
    chain_name  VARCHAR(64)  NOT NULL,
    chain_type  VARCHAR(32)  NOT NULL DEFAULT 'CHAINMAKER',
    chain_role  VARCHAR(32)  NOT NULL DEFAULT 'BUSINESS',
    description VARCHAR(255),
    status      TINYINT      DEFAULT 1,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chain_code (chain_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO chain_info (chain_code, chain_name, chain_type, chain_role, description, status) VALUES
('main-regulator', '主监管链', 'CHAINMAKER', 'REGULATOR', '汇聚各业务子链存证摘要，监管方核验入口', 1),
('sub-production', '生产子链', 'CHAINMAKER', 'BUSINESS', '生产环节溯源数据存证', 1),
('sub-quality', '质检子链', 'CHAINMAKER', 'BUSINESS', '质检环节溯源数据存证', 1),
('sub-logistics', '物流子链', 'FISCO_BCOS', 'BUSINESS', '物流环节溯源数据存证（异构链）', 1)
ON DUPLICATE KEY UPDATE chain_name = VALUES(chain_name);

CREATE TABLE IF NOT EXISTS cross_chain_record (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    source_chain_id     VARCHAR(64),
    source_tx_hash      VARCHAR(256),
    source_block_height BIGINT,
    business_type       VARCHAR(64),
    parts_did           VARCHAR(256),
    data_hash           VARCHAR(256),
    object_cid          VARCHAR(256),
    target_chain_id     VARCHAR(64),
    target_tx_hash      VARCHAR(256),
    target_block_height BIGINT,
    status              TINYINT      DEFAULT 1,
    relay_time          DATETIME,
    create_time         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_parts_did (parts_did),
    KEY idx_source_chain (source_chain_id),
    KEY idx_target_chain (target_chain_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
