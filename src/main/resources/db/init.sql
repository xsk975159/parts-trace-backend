CREATE DATABASE IF NOT EXISTS parts_trace DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE parts_trace;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
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
("ENGINE",        "发动机部件",    0, 1, 1, 1),
("CHASSIS",       "底盘部件",   0, 1, 2, 1),
("ELECTRIC",      "电器部件",  0, 1, 3, 1),
("BODY",          "车身部件",      0, 1, 4, 1),
("ENGINE_CORE",   "发动机核心",     1, 2, 1, 1),
("ENGINE_FUEL",   "燃油系统",     1, 2, 2, 1),
("CHASSIS_BRAKE", "制动系统",    2, 2, 1, 1),
("CHASSIS_SUSP",  "悬挂系统",      2, 2, 2, 1),
("ENGINE_COOL",     "冷却系统",       1, 2, 3, 1),
("ENGINE_EXHAUST",  "排气系统",       1, 2, 4, 1),
("CHASSIS_STEER",   "转向系统",       2, 2, 3, 1),
("CHASSIS_TRANS",   "传动系统",       2, 2, 4, 1),
("ELECTRIC_SENSOR", "传感器",         3, 2, 1, 1),
("ELECTRIC_ECU",    "电控单元",       3, 2, 2, 1),
("BODY_DOOR",       "车门部件",       4, 2, 1, 1),
("BODY_GLASS",      "玻璃部件",       4, 2, 2, 1),
("ENGINE_CORE_PISTON","活塞组件",     5, 3, 1, 1),
("ENGINE_CORE_CRANK", "曲轴组件",     5, 3, 2, 1),
("ENGINE_FUEL_INJ",   "燃油喷射器",   6, 3, 1, 1),
("CHASSIS_BRAKE_DISC","制动盘",       7, 3, 1, 1),
("CHASSIS_BRAKE_PAD", "制动片",       7, 3, 2, 1),
("CHASSIS_SUSP_SPRING","弹簧减震器",  8, 3, 1, 1);

