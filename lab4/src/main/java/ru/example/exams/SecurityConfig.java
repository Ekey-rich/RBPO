package ru.example.exams;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder encoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService users(JdbcTemplate db) {
        return login -> db.query("SELECT * FROM users WHERE username=?", rs -> {
            if (!rs.next()) throw new UsernameNotFoundException("Пользователь не найден");
            return User.withUsername(rs.getString("username"))
                    .password(rs.getString("password"))
                    .roles(rs.getString("role"))
                    .disabled(!rs.getBoolean("enabled"))
                    .build();
        }, login);
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/register", "/csrf").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build(); // CSRF включён по умолчанию
    }

    @Bean
    CommandLineRunner makeAdmin(JdbcTemplate db, PasswordEncoder encoder) {
        return args -> {
            String password = System.getenv("ADMIN_PASSWORD");
            if (password == null || password.isBlank()) return;
            if (db.queryForObject("SELECT COUNT(*) FROM users WHERE username='admin'", Integer.class) == 0)
                db.update("INSERT INTO users(name, username, password, role, enabled) VALUES ('Администратор','admin',?,'ADMIN',TRUE)", encoder.encode(password));
        };
    }
}
