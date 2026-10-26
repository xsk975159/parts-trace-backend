import java.sql.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 只允许操作本机13316端口和指定的独立测试数据目录。 */
public class PrepareIntegrationFixtures {
    public static void main(String[] args) throws Exception {
        String password=System.getenv("CCF_TEST_PASSWORD");
        if(!"1".equals(System.getenv("CCF_RUN_ISOLATED_INTEGRATION")) || password==null || password.length()<12)
            throw new IllegalStateException("请显式启用隔离集成测试，并设置至少12字符的测试密码");
        Class.forName("com.mysql.cj.jdbc.Driver");
        try(Connection db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:13316/parts_trace?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai","ccf_test","");
            Statement statement=db.createStatement();ResultSet info=statement.executeQuery("SELECT @@port,@@datadir")) {
            info.next();
            if(info.getInt(1)!=13316 || !info.getString(2).replace('\\','/').contains("/tmp/integration/mysql-isolated/"))
                throw new IllegalStateException("不是指定的独立测试数据库，拒绝修改");
            try(PreparedStatement update=db.prepareStatement("UPDATE sys_user SET password=?,status=1 WHERE id IN (1,2,3,4,5)")) {
                update.setString(1,new BCryptPasswordEncoder().encode(password));update.executeUpdate();
            }
        }
        System.out.println("隔离测试账户已就绪");
    }
}