INSERT IGNORE INTO parts_info
(parts_code, parts_name, batch_number, category_id, specification, unit, quantity, weight, material, manufacturer, manufacturer_id, production_date, expiry_date, quality_standard, certification, description, status, creator_id)
VALUES
('PT-ENG-001', '高压共轨燃油泵',      'BATCH-2024-001', 6,  '压力：180MPa，流量：350ml/min',         '个', 3.00,    3.2500, '铸铁+铝合金',   '博世汽车零部件（苏州）有限公司',     2, '2024-01-15', '2029-01-15', 'ISO/TS 16949', 'CE,CCC',     '适用于柴油发动机高压共轨系统',           1, 2),
('PT-ENG-002', '发动机活塞',          'BATCH-2024-002', 17, '直径：86mm，行程：86mm，压缩比：10.5:1','个', 3.00,    0.4200, '铝合金',        '山东滨州渤海活塞有限公司',           2, '2024-01-20', '2029-01-20', 'GB/T 1148',   'CCC',        '适用于1.8T汽油发动机',                   1, 2),
('PT-ENG-003', '曲轴总成',            'BATCH-2024-003', 18, '材质：42CrMo，重量：18kg',              '根', 3.00,  18.000, '42CrMo合金钢',  '湖北新冶钢有限公司',                 2, '2024-02-01', '2034-02-01', 'JB/T 5075',   'CCC',        '四缸发动机用曲轴，热处理调质',           1, 2),
('PT-ENG-004', '机油泵总成',          'BATCH-2024-004', 5,  '排量：11ml/r，压力：0.4-0.6MPa',        '套', 8.00,  1.8000, '铸铁',          '温州奔松科技有限公司',               2, '2024-02-10', '2029-02-10', 'QC/T 474',    'CCC',        '摆线式机油泵，适配2.0L发动机',           1, 2),
('PT-ENG-005', '涡轮增压器',          'BATCH-2024-005', 5,  '增压比：2.5，最大转速：220000rpm',       '个', 4.00,  4.5000, '镍基合金',      '霍尼韦尔（中国）有限公司',           2, '2024-02-15', '2030-02-15', 'ISO 9001',    'CE,CCC',     '适用于1.5T/2.0T发动机',                  1, 2),
('PT-CHN-001', '前制动盘',            'BATCH-2024-006', 20, '直径：300mm，厚度：28mm',               '片', 3.00,    5.6000, '灰铸铁HT250',   '浙江万安科技股份有限公司',           2, '2024-03-01', '2027-03-01', 'GB 10597',    'CCC,IATF',   '通风盘式制动盘，适配B级轿车',            1, 2),
('PT-CHN-002', '陶瓷制动片套装',      'BATCH-2024-007', 21, '尺寸：156×63×17mm，摩擦系数：0.38-0.45','套', 3.00, 0.8500, '陶瓷纤维复合',  '北京华夏汽车配件有限公司',           2, '2024-03-05', '2027-03-05', 'GB 5763',     'CCC',        '低粉尘低噪音陶瓷配方制动片',             1, 2),
('PT-CHN-003', '麦弗逊减震器总成',    'BATCH-2024-008', 22, '行程：200mm，阻尼可调：3档',             '支', 6.00,  3.2000, '高强度钢',      '天津天弹弹簧制造有限公司',           2, '2024-03-10', '2028-03-10', 'QC/T 545',    'CCC',        '前悬架减震器，适配A/B级轿车',            1, 2),
('PT-CHN-004', '转向拉杆球头',        'BATCH-2024-009', 11, '球销直径：22mm，承载：8kN',              '个', 2.00, 0.3500, '40Cr钢',        '瑞安市华业汽车配件有限公司',         2, '2024-03-15', '2027-03-15', 'QC/T 292',    'CCC',        '转向横拉杆球头销',                       1, 2),
('PT-CHN-005', '等速万向节总成',      'BATCH-2024-010', 12, '节径：100mm，最大偏角：47°',             '套', 4.00,  3.8000, '轴承钢GCr15',   '上海纳铁福传动系统有限公司',         2, '2024-03-20', '2029-03-20', 'ISO/TS 16949','CCC,IATF',   '外球笼式等速万向节，含防尘套',           1, 2),
('PT-ELE-001', '车身控制模块BCM',     'BATCH-2024-011', 14, 'CAN总线，工作电压：9-16V',               '个', 5.00,  0.3200, 'PCB+塑料外壳',  '德尔福科技（上海）有限公司',         2, '2024-04-01', '2031-04-01', 'AEC-Q100',    'CE,E-Mark',  '控制车窗/门锁/雨刮/照明等车身功能',      1, 2),
('PT-ELE-002', '前氧传感器',          'BATCH-2024-012', 13, '加热型，响应时间<3s，工作温度：750℃',    '个', 15.00, 0.1500, '氧化锆陶瓷',    '广州博世汽车零部件有限公司',         2, '2024-04-05', '2028-04-05', 'ISO 9001',    'E-Mark,CCC', '监测发动机废气氧含量，精准控制空燃比',   1, 2),
('PT-ELE-003', '水温传感器',          'BATCH-2024-013', 13, '测量范围：-40~135℃，精度±1℃',           '个', 2.00, 0.1200, '热敏电阻',      '上海德科电子有限公司',               2, '2024-04-10', '2028-04-10', 'GB/T 18856',  'CCC',        '实时监测发动机冷却液温度',               1, 2),
('PT-BDY-001', '左前车门总成',        'BATCH-2024-014', 15, '尺寸：1200×800mm，材质：镀锌钢板',       '个', 3.00,  22.500, '镀锌钢板',      '一汽富华汽车部件有限公司',         2, '2024-04-15', '2034-04-15', 'QC/T 625',    'CCC',        '含车门内板、外板、玻璃升降器',           1, 2),
('PT-BDY-002', '前挡风玻璃',          'BATCH-2024-015', 16, '尺寸：1450×850mm，夹层钢化玻璃',         '块', 5.00,  8.3000, '钢化玻璃',      '福耀玻璃工业集团股份有限公司',       2, '2024-04-20', '2034-04-20', 'GB 9656',     '3C认证',     '汽车专用夹层安全玻璃，防紫外线',         1, 2),
('PT-ENG-006', '三元催化器',          'BATCH-2024-016', 10, '载体：陶瓷蜂窝，转化效率≥95%',           '个', 6.00,  4.2000, '陶瓷+贵金属',   '庄信万丰（上海）催化剂有限公司',     2, '2024-05-01', '2029-05-01', 'GB 18352',    'CCC',        '降低发动机废气污染物排放',               1, 2),
('PT-ENG-007', '节温器总成',          'BATCH-2024-017', 9,  '开启温度：87℃，材质：黄铜',              '个', 12.00, 0.2800, '黄铜',          '浙江银轮机械股份有限公司',           2, '2024-05-05', '2029-05-05', 'QC/T 295',    'CCC',        '控制发动机冷却液循环',                   1, 2),
('PT-CHN-006', '稳定杆连杆',          'BATCH-2024-018', 8,  '长度：280mm，球头强度：10kN',             '个', 18.00, 0.4500, '45#钢',         '江苏彤明高科汽车电器有限公司',       2, '2024-05-10', '2027-05-10', 'GB/T 3098',   'CCC',        '提升车辆行驶稳定性',                     1, 2),
('PT-ELE-004', '倒车雷达探头',        'BATCH-2024-019', 13, '探测距离：0.1-2.5m，超声波频率：40kHz',  '个', 3.00, 0.0800, 'ABS塑料',      '深圳市道通科技股份有限公司',         2, '2024-05-15', '2028-05-15', 'ISO 9001',    'CE',         '泊车辅助系统超声波探头',                 1, 2),
('PT-BDY-003', '后视镜总成',          'BATCH-2024-020', 15, '电动调节+加热，适配A级轿车',             '个', 1.00, 0.7500, 'ABS+玻璃',     '湖北三环汽车电器有限公司',         2, '2024-05-20', '2029-05-20', 'QC/T 625',    'CCC',        '左右通用型电动后视镜',                   1, 2);

INSERT IGNORE INTO parts_unit (parts_id, batch_number, unit_code, unit_status) VALUES
(1, 'BATCH-2024-001', 'BATCH-2024-001-U001', 0),
(1, 'BATCH-2024-001', 'BATCH-2024-001-U002', 0),
(1, 'BATCH-2024-001', 'BATCH-2024-001-U003', 1),
(2, 'BATCH-2024-002', 'BATCH-2024-002-U001', 0),
(2, 'BATCH-2024-002', 'BATCH-2024-002-U002', 0),
(2, 'BATCH-2024-002', 'BATCH-2024-002-U003', 0),
(6, 'BATCH-2024-006', 'BATCH-2024-006-U001', 0),
(6, 'BATCH-2024-006', 'BATCH-2024-006-U002', 0),
(6, 'BATCH-2024-006', 'BATCH-2024-006-U003', 0);

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