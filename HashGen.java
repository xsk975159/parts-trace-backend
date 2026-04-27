import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class HashGen {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        System.out.println("admin123=" + encoder.encode("admin123"));
        System.out.println("123456=" + encoder.encode("123456"));
    }
}
