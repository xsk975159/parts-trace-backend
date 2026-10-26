-- =====================================================================
-- 跨链存证演示数据（单独 seed，供前端「追溯查询」页直接展示中继记录）
-- 说明：init.sql 只灌了 chain_info 拓扑，没有灌 cross_chain_record，
--       因为中继记录本应在业务操作（登记事件/签发凭证/生成DID）时由存储适配器落库。
--       本脚本按 init.sql 里已有的 seed 数据，反向补一批「业务子链 → 主监管链」的中继摘要。
-- 执行：mysql -uroot -p < cross_chain_demo.sql   （或 mysql -uroot -pzxg113913 parts_trace < cross_chain_demo.sql）
-- 覆盖：3 份 DID 文档 + 8 个溯源事件 + 8 份数字凭证，共 19 条中继记录。
-- =====================================================================

USE parts_trace;
SET NAMES utf8mb4;

-- 幂等：先清空，避免重复执行造成重复记录
DELETE FROM cross_chain_record;

INSERT INTO cross_chain_record
    (source_chain_id, source_tx_hash, source_block_height, business_type, parts_did, data_hash, object_cid,
     target_chain_id, target_tx_hash, target_block_height, status, relay_time)
VALUES
-- ── 单件 DID 文档（生产子链，didChain → sub-production） ──
('sub-production', 'tx_did_u001',    21001, 'did_document_unit', 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U001', 'hash-did-u001',    'cid://did_doc_u001',    'main-regulator', 'tx-main-0001', 1,  1, '2024-01-15 10:05:00'),
('sub-production', 'tx_did_u002',    21002, 'did_document_unit', 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U002', 'hash-did-u002',    'cid://did_doc_u002',    'main-regulator', 'tx-main-0002', 2,  1, '2024-01-15 10:06:00'),
('sub-production', 'tx_did_u003',    21003, 'did_document_unit', 'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U003', 'hash-did-u003',    'cid://did_doc_u003',    'main-regulator', 'tx-main-0003', 3,  1, '2024-01-15 10:07:00'),

-- ── 生产事件（生产子链，production → sub-production） ──
('sub-production', 'tx_evt_u001_p',  21004, 'trace_event',      'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U001', 'hash_evt_u001_p',  'cid://evt_attach_u001_p', 'main-regulator', 'tx-main-0004', 4,  1, '2024-01-15 10:20:30'),
('sub-production', 'tx_evt_u002_p',  21005, 'trace_event',      'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U002', 'hash_evt_u002_p',  'cid://evt_attach_u002_p', 'main-regulator', 'tx-main-0005', 5,  1, '2024-01-15 10:22:30'),
('sub-production', 'tx_evt_u003_p',  21006, 'trace_event',      'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U003', 'hash_evt_u003_p',  'cid://evt_attach_u003_p', 'main-regulator', 'tx-main-0006', 6,  1, '2024-01-15 10:25:30'),

-- ── 生产凭证（生产子链，production → sub-production） ──
('sub-production', 'tx_vc_u001_p',   21007, 'vc',               'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U001', 'hash_vc_u001_p',   'cid://vc_u001_p',       'main-regulator', 'tx-main-0007', 7,  1, '2024-01-15 11:00:00'),
('sub-production', 'tx_vc_u002_p',   21008, 'vc',               'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U002', 'hash_vc_u002_p',   'cid://vc_u002_p',       'main-regulator', 'tx-main-0008', 8,  1, '2024-01-15 11:10:00'),
('sub-production', 'tx_vc_u003_p',   21009, 'vc',               'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U003', 'hash_vc_u003_p',   'cid://vc_u003_p',       'main-regulator', 'tx-main-0009', 9,  1, '2024-01-15 11:20:00'),

-- ── 质检事件（质检子链，quality → sub-quality） ──
('sub-quality',    'tx_evt_u001_q',  31001, 'trace_event',      'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U001', 'hash_evt_u001_q',  'cid://evt_attach_u001_q', 'main-regulator', 'tx-main-0010', 10, 1, '2024-01-16 09:10:20'),
('sub-quality',    'tx_evt_u002_q',  31002, 'trace_event',      'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U002', 'hash_evt_u002_q',  'cid://evt_attach_u002_q', 'main-regulator', 'tx-main-0011', 11, 1, '2024-01-16 09:16:20'),
('sub-quality',    'tx_evt_u003_q',  31003, 'trace_event',      'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U003', 'hash_evt_u003_q',  'cid://evt_attach_u003_q', 'main-regulator', 'tx-main-0012', 12, 1, '2024-01-16 09:25:20'),

-- ── 质检凭证（质检子链，quality → sub-quality） ──
('sub-quality',    'tx_vc_u001_q',   31004, 'vc',               'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U001', 'hash_vc_u001_q',   'cid://vc_u001_q',       'main-regulator', 'tx-main-0013', 13, 1, '2024-01-16 10:00:00'),
('sub-quality',    'tx_vc_u002_q',   31005, 'vc',               'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U002', 'hash_vc_u002_q',   'cid://vc_u002_q',       'main-regulator', 'tx-main-0014', 14, 1, '2024-01-16 10:12:00'),
('sub-quality',    'tx_vc_u003_q',   31006, 'vc',               'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U003', 'hash_vc_u003_q',   'cid://vc_u003_q',       'main-regulator', 'tx-main-0015', 15, 1, '2024-01-16 10:20:00'),

-- ── 物流事件（物流子链 FISCO BCOS，logistics → sub-logistics） ──
('sub-logistics',  'tx_evt_u001_l',  41001, 'trace_event',      'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U001', 'hash_evt_u001_l',  'cid://evt_attach_u001_l', 'main-regulator', 'tx-main-0016', 16, 1, '2024-01-18 14:30:00'),
('sub-logistics',  'tx_evt_u002_l',  41002, 'trace_event',      'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U002', 'hash_evt_u002_l',  'cid://evt_attach_u002_l', 'main-regulator', 'tx-main-0017', 17, 1, '2024-01-18 14:36:00'),

-- ── 流转凭证（物流子链，transfer → sub-logistics） ──
('sub-logistics',  'tx_vc_u001_l',   41003, 'vc',               'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U001', 'hash_vc_u001_l',   'cid://vc_u001_l',       'main-regulator', 'tx-main-0018', 18, 1, '2024-01-18 15:00:00'),
('sub-logistics',  'tx_vc_u002_l',   41004, 'vc',               'did:industrial:partchain:110331f8b53d5d41:BATCH-2024-001:BATCH-2024-001-U002', 'hash_vc_u002_l',   'cid://vc_u002_l',       'main-regulator', 'tx-main-0019', 19, 1, '2024-01-18 15:10:00');
