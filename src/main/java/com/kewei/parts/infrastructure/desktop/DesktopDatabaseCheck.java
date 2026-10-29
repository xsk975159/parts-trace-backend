package com.kewei.parts.infrastructure.desktop;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

/** 启动前只读核对数据库。使用JAR中的JDBC驱动，不要求安装mysql命令行客户端。 */
public final class DesktopDatabaseCheck {
    private DesktopDatabaseCheck() { }

    public static void main(String[] args) {
        try {
            String host = environment("KEWEI_DBSERVER", "127.0.0.1");
            int port = Integer.parseInt(environment("KEWEI_DBPORT", "3306"));
            if (!host.matches("[A-Za-z0-9._:\\[\\]-]+") || port < 1 || port > 65535)
                throw new IllegalArgumentException("数据库地址或端口格式不正确");
            Properties properties = new Properties();
            properties.setProperty("user", environment("KEWEI_DBUSER", "root"));
            properties.setProperty("password", environment("KEWEI_DBPWD", ""));
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = "jdbc:mysql://" + host + ":" + port
                    + "/parts_trace?useUnicode=true&characterEncoding=utf8&useSSL=false"
                    + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=10000";
            try (Connection connection = DriverManager.getConnection(url, properties)) {
                connection.setReadOnly(true);
                try (Statement statement = connection.createStatement()) {
                    try (ResultSet result = statement.executeQuery(
                            "SELECT COUNT(*) FROM sys_user_rsa_key WHERE private_key LIKE 'enc:v1:%'")) {
                        if (!result.next()) throw new IllegalStateException("无法读取密钥状态");
                        System.out.println("ENCRYPTED_KEYS=" + result.getLong(1));
                    }
                    try (ResultSet result = statement.executeQuery(schemaQuery())) {
                        while (result.next()) System.out.println("MISSING=" + result.getString(1));
                    }
                    if (Boolean.parseBoolean(environment("RELAY_CONFIRMATION_ENABLED", "false"))) {
                        try (ResultSet result = statement.executeQuery(
                                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='relay_confirmation'")) {
                            if (!result.next() || result.getInt(1) != 1)
                                System.out.println("MISSING=table:relay_confirmation");
                        }
                    }
                }
            }
        } catch (Exception error) {
            // JDBC错误不输出连接凭据或完整URL。
            System.err.println("数据库检查失败，请核对连接配置、parts_trace库及数据库升级状态。错误类型："
                    + error.getClass().getSimpleName());
            System.exit(2);
        }
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }

    private static String schemaQuery() throws Exception {
        try (InputStream input = DesktopDatabaseCheck.class.getResourceAsStream("/desktop/check-schema.sql")) {
            if (input == null) throw new IllegalStateException("运行包缺少数据库结构检查脚本");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8).replace("\uFEFF", "");
        }
    }
}
