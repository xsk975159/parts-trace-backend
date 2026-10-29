package com.kewei.parts.infrastructure.desktop;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.sql.*;
import java.util.Properties;
import java.util.UUID;

/** 独立运行包的数据库入口；仅操作经过数据目录和实例标识双重核对的私有实例。 */
public final class StandaloneDatabase {
    private StandaloneDatabase() { }

    public static void main(String[] args) {
        try {
            String action = args.length == 1 ? args[0] : "";
            if (!action.equals("prepare") && !action.equals("probe") && !action.equals("shutdown"))
                throw new IllegalArgumentException("未知操作");
            int port = Integer.parseInt(required("PARTS_TRACE_PRIVATE_DB_PORT"));
            if (port < 1024 || port > 65535) throw new IllegalArgumentException("端口超出范围");
            String instance = UUID.fromString(required("PARTS_TRACE_PRIVATE_DB_ID")).toString();
            Properties properties = new Properties();
            boolean shutdown = action.equals("shutdown");
            properties.setProperty("user", shutdown ? "parts_trace_shutdown" : "parts_trace_app");
            properties.setProperty("password", required(shutdown
                    ? "PARTS_TRACE_PRIVATE_ROOT_PASSWORD" : "KEWEI_DBPWD"));
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = "jdbc:mysql://127.0.0.1:" + port
                    + "/?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai"
                    + "&allowPublicKeyRetrieval=true&connectTimeout=1500&socketTimeout=60000";
            try (Connection connection = DriverManager.getConnection(url, properties);
                 Statement statement = connection.createStatement()) {
                try (ResultSet result = statement.executeQuery("SELECT @@datadir")) {
                    if (!result.next()) throw new IllegalStateException("无法核对数据库目录");
                    // Windows版MySQL以本机代码页保存datadir，返回值标记为binary，不能按JDBC的UTF-8解码。
                    String actualDirectory = new String(result.getBytes(1),
                            Charset.forName(required("PARTS_TRACE_PRIVATE_FILESYSTEM_CHARSET")));
                    requireSameDirectory(required("PARTS_TRACE_PRIVATE_DB_DIRECTORY"), actualDirectory);
                }
                if (shutdown) {
                    statement.execute("SHUTDOWN");
                    return;
                }
                if (action.equals("probe")) {
                    System.out.println("PRIVATE_DATABASE_READY");
                    return;
                }
                long tables;
                try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables"
                        + " WHERE table_schema='parts_trace'")) {
                    result.next(); tables = result.getLong(1);
                }
                if (tables == 0) {
                    ScriptUtils.executeSqlScript(connection,
                            new EncodedResource(new ClassPathResource("db/init.sql"), StandardCharsets.UTF_8));
                    statement.execute("USE parts_trace");
                    // 初始化脚本只允许对空库执行；中途中断后已有表，下一次启动拒绝覆盖。
                    statement.execute("CREATE TABLE desktop_installation (id TINYINT PRIMARY KEY,"
                            + " instance_id VARCHAR(36) NOT NULL, schema_version INT NOT NULL)");
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO desktop_installation VALUES (1, ?, 1)")) {
                        insert.setString(1, instance); insert.executeUpdate();
                    }
                    System.out.println("PRIVATE_DATABASE_INITIALIZED");
                } else {
                    statement.execute("USE parts_trace");
                    try (ResultSet result = statement.executeQuery(
                            "SELECT instance_id, schema_version FROM desktop_installation WHERE id=1")) {
                        if (!result.next() || !instance.equals(result.getString(1)) || result.getInt(2) != 1)
                            throw new IllegalStateException("私有数据库标识或版本不一致，已停止启动");
                    }
                    System.out.println("PRIVATE_DATABASE_REUSED");
                }
            }
        } catch (Exception error) {
            // 不输出SQL、连接串或凭据，避免初始化失败时泄露随机数据库密码。
            System.err.println("私有数据库操作失败（" + error.getClass().getSimpleName()
                    + "）。请保留data目录，不要对已有数据重新导入init.sql。");
            System.exit(2);
        }
    }

    static void requireSameDirectory(String expected, String actual) throws Exception {
        if (!new File(expected).getCanonicalFile().equals(new File(actual).getCanonicalFile()))
            throw new IllegalStateException("拒绝操作其他数据库实例");
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("缺少运行参数");
        return value;
    }
}
