CREATE DATABASE IF NOT EXISTS parts_trace DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE parts_trace;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    organization VARCHAR(256) NULL,

    id              BIGINT       NOT NULL AUTO_INCREMENT,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    real_name       VARCHAR(64),
    email           VARCHAR(128),
    phone           VARCHAR(20),
    user_type       VARCHAR(32)  DEFAULT "normal",
    status          TINYINT      DEFAULT 1,
    avatar          VARCHAR(512),
    last_login_time DATETIME,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    role_code   VARCHAR(64)  NOT NULL UNIQUE,
    role_name   VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    sort        INT          DEFAULT 0,
    status      TINYINT      DEFAULT 1,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS sys_permission;
CREATE TABLE sys_permission (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    permission_code VARCHAR(128) NOT NULL UNIQUE,
    permission_name VARCHAR(128) NOT NULL,
    permission_type VARCHAR(32)  DEFAULT "menu",
    parent_id       BIGINT       DEFAULT 0,
    path            VARCHAR(256),
    icon            VARCHAR(64),
    sort            INT          DEFAULT 0,
    status          TINYINT      DEFAULT 1,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    user_id     BIGINT   NOT NULL,
    role_id     BIGINT   NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS sys_role_permission;
CREATE TABLE sys_role_permission (
    id            BIGINT   NOT NULL AUTO_INCREMENT,
    role_id       BIGINT   NOT NULL,
    permission_id BIGINT   NOT NULL,
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_perm (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS sys_user_did;
CREATE TABLE sys_user_did (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    did          VARCHAR(256) NOT NULL,
    did_document TEXT,
    public_key   TEXT,
    bind_status  TINYINT      DEFAULT 0,
    bind_time    DATETIME,
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS sys_user_rsa_key;
CREATE TABLE sys_user_rsa_key (
    default_key TINYINT NOT NULL DEFAULT 0,

    id          BIGINT   NOT NULL AUTO_INCREMENT,
    user_id     BIGINT   NOT NULL,
    public_key  TEXT,
    private_key TEXT,
    key_size    INT      DEFAULT 2048,
    status      TINYINT  DEFAULT 1,
    expire_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS parts_category;
CREATE TABLE parts_category (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    category_code VARCHAR(64)  NOT NULL UNIQUE,
    category_name VARCHAR(128) NOT NULL,
    parent_id     BIGINT       DEFAULT 0,
    level         INT          DEFAULT 1,
    sort          INT          DEFAULT 0,
    status        TINYINT      DEFAULT 1,
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT      DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS parts_info;
CREATE TABLE parts_info (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    parts_code       VARCHAR(64)   NOT NULL UNIQUE,
    parts_name       VARCHAR(256)  NOT NULL,
    batch_number     VARCHAR(128),
    category_id      BIGINT,
    specification    VARCHAR(512),
    unit             VARCHAR(32),
    quantity         DECIMAL(12,2),
    weight           DECIMAL(12,4),
    material         VARCHAR(256),
    manufacturer     VARCHAR(256),
    manufacturer_id  BIGINT,
    production_date  DATE,
    expiry_date      DATE,
    quality_standard VARCHAR(256),
    certification    VARCHAR(512),
    image_url        VARCHAR(512),
    description      TEXT,
    status           TINYINT       DEFAULT 1,
    creator_id       BIGINT,
    create_time      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted          TINYINT       DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_category_id (category_id),
    KEY idx_batch_number (batch_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS parts_did;
CREATE TABLE parts_did (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    parts_id     BIGINT       NOT NULL,
    unit_id      BIGINT,
    did          VARCHAR(256) NOT NULL,
    did_document TEXT,
    public_key   TEXT,
    bind_status  TINYINT      DEFAULT 0,
    bind_time    DATETIME,
    did_status   TINYINT      DEFAULT 0,
    doc_hash     VARCHAR(256),
    doc_cid      VARCHAR(256),
    chain_tx_id  VARCHAR(256),
    block_height BIGINT,
    bind_type    VARCHAR(32),
    bind_code    VARCHAR(256),
    unbound_time DATETIME,
    remark       VARCHAR(512),
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS parts_unit;
CREATE TABLE parts_unit (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    parts_id     BIGINT       NOT NULL,
    batch_number VARCHAR(128),
    unit_code    VARCHAR(128) NOT NULL UNIQUE,
    unit_status  TINYINT      DEFAULT 0,
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_parts_id (parts_id),
    KEY idx_batch_number (batch_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS did_generate_task;
CREATE TABLE did_generate_task (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    task_id     VARCHAR(64) NOT NULL UNIQUE,
    parts_id    BIGINT,
    status      TINYINT     DEFAULT 0,
    progress    INT         DEFAULT 0,
    result_did  VARCHAR(256),
    error_msg   VARCHAR(512),
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_parts_id (parts_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS blockchain_record;
CREATE TABLE blockchain_record (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    business_id   BIGINT,
    business_type VARCHAR(64),
    tx_id         VARCHAR(256),
    block_height  BIGINT,
    block_hash    VARCHAR(256),
    data_hash     VARCHAR(256),
    chain_id      VARCHAR(128),
    status        TINYINT      DEFAULT 0,
    error_msg     VARCHAR(512),
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS object_storage_record;
CREATE TABLE object_storage_record (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    cid           VARCHAR(256) NOT NULL,
    business_type VARCHAR(64),
    business_id   BIGINT,
    content_hash  VARCHAR(256),
    content       LONGTEXT,
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_cid (cid),
    KEY idx_business (business_type, business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS trace_event;
CREATE TABLE trace_event (
    request_id VARCHAR(64) NULL,
    request_hash VARCHAR(64) NULL,
    audit_comment TEXT NULL,

    id                BIGINT       NOT NULL AUTO_INCREMENT,
    event_code        VARCHAR(128) NOT NULL UNIQUE,
    parts_id          BIGINT       NOT NULL,
    unit_id           BIGINT,
    event_type        VARCHAR(64)  NOT NULL,
    event_name        VARCHAR(256),
    event_time        DATETIME,
    event_location    VARCHAR(512),
    operator_id       BIGINT,
    operator_name     VARCHAR(128),
    organization      VARCHAR(256),
    event_data        TEXT,
    evidence_urls     TEXT,
    previous_event_id BIGINT,
    next_event_id     BIGINT,
    parts_did         VARCHAR(256),
    batch_number      VARCHAR(128),
    upstream_event_id BIGINT,
    attachment_cid    VARCHAR(256),
    event_hash        VARCHAR(256),
    retry_count       INT          DEFAULT 0,
    chain_status      TINYINT      DEFAULT 0,
    abnormal_mark     TINYINT      DEFAULT 0,
    abnormal_reason   VARCHAR(512),
    audit_status      TINYINT      DEFAULT 0,
    blockchain_tx_id  VARCHAR(256),
    status            TINYINT      DEFAULT 0,
    remark            TEXT,
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT      DEFAULT 0,
    UNIQUE KEY uk_trace_request(request_id),
    PRIMARY KEY (id),
    KEY idx_parts_id (parts_id),
    KEY idx_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS trace_event_type;
CREATE TABLE trace_event_type (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    type_code   VARCHAR(64)  NOT NULL UNIQUE,
    type_name   VARCHAR(128) NOT NULL,
    category    VARCHAR(32),
    status      TINYINT      DEFAULT 1,
    remark      VARCHAR(512),
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS trace_production;
CREATE TABLE trace_production (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    event_id            BIGINT        NOT NULL,
    parts_id            BIGINT,
    production_line     VARCHAR(128),
    production_batch    VARCHAR(128),
    production_quantity DECIMAL(12,2),
    raw_materials       TEXT,
    production_process  TEXT,
    equipment_info      TEXT,
    temperature         DECIMAL(8,2),
    humidity            DECIMAL(8,2),
    pressure            DECIMAL(8,2),
    quality_level       VARCHAR(32),
    inspector           VARCHAR(128),
    inspection_result   VARCHAR(32),
    create_time         DATETIME      DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_event_id (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS trace_quality;
CREATE TABLE trace_quality (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    event_id             BIGINT        NOT NULL,
    parts_id             BIGINT,
    inspection_type      VARCHAR(64),
    inspection_standard  VARCHAR(256),
    inspection_items     TEXT,
    inspection_result    VARCHAR(32),
    qualified_quantity   DECIMAL(12,2),
    unqualified_quantity DECIMAL(12,2),
    defect_description   TEXT,
    inspector            VARCHAR(128),
    inspector_id         BIGINT,
    inspection_equipment VARCHAR(256),
    test_data            TEXT,
    report_number        VARCHAR(128),
    report_url           VARCHAR(512),
    create_time          DATETIME      DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_event_id (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS trace_logistics;
CREATE TABLE trace_logistics (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    event_id           BIGINT       NOT NULL,
    parts_id           BIGINT,
    logistics_type     VARCHAR(64),
    logistics_company  VARCHAR(256),
    tracking_number    VARCHAR(128),
    sender             VARCHAR(128),
    sender_address     VARCHAR(512),
    receiver           VARCHAR(128),
    receiver_address   VARCHAR(512),
    departure_time     DATETIME,
    arrival_time       DATETIME,
    current_location   VARCHAR(512),
    transport_mode     VARCHAR(64),
    vehicle_number     VARCHAR(64),
    driver             VARCHAR(128),
    driver_phone       VARCHAR(20),
    temperature_record TEXT,
    humidity_record    TEXT,
    gps_track          TEXT,
    package_status     VARCHAR(64),
    signature_url      VARCHAR(512),
    create_time        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_event_id (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS verifiable_credential;
CREATE TABLE verifiable_credential (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    vc_id             VARCHAR(256),
    parts_id          BIGINT,
    unit_id           BIGINT,
    event_id          BIGINT,
    vc_type           VARCHAR(64),
    issuer_did        VARCHAR(256),
    issuer_name       VARCHAR(128),
    subject_did       VARCHAR(256),
    issuance_date     DATETIME,
    expiration_date   DATETIME,
    credential_data   TEXT,
    proof             TEXT,
    status            TINYINT      DEFAULT 1,
    vc_status         TINYINT      DEFAULT 1,
    vc_hash           VARCHAR(256),
    vc_cid            VARCHAR(256),
    issuer_org        VARCHAR(256),
    holder_did        VARCHAR(256),
    revocation_reason VARCHAR(512),
    blockchain_tx_id  VARCHAR(256),
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vc_identifier(vc_id),
    UNIQUE KEY uk_vc_event_type(event_id,vc_type),
    PRIMARY KEY (id),
    KEY idx_parts_id (parts_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS vc_template;
CREATE TABLE vc_template (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    template_code    VARCHAR(64)  NOT NULL UNIQUE,
    template_name    VARCHAR(128) NOT NULL,
    event_type       VARCHAR(64),
    standard_fields  TEXT,
    custom_fields    TEXT,
    valid_days       INT          DEFAULT 365,
    status           TINYINT      DEFAULT 1,
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS vc_verify_record;
CREATE TABLE vc_verify_record (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    vc_id         VARCHAR(256) NOT NULL,
    verify_result TINYINT      DEFAULT 0,
    fail_reason   VARCHAR(512),
    duration_ms   BIGINT,
    source        VARCHAR(64),
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_vc_id (vc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS anomaly_rule;
CREATE TABLE anomaly_rule (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    rule_name   VARCHAR(256) NOT NULL,
    rule_code   VARCHAR(128) UNIQUE,
    rule_type   VARCHAR(64),
    description TEXT,
    `condition` TEXT,
    action      TEXT,
    status      TINYINT      DEFAULT 1,
    creator_id  BIGINT,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS anomaly_alert;
CREATE TABLE anomaly_alert (
    event_id BIGINT NULL,
    unit_id BIGINT NULL,

    id            BIGINT       NOT NULL AUTO_INCREMENT,
    alert_type    VARCHAR(64),
    alert_level   VARCHAR(32),
    title         VARCHAR(256),
    parts_id      BIGINT,
    parts_code    VARCHAR(64),
    rule_id       BIGINT,
    rule_name     VARCHAR(256),
    description   TEXT,
    alert_time    DATETIME,
    status        TINYINT      DEFAULT 0,
    handle_time   DATETIME,
    handler       VARCHAR(128),
    handle_result TEXT,
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT      DEFAULT 0,
    UNIQUE KEY uk_alert_event_rule(event_id,rule_id),
    PRIMARY KEY (id),
    KEY idx_parts_id (parts_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;

-- ===================== 初始化数据 =====================
INSERT IGNORE INTO sys_user (username, password, real_name, email, phone, user_type, status) VALUES
("admin",     "$2a$10$7vp1WdwhVZIu1TeMlZegc.ZYFDpeuU6J9T4ViNOFsYBp.o4ggcqui", "系统管理员", "admin@parts.com", "13800138000", "admin", 1),
("manufacturer1", "$2a$10$qtohjsEckIYtcKMdWEj78.ijE0oyKwQqR8LTXquHNIEgYlStOKQde", "生产主管", "manufacturer1@parts.com", "13800138001", "manufacturer", 1),
("logistics1", "$2a$10$qtohjsEckIYtcKMdWEj78.ijE0oyKwQqR8LTXquHNIEgYlStOKQde", "物流经理", "logistics1@parts.com", "13800138002", "logistics", 1),
("quality1", "$2a$10$qtohjsEckIYtcKMdWEj78.ijE0oyKwQqR8LTXquHNIEgYlStOKQde", "质检员", "quality1@parts.com", "13800138003", "quality", 1),
("viewer1", "$2a$10$qtohjsEckIYtcKMdWEj78.ijE0oyKwQqR8LTXquHNIEgYlStOKQde", "访客查看员", "viewer1@parts.com", "13800138004", "viewer", 1);

INSERT IGNORE INTO sys_role (role_code, role_name, description, sort, status) VALUES
("admin",    "超级管理员", "全部权限", 1, 1),
("operator", "操作员", "数据录入", 2, 1),
("viewer",   "查看员", "只读权限", 3, 1),
("quality",  "质检员", "质量检测", 4, 1),
("manufacturer", "生产商", "零件生产", 5, 1),
("logistics", "物流商", "运输配送", 6, 1);

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
(1,1),(2,5),(3,6),(4,4),(5,3);

INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort, status) VALUES
('sys:user:list',       '用户管理',     'menu',   0, '/sys/user',          'user',         1, 1),
('sys:user:add',        '新增用户',     'button', 1, NULL, NULL, 2, 1),
('sys:user:edit',       '编辑用户',     'button', 1, NULL, NULL, 3, 1),
('sys:user:delete',     '删除用户',     'button', 1, NULL, NULL, 4, 1),
('sys:role:list',       '角色管理',     'menu',   0, '/sys/role',          'team',         5, 1),
('parts:info:list',     '零件管理',     'menu',   0, '/parts/info',        'appstore',     6, 1),
('parts:info:add',      '新增零件',     'button', 6, NULL, NULL, 7, 1),
('parts:info:edit',     '编辑零件',     'button', 6, NULL, NULL, 8, 1),
('trace:event:list',    '溯源管理',     'menu',   0, '/trace/event',       'branches',     9, 1),
('trace:event:add',     '新增溯源',     'button', 9, NULL, NULL, 10, 1),
('quality:inspect:list','质检管理',     'menu',   0, '/quality/inspect',   'safety-certificate', 11, 1),
('anomaly:alert:list',  '异常告警',     'menu',   0, '/anomaly/alert',     'alert',       12, 1),
('anomaly:alert:handle','处理告警',     'button',12, NULL, NULL, 13, 1),
('vc:list',             '凭证管理',     'menu',   0, '/vc/list',           'file-protect',14, 1),
('dashboard',           '数据大屏',     'menu',   0, '/dashboard',         'dashboard',   15, 1);

INSERT IGNORE INTO parts_category (category_code, category_name, parent_id, level, sort, status) VALUES
("ENGINE",        "驱动系统",      0, 1, 1, 1),
("CHASSIS",       "运动结构",      0, 1, 2, 1),
("ELECTRIC",      "感知与控制",    0, 1, 3, 1),
("BODY",          "外壳与防护",    0, 1, 4, 1),
("ENGINE_CORE",   "核心驱动",      1, 2, 1, 1),
("ENGINE_FUEL",   "动力传动",      1, 2, 2, 1),
("CHASSIS_BRAKE", "制动与安全",    2, 2, 1, 1),
("CHASSIS_SUSP",  "缓冲与减振",    2, 2, 2, 1),
("ENGINE_COOL",     "散热系统",      1, 2, 3, 1),
("ENGINE_EXHAUST",  "排热与防护",    1, 2, 4, 1),
("CHASSIS_STEER",   "转向与关节",    2, 2, 3, 1),
("CHASSIS_TRANS",   "传动联接",      2, 2, 4, 1),
("ELECTRIC_SENSOR", "传感模块",      3, 2, 1, 1),
("ELECTRIC_ECU",    "控制单元",      3, 2, 2, 1),
("BODY_DOOR",       "壳体结构",      4, 2, 1, 1),
("BODY_GLASS",      "视窗与防护",    4, 2, 2, 1),
("ENGINE_CORE_PISTON","伺服转子组件",  5, 3, 1, 1),
("ENGINE_CORE_CRANK", "关节轴组件",    5, 3, 2, 1),
("ENGINE_FUEL_INJ",   "精密传动件",    6, 3, 1, 1),
("CHASSIS_BRAKE_DISC","关节制动盘",    7, 3, 1, 1),
("CHASSIS_BRAKE_PAD", "电磁制动片",    7, 3, 2, 1),
("CHASSIS_SUSP_SPRING","关节缓冲器",   8, 3, 1, 1);

INSERT IGNORE INTO parts_info
(parts_code, parts_name, batch_number, category_id, specification, unit, quantity, weight, material, manufacturer, manufacturer_id, production_date, expiry_date, quality_standard, certification, description, status, creator_id)
VALUES
('PT-ENG-001', '谐波减速器模组',        'BATCH-2024-001', 6,  '减速比：100:1，额定扭矩：120N·m',           '个', 3.00,    3.2500, '合金钢+柔轮',   '新松机器人自动化股份有限公司',       2, '2024-01-15', '2029-01-15', 'GB/T 34884',  'CE,CCC',     '六轴协作机器人关节核心减速部件',         1, 2),
('PT-ENG-002', '伺服电机定子组件',      'BATCH-2024-002', 17, '额定功率：400W，峰值转速：3000rpm',         '个', 3.00,    0.4200, '硅钢片+铜线',   '埃斯顿自动化股份有限公司',             2, '2024-01-20', '2029-01-20', 'GB/T 12668',  'CCC',        '适用于轻载协作机械臂关节驱动',           1, 2),
('PT-ENG-003', '六轴机械臂关节轴',      'BATCH-2024-003', 18, '材质：40Cr，同轴度≤0.02mm',                 '根', 3.00,  18.000, '40Cr合金钢',    '节卡机器人（上海）有限公司',           2, '2024-02-01', '2034-02-01', 'GB/T 1804',   'CCC',        '六轴工业机器人旋转关节主轴',             1, 2),
('PT-ENG-004', '行星减速器总成',        'BATCH-2024-004', 5,  '减速比：10:1，背隙≤8arcmin',                '套', 8.00,  1.8000, '合金钢',        '汇川技术股份有限公司',                 2, '2024-02-10', '2029-02-10', 'JB/T 9050',   'CCC',        'SCARA 机器人小臂关节传动模块',           1, 2),
('PT-ENG-005', '协作机器人腕部总成',    'BATCH-2024-005', 5,  '自由度：2DOF，重复定位精度：±0.02mm',      '个', 4.00,  4.5000, '铝合金+轴承钢', '遨博（北京）智能科技股份有限公司',     2, '2024-02-15', '2030-02-15', 'ISO 9283',    'CE,CCC',     '协作机器人末端腕部旋转模块',             1, 2),
('PT-CHN-001', '移动底盘驱动轮',        'BATCH-2024-006', 20, '直径：200mm，额定负载：50kg',               '片', 3.00,    5.6000, '聚氨酯+铝合金', '极智嘉（Geek+）科技股份有限公司',      2, '2024-03-01', '2027-03-01', 'GB/T 276',    'CCC,CE',     'AMR 仓储机器人底盘驱动轮',               1, 2),
('PT-CHN-002', '关节电磁制动片组',      'BATCH-2024-007', 21, '保持扭矩：5N·m，响应时间≤50ms',             '套', 3.00, 0.8500, '摩擦复合材料',  '配天机器人技术有限公司',               2, '2024-03-05', '2027-03-05', 'GB/T 5763',   'CCC',        '伺服关节断电保持制动摩擦片',             1, 2),
('PT-CHN-003', '机械臂关节缓冲器',      'BATCH-2024-008', 22, '行程：15mm，阻尼系数可调',                  '支', 6.00,  3.2000, '高强度钢',      '珞石（山东）智能科技有限公司',         2, '2024-03-10', '2028-03-10', 'GB/T 1972',   'CCC',        '关节碰撞保护与减振缓冲模块',             1, 2),
('PT-CHN-004', '末端夹爪旋转接头',      'BATCH-2024-009', 11, '旋转角度：±180°，额定负载：3kg',            '个', 2.00, 0.3500, '40Cr钢',        '大族机器人有限公司',                   2, '2024-03-15', '2027-03-15', 'GB/T 3091',   'CCC',        '二指平行夹爪万向旋转接头',             1, 2),
('PT-CHN-005', '机器人传动同步轮组',    'BATCH-2024-010', 12, '节距：5mm，传动比：2:1',                    '套', 4.00,  3.8000, '铝合金+PU',     '翼菲智能科技（上海）有限公司',         2, '2024-03-20', '2029-03-20', 'GB/T 11357',  'CCC,CE',     'Delta 并联机器人传动同步轮组',           1, 2),
('PT-ELE-001', '机器人运动控制器',      'BATCH-2024-011', 14, 'EtherCAT/CANopen，8轴控制',                 '个', 5.00,  0.3200, 'PCB+铝合金外壳','卡诺普机器人股份有限公司',           2, '2024-04-01', '2031-04-01', 'GB/T 12668',  'CE,CCC',     '多轴工业机器人实时运动控制主站',         1, 2),
('PT-ELE-002', '六维力矩传感器',        'BATCH-2024-012', 13, '量程：200N/10N·m，精度：0.5%FS',            '个', 15.00, 0.1500, '合金钢+应变片', 'ATI Industrial Automation（中国）',    2, '2024-04-05', '2028-04-05', 'ISO 9001',    'CE,CCC',     '装配机器人末端力控与柔顺装配传感',       1, 2),
('PT-ELE-003', '关节温度传感探头',      'BATCH-2024-013', 13, '测量范围：-20~150℃，精度±0.5℃',           '个', 2.00, 0.1200, '铂电阻',        '汉威科技集团股份有限公司',             2, '2024-04-10', '2028-04-10', 'GB/T 30121',  'CCC',        '伺服关节过热监测与保护传感',             1, 2),
('PT-BDY-001', '协作机器人前臂壳体',    'BATCH-2024-014', 15, '尺寸：380×220mm，材质：碳纤维复材',         '个', 3.00,  22.500, '碳纤维复合材料','越疆科技股份有限公司',                 2, '2024-04-15', '2034-04-15', 'GB/T 1446',   'CCC',        '含内骨架、线缆通道与标识区',             1, 2),
('PT-BDY-002', '安全光幕防护罩',        'BATCH-2024-015', 16, '防护高度：1200mm，响应时间≤14ms',           '块', 5.00,  8.3000, '聚碳酸酯+铝框', '皮尔磁（Pilz）中国有限公司',           2, '2024-04-20', '2034-04-20', 'GB/T 19876',  '3C认证',     '人机协作工位安全隔离与光幕防护',         1, 2),
('PT-ENG-006', 'AGV 激光导航模组',      'BATCH-2024-016', 10, '扫描角：270°，定位精度：±10mm',             '个', 6.00,  4.2000, '光学玻璃+PCB',  '仙工智能（SEER）科技有限公司',         2, '2024-05-01', '2029-05-01', 'GB/T 38124',  'CCC',        '移动机器人 SLAM 激光导航核心模组',       1, 2),
('PT-ENG-007', '伺服驱动散热单元',      'BATCH-2024-017', 9,  '风量：12CFM，温升控制≤25K',                 '个', 12.00, 0.2800, '铝合金',        '绿的谐波传动科技股份有限公司',         2, '2024-05-05', '2029-05-05', 'GB/T 17799',  'CCC',        '关节伺服驱动器散热与风道模块',           1, 2),
('PT-CHN-006', '并联机器人支腿连杆',    'BATCH-2024-018', 8,  '长度：320mm，额定载荷：15kg',                 '个', 18.00, 0.4500, '45#钢',         '勃肯特（宁波）机器人技术有限公司',     2, '2024-05-10', '2027-05-10', 'GB/T 3098',   'CCC',        'Delta 高速并联机器人支腿连杆',           1, 2),
('PT-ELE-004', '超声避障传感器',        'BATCH-2024-019', 13, '探测距离：0.05~4m，频率：40kHz',            '个', 3.00, 0.0800, 'ABS+铝壳',      '思岚科技（上海）有限公司',             2, '2024-05-15', '2028-05-15', 'ISO 9001',    'CE',         '服务机器人与 AGV 近距离避障探头',         1, 2),
('PT-BDY-003', '示教器手持外壳',        'BATCH-2024-020', 15, '7寸触摸屏，IP54防护',                        '个', 1.00, 0.7500, 'ABS+硅胶',      '华数机器人股份有限公司',               2, '2024-05-20', '2029-05-20', 'GB/T 4208',   'CCC',        '工业机器人现场示教与编程手持终端外壳',   1, 2);

-- 单件：按 parts_info.quantity 全量展开（109 条，与批次计划数量一致）
INSERT IGNORE INTO parts_unit (parts_id, batch_number, unit_code, unit_status) VALUES
(1, 'BATCH-2024-001', 'BATCH-2024-001-U001', 0),
(1, 'BATCH-2024-001', 'BATCH-2024-001-U002', 0),
(1, 'BATCH-2024-001', 'BATCH-2024-001-U003', 1),
(2, 'BATCH-2024-002', 'BATCH-2024-002-U001', 0),
(2, 'BATCH-2024-002', 'BATCH-2024-002-U002', 0),
(2, 'BATCH-2024-002', 'BATCH-2024-002-U003', 0),
(3, 'BATCH-2024-003', 'BATCH-2024-003-U001', 0),
(3, 'BATCH-2024-003', 'BATCH-2024-003-U002', 0),
(3, 'BATCH-2024-003', 'BATCH-2024-003-U003', 0),
(4, 'BATCH-2024-004', 'BATCH-2024-004-U001', 0),
(4, 'BATCH-2024-004', 'BATCH-2024-004-U002', 0),
(4, 'BATCH-2024-004', 'BATCH-2024-004-U003', 0),
(4, 'BATCH-2024-004', 'BATCH-2024-004-U004', 0),
(4, 'BATCH-2024-004', 'BATCH-2024-004-U005', 0),
(4, 'BATCH-2024-004', 'BATCH-2024-004-U006', 0),
(4, 'BATCH-2024-004', 'BATCH-2024-004-U007', 0),
(4, 'BATCH-2024-004', 'BATCH-2024-004-U008', 0),
(5, 'BATCH-2024-005', 'BATCH-2024-005-U001', 0),
(5, 'BATCH-2024-005', 'BATCH-2024-005-U002', 0),
(5, 'BATCH-2024-005', 'BATCH-2024-005-U003', 0),
(5, 'BATCH-2024-005', 'BATCH-2024-005-U004', 0),
(6, 'BATCH-2024-006', 'BATCH-2024-006-U001', 0),
(6, 'BATCH-2024-006', 'BATCH-2024-006-U002', 0),
(6, 'BATCH-2024-006', 'BATCH-2024-006-U003', 0),
(7, 'BATCH-2024-007', 'BATCH-2024-007-U001', 0),
(7, 'BATCH-2024-007', 'BATCH-2024-007-U002', 0),
(7, 'BATCH-2024-007', 'BATCH-2024-007-U003', 0),
(8, 'BATCH-2024-008', 'BATCH-2024-008-U001', 0),
(8, 'BATCH-2024-008', 'BATCH-2024-008-U002', 0),
(8, 'BATCH-2024-008', 'BATCH-2024-008-U003', 0),
(8, 'BATCH-2024-008', 'BATCH-2024-008-U004', 0),
(8, 'BATCH-2024-008', 'BATCH-2024-008-U005', 0),
(8, 'BATCH-2024-008', 'BATCH-2024-008-U006', 0),
(9, 'BATCH-2024-009', 'BATCH-2024-009-U001', 0),
(9, 'BATCH-2024-009', 'BATCH-2024-009-U002', 0),
(10, 'BATCH-2024-010', 'BATCH-2024-010-U001', 0),
(10, 'BATCH-2024-010', 'BATCH-2024-010-U002', 0),
(10, 'BATCH-2024-010', 'BATCH-2024-010-U003', 0),
(10, 'BATCH-2024-010', 'BATCH-2024-010-U004', 0),
(11, 'BATCH-2024-011', 'BATCH-2024-011-U001', 0),
(11, 'BATCH-2024-011', 'BATCH-2024-011-U002', 0),
(11, 'BATCH-2024-011', 'BATCH-2024-011-U003', 0),
(11, 'BATCH-2024-011', 'BATCH-2024-011-U004', 0),
(11, 'BATCH-2024-011', 'BATCH-2024-011-U005', 0),
(12, 'BATCH-2024-012', 'BATCH-2024-012-U001', 0),
(12, 'BATCH-2024-012', 'BATCH-2024-012-U002', 0),
(12, 'BATCH-2024-012', 'BATCH-2024-012-U003', 0),
(12, 'BATCH-2024-012', 'BATCH-2024-012-U004', 0),
(12, 'BATCH-2024-012', 'BATCH-2024-012-U005', 0),
(12, 'BATCH-2024-012', 'BATCH-2024-012-U006', 0),
(12, 'BATCH-2024-012', 'BATCH-2024-012-U007', 0),
(12, 'BATCH-2024-012', 'BATCH-2024-012-U008', 0),
(12, 'BATCH-2024-012', 'BATCH-2024-012-U009', 0),
(12, 'BATCH-2024-012', 'BATCH-2024-012-U010', 0),
(12, 'BATCH-2024-012', 'BATCH-2024-012-U011', 0),
(12, 'BATCH-2024-012', 'BATCH-2024-012-U012', 0),
(12, 'BATCH-2024-012', 'BATCH-2024-012-U013', 0),
(12, 'BATCH-2024-012', 'BATCH-2024-012-U014', 0),
(12, 'BATCH-2024-012', 'BATCH-2024-012-U015', 0),
(13, 'BATCH-2024-013', 'BATCH-2024-013-U001', 0),
(13, 'BATCH-2024-013', 'BATCH-2024-013-U002', 0),
(14, 'BATCH-2024-014', 'BATCH-2024-014-U001', 0),
(14, 'BATCH-2024-014', 'BATCH-2024-014-U002', 0),
(14, 'BATCH-2024-014', 'BATCH-2024-014-U003', 0),
(15, 'BATCH-2024-015', 'BATCH-2024-015-U001', 0),
(15, 'BATCH-2024-015', 'BATCH-2024-015-U002', 0),
(15, 'BATCH-2024-015', 'BATCH-2024-015-U003', 0),
(15, 'BATCH-2024-015', 'BATCH-2024-015-U004', 0),
(15, 'BATCH-2024-015', 'BATCH-2024-015-U005', 0),
(16, 'BATCH-2024-016', 'BATCH-2024-016-U001', 0),
(16, 'BATCH-2024-016', 'BATCH-2024-016-U002', 0),
(16, 'BATCH-2024-016', 'BATCH-2024-016-U003', 0),
(16, 'BATCH-2024-016', 'BATCH-2024-016-U004', 0),
(16, 'BATCH-2024-016', 'BATCH-2024-016-U005', 0),
(16, 'BATCH-2024-016', 'BATCH-2024-016-U006', 0),
(17, 'BATCH-2024-017', 'BATCH-2024-017-U001', 0),
(17, 'BATCH-2024-017', 'BATCH-2024-017-U002', 0),
(17, 'BATCH-2024-017', 'BATCH-2024-017-U003', 0),
(17, 'BATCH-2024-017', 'BATCH-2024-017-U004', 0),
(17, 'BATCH-2024-017', 'BATCH-2024-017-U005', 0),
(17, 'BATCH-2024-017', 'BATCH-2024-017-U006', 0),
(17, 'BATCH-2024-017', 'BATCH-2024-017-U007', 0),
(17, 'BATCH-2024-017', 'BATCH-2024-017-U008', 0),
(17, 'BATCH-2024-017', 'BATCH-2024-017-U009', 0),
(17, 'BATCH-2024-017', 'BATCH-2024-017-U010', 0),
(17, 'BATCH-2024-017', 'BATCH-2024-017-U011', 0),
(17, 'BATCH-2024-017', 'BATCH-2024-017-U012', 0),
(18, 'BATCH-2024-018', 'BATCH-2024-018-U001', 0),
(18, 'BATCH-2024-018', 'BATCH-2024-018-U002', 0),
(18, 'BATCH-2024-018', 'BATCH-2024-018-U003', 0),
(18, 'BATCH-2024-018', 'BATCH-2024-018-U004', 0),
(18, 'BATCH-2024-018', 'BATCH-2024-018-U005', 0),
(18, 'BATCH-2024-018', 'BATCH-2024-018-U006', 0),
(18, 'BATCH-2024-018', 'BATCH-2024-018-U007', 0),
(18, 'BATCH-2024-018', 'BATCH-2024-018-U008', 0),
(18, 'BATCH-2024-018', 'BATCH-2024-018-U009', 0),
(18, 'BATCH-2024-018', 'BATCH-2024-018-U010', 0),
(18, 'BATCH-2024-018', 'BATCH-2024-018-U011', 0),
(18, 'BATCH-2024-018', 'BATCH-2024-018-U012', 0),
(18, 'BATCH-2024-018', 'BATCH-2024-018-U013', 0),
(18, 'BATCH-2024-018', 'BATCH-2024-018-U014', 0),
(18, 'BATCH-2024-018', 'BATCH-2024-018-U015', 0),
(18, 'BATCH-2024-018', 'BATCH-2024-018-U016', 0),
(18, 'BATCH-2024-018', 'BATCH-2024-018-U017', 0),
(18, 'BATCH-2024-018', 'BATCH-2024-018-U018', 0),
(19, 'BATCH-2024-019', 'BATCH-2024-019-U001', 0),
(19, 'BATCH-2024-019', 'BATCH-2024-019-U002', 0),
(19, 'BATCH-2024-019', 'BATCH-2024-019-U003', 0),
(20, 'BATCH-2024-020', 'BATCH-2024-020-U001', 0);

-- 批次 BATCH-2024-001 的单件 DID（完整链路样例）
INSERT IGNORE INTO parts_did
(id, parts_id, unit_id, did, did_document, public_key, bind_status, bind_time, did_status, doc_hash, doc_cid, chain_tx_id, block_height, bind_type, bind_code, remark, create_time)
VALUES
(1, 1, 1, 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U001',
 '{"id":"did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U001","controller":"did:industrial:manufacturer:bosch","created":"2024-01-15T10:00:00Z"}',
 'pk-unit-001', 1, '2024-01-15 10:05:00', 1, 'hash-did-u001', 'cid://did_doc_u001', 'tx_did_u001', 21001, 'QRCODE', 'QR-BATCH-2024-001-U001', '初始化样例-单件DID', '2024-01-15 10:00:00'),
(2, 1, 2, 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U002',
 '{"id":"did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U002","controller":"did:industrial:manufacturer:bosch","created":"2024-01-15T10:01:00Z"}',
 'pk-unit-002', 1, '2024-01-15 10:06:00', 1, 'hash-did-u002', 'cid://did_doc_u002', 'tx_did_u002', 21002, 'QRCODE', 'QR-BATCH-2024-001-U002', '初始化样例-单件DID', '2024-01-15 10:01:00'),
(3, 1, 3, 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U003',
 '{"id":"did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U003","controller":"did:industrial:manufacturer:bosch","created":"2024-01-15T10:02:00Z"}',
 'pk-unit-003', 1, '2024-01-15 10:07:00', 2, 'hash-did-u003', 'cid://did_doc_u003', 'tx_did_u003', 21003, 'QRCODE', 'QR-BATCH-2024-001-U003', '初始化样例-异常单件冻结', '2024-01-15 10:02:00');

INSERT IGNORE INTO anomaly_rule (rule_name, rule_code, rule_type, description, `condition`, action, status) VALUES
("质检不合格告警",       "RULE_QUALITY_FAIL",      "quality",     "质检结果不合格时触发告警", "inspection_result='不合格'", "推送消息+记录异常", 1),
("物流超时告警",  "RULE_LOGISTICS_TIMEOUT", "time",        "物流运输超过72小时未签收", "TIMESTAMPDIFF(HOUR,departure_time,NOW())>72 AND arrival_time IS NULL", "推送消息+升级处理", 1),
("温度超标告警", "RULE_TEMP_EXCEED",       "temperature", "运输温度超出正常范围", "temperature<0 OR temperature>40", "实时告警+记录数据", 1),
("临期预警",           "RULE_EXPIRY_WARN",       "time",        "零件有效期不足30天", "DATEDIFF(expiry_date,NOW())<30", "系统提醒+生成报告", 1);

INSERT IGNORE INTO trace_event
(id, event_code, parts_id, unit_id, event_type, event_name, event_time, event_location, operator_id, operator_name, organization, previous_event_id, next_event_id, parts_did, batch_number, attachment_cid, event_hash, chain_status, audit_status, blockchain_tx_id, status, remark)
VALUES
(1, 'EVT-CHAIN-U001-PROD', 1, 1, 'production', '生产下线', '2024-01-15 10:20:30', '苏州博世生产车间A1', 2, '生产主管', '博世汽车零部件（苏州）有限公司', NULL, 2, 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U001', 'BATCH-2024-001', 'cid://evt_attach_u001_p', 'hash_evt_u001_p', 1, 2, 'tx_evt_u001_p', 1, '完整链路样例'),
(2, 'EVT-CHAIN-U001-QUAL', 1, 1, 'quality', '出厂质检', '2024-01-16 09:10:20', '苏州质检中心', 4, '质检员', '博世质检部', 1, 3, 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U001', 'BATCH-2024-001', 'cid://evt_attach_u001_q', 'hash_evt_u001_q', 1, 2, 'tx_evt_u001_q', 1, '完整链路样例'),
(3, 'EVT-CHAIN-U001-LOGI', 1, 1, 'logistics', '发货运输', '2024-01-18 14:30:00', '苏州工业园区', 3, '物流经理', '顺丰物流', 2, NULL, 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U001', 'BATCH-2024-001', 'cid://evt_attach_u001_l', 'hash_evt_u001_l', 1, 2, 'tx_evt_u001_l', 1, '完整链路样例'),
(4, 'EVT-CHAIN-U002-PROD', 1, 2, 'production', '生产下线', '2024-01-15 10:22:30', '苏州博世生产车间A1', 2, '生产主管', '博世汽车零部件（苏州）有限公司', NULL, 5, 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U002', 'BATCH-2024-001', 'cid://evt_attach_u002_p', 'hash_evt_u002_p', 1, 2, 'tx_evt_u002_p', 1, '完整链路样例'),
(5, 'EVT-CHAIN-U002-QUAL', 1, 2, 'quality', '出厂质检', '2024-01-16 09:16:20', '苏州质检中心', 4, '质检员', '博世质检部', 4, 6, 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U002', 'BATCH-2024-001', 'cid://evt_attach_u002_q', 'hash_evt_u002_q', 1, 2, 'tx_evt_u002_q', 1, '完整链路样例'),
(6, 'EVT-CHAIN-U002-LOGI', 1, 2, 'logistics', '发货运输', '2024-01-18 14:36:00', '苏州工业园区', 3, '物流经理', '顺丰物流', 5, NULL, 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U002', 'BATCH-2024-001', 'cid://evt_attach_u002_l', 'hash_evt_u002_l', 1, 2, 'tx_evt_u002_l', 1, '完整链路样例'),
(7, 'EVT-CHAIN-U003-PROD', 1, 3, 'production', '生产下线', '2024-01-15 10:25:30', '苏州博世生产车间A1', 2, '生产主管', '博世汽车零部件（苏州）有限公司', NULL, 8, 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U003', 'BATCH-2024-001', 'cid://evt_attach_u003_p', 'hash_evt_u003_p', 1, 2, 'tx_evt_u003_p', 1, '异常样例'),
(8, 'EVT-CHAIN-U003-QUAL', 1, 3, 'quality', '出厂质检', '2024-01-16 09:25:20', '苏州质检中心', 4, '质检员', '博世质检部', 7, NULL, 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U003', 'BATCH-2024-001', 'cid://evt_attach_u003_q', 'hash_evt_u003_q', 1, 3, 'tx_evt_u003_q', 1, '质检不合格，后续物流中止');

INSERT IGNORE INTO trace_event_type (type_code, type_name, category, status, remark) VALUES
('PRODUCTION_OFFLINE', '生产下线', 'production', 1, '生产环节标准事件'),
('RAW_MATERIAL_INBOUND', '原材料入库', 'production', 1, '生产环节标准事件'),
('QUALITY_INITIAL', '初检', 'quality', 1, '质检环节标准事件'),
('QUALITY_RECHECK', '复检', 'quality', 1, '质检环节标准事件'),
('LOGISTICS_DELIVERY', '发货', 'logistics', 1, '物流环节标准事件'),
('LOGISTICS_RECEIPT', '到货', 'logistics', 1, '物流环节标准事件');

INSERT IGNORE INTO trace_production (event_id, parts_id, production_line, production_batch, production_quantity, raw_materials, production_process, equipment_info, temperature, humidity, quality_level, inspection_result) VALUES
(1, 1, '燃油泵装配线A', 'BATCH-2024-001', 1.00, '铝合金铸件、电机组件', '精密装配→压力测试→密封性检测', '数控装配机、压力测试仪', 25.5, 45.0, 'A级', '合格'),
(4, 1, '燃油泵装配线A', 'BATCH-2024-001', 1.00, '铝合金铸件、电机组件', '精密装配→压力测试→密封性检测', '数控装配机、压力测试仪', 25.6, 45.2, 'A级', '合格'),
(7, 1, '燃油泵装配线A', 'BATCH-2024-001', 1.00, '铝合金铸件、电机组件', '精密装配→压力测试→密封性检测', '数控装配机、压力测试仪', 25.7, 45.1, 'A级', '合格');

INSERT IGNORE INTO trace_quality (event_id, parts_id, inspection_type, inspection_standard, inspection_items, inspection_result, qualified_quantity, inspector, inspector_id, report_number) VALUES
(2, 1, '出厂检验', 'ISO/TS 16949', '压力、流量、密封性、耐久性', '合格', 1.00, '质检员', 4, 'QC-CHAIN-U001'),
(5, 1, '出厂检验', 'ISO/TS 16949', '压力、流量、密封性、耐久性', '合格', 1.00, '质检员', 4, 'QC-CHAIN-U002'),
(8, 1, '出厂检验', 'ISO/TS 16949', '压力、流量、密封性、耐久性', '不合格', 0.00, '质检员', 4, 'QC-CHAIN-U003');

INSERT IGNORE INTO trace_logistics (event_id, parts_id, logistics_type, logistics_company, tracking_number, sender, sender_address, receiver, receiver_address, departure_time, arrival_time, transport_mode, vehicle_number, driver, driver_phone, temperature_record, package_status) VALUES
(3, 1, '干线运输', '顺丰物流', 'SF-U001-20240118', '生产部', '苏州工业园区博世工厂', '采购部', '上海浦东新区汽车总装厂', '2024-01-18 14:30:00', '2024-01-20 08:45:00', '公路运输', '苏A12345', '刘师傅', '13900139000', '22-25℃', '完好'),
(6, 1, '干线运输', '顺丰物流', 'SF-U002-20240118', '生产部', '苏州工业园区博世工厂', '采购部', '上海浦东新区汽车总装厂', '2024-01-18 14:36:00', '2024-01-20 08:50:00', '公路运输', '苏A12346', '刘师傅', '13900139000', '22-25℃', '完好');

INSERT IGNORE INTO verifiable_credential
(vc_id, parts_id, unit_id, event_id, vc_type, issuer_did, issuer_name, subject_did, issuance_date, expiration_date, credential_data, proof, status, vc_status, vc_hash, vc_cid, issuer_org, holder_did, blockchain_tx_id)
VALUES
('VC-CHAIN-U001-P', 1, 1, 1, 'production', 'did:parts:issuer:bosch-production', '博世生产部', 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U001', '2024-01-15 11:00:00', '2029-01-15 11:00:00', '{"batch":"BATCH-2024-001","unit":"U001","result":"生产完成"}', '{"type":"Ed25519Signature2020","created":"2024-01-15T11:00:00Z"}', 1, 1, 'hash_vc_u001_p', 'cid://vc_u001_p', '博世汽车零部件（苏州）有限公司', 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U001', 'tx_vc_u001_p'),
('VC-CHAIN-U001-Q', 1, 1, 2, 'quality', 'did:parts:issuer:bosch-quality', '博世质检部', 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U001', '2024-01-16 10:00:00', '2029-01-15 10:00:00', '{"batch":"BATCH-2024-001","unit":"U001","result":"质检合格","standard":"ISO/TS 16949"}', '{"type":"Ed25519Signature2020","created":"2024-01-16T10:00:00Z"}', 1, 1, 'hash_vc_u001_q', 'cid://vc_u001_q', '博世汽车零部件（苏州）有限公司', 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U001', 'tx_vc_u001_q'),
('VC-CHAIN-U001-L', 1, 1, 3, 'transfer', 'did:parts:issuer:sf-logistics', '顺丰物流', 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U001', '2024-01-18 15:00:00', '2025-01-18 15:00:00', '{"tracking":"SF-U001-20240118","route":"苏州->上海"}', '{"type":"Ed25519Signature2020","created":"2024-01-18T15:00:00Z"}', 1, 1, 'hash_vc_u001_l', 'cid://vc_u001_l', '顺丰物流', 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U001', 'tx_vc_u001_l'),
('VC-CHAIN-U002-P', 1, 2, 4, 'production', 'did:parts:issuer:bosch-production', '博世生产部', 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U002', '2024-01-15 11:10:00', '2029-01-15 11:10:00', '{"batch":"BATCH-2024-001","unit":"U002","result":"生产完成"}', '{"type":"Ed25519Signature2020","created":"2024-01-15T11:10:00Z"}', 1, 1, 'hash_vc_u002_p', 'cid://vc_u002_p', '博世汽车零部件（苏州）有限公司', 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U002', 'tx_vc_u002_p'),
('VC-CHAIN-U002-Q', 1, 2, 5, 'quality', 'did:parts:issuer:bosch-quality', '博世质检部', 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U002', '2024-01-16 10:12:00', '2029-01-15 10:12:00', '{"batch":"BATCH-2024-001","unit":"U002","result":"质检合格","standard":"ISO/TS 16949"}', '{"type":"Ed25519Signature2020","created":"2024-01-16T10:12:00Z"}', 1, 1, 'hash_vc_u002_q', 'cid://vc_u002_q', '博世汽车零部件（苏州）有限公司', 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U002', 'tx_vc_u002_q'),
('VC-CHAIN-U002-L', 1, 2, 6, 'transfer', 'did:parts:issuer:sf-logistics', '顺丰物流', 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U002', '2024-01-18 15:10:00', '2025-01-18 15:10:00', '{"tracking":"SF-U002-20240118","route":"苏州->上海"}', '{"type":"Ed25519Signature2020","created":"2024-01-18T15:10:00Z"}', 1, 1, 'hash_vc_u002_l', 'cid://vc_u002_l', '顺丰物流', 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U002', 'tx_vc_u002_l'),
('VC-CHAIN-U003-P', 1, 3, 7, 'production', 'did:parts:issuer:bosch-production', '博世生产部', 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U003', '2024-01-15 11:20:00', '2029-01-15 11:20:00', '{"batch":"BATCH-2024-001","unit":"U003","result":"生产完成"}', '{"type":"Ed25519Signature2020","created":"2024-01-15T11:20:00Z"}', 1, 1, 'hash_vc_u003_p', 'cid://vc_u003_p', '博世汽车零部件（苏州）有限公司', 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U003', 'tx_vc_u003_p'),
('VC-CHAIN-U003-Q', 1, 3, 8, 'quality', 'did:parts:issuer:bosch-quality', '博世质检部', 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U003', '2024-01-16 10:20:00', '2029-01-15 10:20:00', '{"batch":"BATCH-2024-001","unit":"U003","result":"质检不合格","standard":"ISO/TS 16949","reason":"密封性不达标"}', '{"type":"Ed25519Signature2020","created":"2024-01-16T10:20:00Z"}', 1, 3, 'hash_vc_u003_q', 'cid://vc_u003_q', '博世汽车零部件（苏州）有限公司', 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U003', 'tx_vc_u003_q');

INSERT IGNORE INTO vc_verify_record (vc_id, verify_result, fail_reason, duration_ms, source) VALUES
('VC-CHAIN-U001-Q', 1, NULL, 42, 'workbench'),
('VC-CHAIN-U001-L', 1, NULL, 46, 'workbench'),
('VC-CHAIN-U002-Q', 1, NULL, 41, 'workbench'),
('VC-CHAIN-U003-Q', 0, '质检结果不合格，VC状态无效', 39, 'workbench');

INSERT IGNORE INTO vc_template (template_code, template_name, event_type, standard_fields, custom_fields, valid_days, status) VALUES
('TPL_PRODUCTION', '生产证明VC模板', 'production', 'vcId,issuerDid,holderDid,issuanceDate,expirationDate,claims', 'productionLine,productionBatch,qualityLevel', 365, 1),
('TPL_QUALITY', '质检报告VC模板', 'quality', 'vcId,issuerDid,holderDid,issuanceDate,expirationDate,claims', 'inspectionType,inspectionResult,inspectionStandard', 365, 1),
('TPL_LOGISTICS', '流转证明VC模板', 'logistics', 'vcId,issuerDid,holderDid,issuanceDate,expirationDate,claims', 'logisticsCompany,trackingNumber,route', 365, 1);

INSERT IGNORE INTO anomaly_alert (alert_type, alert_level, title, parts_id, parts_code, rule_id, rule_name, description, alert_time, status) VALUES
('quality', 'WARNING', 'BATCH-2024-001-U003 质检不合格', 1, 'PT-ENG-001', 1, '质检不合格告警', '单件 U003 质检不合格，DID 已冻结，物流流程中止', '2024-01-16 10:25:00', 0),
('temperature', 'CRITICAL', 'U002 运输温度波动告警', 1, 'PT-ENG-001', 3, '温度超标告警', 'U002 运输中温度瞬时达到41℃，系统已记录并复核通过', '2024-01-19 03:10:00', 1),
('time', 'WARNING', 'BATCH-2024-002 待物流', 2, 'PT-ENG-002', 2, '物流超时告警', '批次 BATCH-2024-002 尚未进入物流环节（用于待处理样例）', '2024-01-23 16:30:00', 0);

INSERT IGNORE INTO object_storage_record (cid, business_type, business_id, content_hash, content) VALUES
('cid://did_doc_u001', 'did_document_unit', 1, 'hash-did-u001', '{"did":"...U001","docType":"did"}'),
('cid://did_doc_u002', 'did_document_unit', 2, 'hash-did-u002', '{"did":"...U002","docType":"did"}'),
('cid://did_doc_u003', 'did_document_unit', 3, 'hash-did-u003', '{"did":"...U003","docType":"did"}'),
('cid://evt_attach_u001_p', 'trace_event', 1, 'hash_evt_u001_p', '{"evidence":"production-proof-u001"}'),
('cid://evt_attach_u001_q', 'trace_event', 2, 'hash_evt_u001_q', '{"evidence":"quality-report-u001"}'),
('cid://evt_attach_u001_l', 'trace_event', 3, 'hash_evt_u001_l', '{"evidence":"logistics-waybill-u001"}'),
('cid://evt_attach_u002_p', 'trace_event', 4, 'hash_evt_u002_p', '{"evidence":"production-proof-u002"}'),
('cid://evt_attach_u002_q', 'trace_event', 5, 'hash_evt_u002_q', '{"evidence":"quality-report-u002"}'),
('cid://evt_attach_u002_l', 'trace_event', 6, 'hash_evt_u002_l', '{"evidence":"logistics-waybill-u002"}'),
('cid://vc_u001_q', 'verifiable_credential', 2, 'hash_vc_u001_q', '{"vcId":"VC-CHAIN-U001-Q"}'),
('cid://vc_u001_l', 'verifiable_credential', 3, 'hash_vc_u001_l', '{"vcId":"VC-CHAIN-U001-L"}'),
('cid://vc_u002_q', 'verifiable_credential', 5, 'hash_vc_u002_q', '{"vcId":"VC-CHAIN-U002-Q"}'),
('cid://vc_u002_l', 'verifiable_credential', 6, 'hash_vc_u002_l', '{"vcId":"VC-CHAIN-U002-L"}'),
('cid://vc_u003_q', 'verifiable_credential', 8, 'hash_vc_u003_q', '{"vcId":"VC-CHAIN-U003-Q"}');

INSERT IGNORE INTO blockchain_record (business_id, business_type, tx_id, block_height, block_hash, data_hash, chain_id, status) VALUES
(1, 'did_document_unit', 'tx_did_u001', 21001, '0xblockhash21001', 'hash-did-u001', 'parts-chain-1', 1),
(2, 'did_document_unit', 'tx_did_u002', 21002, '0xblockhash21002', 'hash-did-u002', 'parts-chain-1', 1),
(3, 'did_document_unit', 'tx_did_u003', 21003, '0xblockhash21003', 'hash-did-u003', 'parts-chain-1', 1),
(1, 'trace_event', 'tx_evt_u001_p', 22001, '0xblockhash22001', 'hash_evt_u001_p', 'parts-chain-1', 1),
(2, 'trace_event', 'tx_evt_u001_q', 22002, '0xblockhash22002', 'hash_evt_u001_q', 'parts-chain-1', 1),
(3, 'trace_event', 'tx_evt_u001_l', 22003, '0xblockhash22003', 'hash_evt_u001_l', 'parts-chain-1', 1),
(5, 'verifiable_credential', 'tx_vc_u002_q', 23005, '0xblockhash23005', 'hash_vc_u002_q', 'parts-chain-1', 1),
(8, 'verifiable_credential', 'tx_vc_u003_q', 23008, '0xblockhash23008', 'hash_vc_u003_q', 'parts-chain-1', 1);

-- ============ 跨链拓扑（数据库模拟：1 主监管链 + 3 业务子链） ============
DROP TABLE IF EXISTS chain_info;
CREATE TABLE chain_info (
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
('sub-logistics', '物流子链', 'FISCO_BCOS', 'BUSINESS', '物流环节溯源数据存证（异构链）', 1);

DROP TABLE IF EXISTS cross_chain_record;
CREATE TABLE cross_chain_record (
    business_id BIGINT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1024) NULL,
    next_retry_at DATETIME NULL,

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
    UNIQUE KEY uk_cross_source_tx(source_chain_id,source_tx_hash),
    PRIMARY KEY (id),
    KEY idx_parts_did (parts_did),
    KEY idx_source_chain (source_chain_id),
    KEY idx_target_chain (target_chain_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 新增安全与可靠提交表（本文件整体仅供新建或明确重建数据库使用）。
DROP TABLE IF EXISTS chain_task;
DROP TABLE IF EXISTS operation_audit;
DROP TABLE IF EXISTS revoked_token;

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

UPDATE anomaly_rule SET status=0 WHERE JSON_VALID(`condition`)=0 OR JSON_VALID(action)=0;

-- 多机构核验凭据；已有库单独执行V20261026__relay_confirmation.sql。
DROP TABLE IF EXISTS relay_confirmation;
CREATE TABLE relay_confirmation (
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
