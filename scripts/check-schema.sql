-- 只读检查当前项目库，返回缺少的表、字段和唯一索引；无缺项时不返回记录。
SELECT CONCAT('table:', required.table_name) AS missing_item
FROM (
    SELECT 'chain_info' AS table_name UNION ALL
    SELECT 'cross_chain_record' UNION ALL
    SELECT 'revoked_token' UNION ALL
    SELECT 'operation_audit' UNION ALL
    SELECT 'chain_task'
) required
WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.tables actual
    WHERE actual.table_schema = DATABASE() AND actual.table_name = required.table_name
)
UNION ALL
SELECT CONCAT('column:', required.table_name, '.', required.column_name)
FROM (
    SELECT 'sys_user' AS table_name, 'organization' AS column_name UNION ALL
    SELECT 'sys_user_rsa_key', 'default_key' UNION ALL
    SELECT 'trace_event', 'request_id' UNION ALL
    SELECT 'trace_event', 'request_hash' UNION ALL
    SELECT 'trace_event', 'audit_comment' UNION ALL
    SELECT 'anomaly_alert', 'event_id' UNION ALL
    SELECT 'anomaly_alert', 'unit_id' UNION ALL
    SELECT 'cross_chain_record', 'business_id' UNION ALL
    SELECT 'cross_chain_record', 'attempts' UNION ALL
    SELECT 'cross_chain_record', 'last_error' UNION ALL
    SELECT 'cross_chain_record', 'next_retry_at'
) required
WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.columns actual
    WHERE actual.table_schema = DATABASE() AND actual.table_name = required.table_name
        AND actual.column_name = required.column_name
)
UNION ALL
SELECT CONCAT('unique-index:', required.table_name, '.', required.index_name)
FROM (
    SELECT 'trace_event' AS table_name, 'uk_trace_request' AS index_name UNION ALL
    SELECT 'verifiable_credential', 'uk_vc_identifier' UNION ALL
    SELECT 'verifiable_credential', 'uk_vc_event_type' UNION ALL
    SELECT 'cross_chain_record', 'uk_cross_source_tx' UNION ALL
    SELECT 'anomaly_alert', 'uk_alert_event_rule'
) required
WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.statistics actual
    WHERE actual.table_schema = DATABASE() AND actual.table_name = required.table_name
        AND actual.index_name = required.index_name AND actual.non_unique = 0
);
