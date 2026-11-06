package ru.example.exams;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

@SpringBootApplication
public class ExamApplication {

    public static void main(String[] args) throws Exception {
        // Пароли лежат в .env, а не в Java-коде.
        Properties env = new Properties();
        try (FileInputStream file = new FileInputStream(".env")) {
            env.load(file);
        }
        String adminPassword = System.getenv().getOrDefault("DB_ADMIN_PASSWORD", env.getProperty("DB_ADMIN_PASSWORD"));
        String appPassword = System.getenv().getOrDefault("DB_PASSWORD", env.getProperty("DB_PASSWORD"));
        if (adminPassword == null || adminPassword.isBlank() || appPassword == null || appPassword.isBlank()) {
            throw new IllegalArgumentException("Укажите DB_ADMIN_PASSWORD и DB_PASSWORD в .env");
        }
        System.setProperty("DB_PASSWORD", appPassword);

        Files.createDirectories(Path.of("data"));
        String url = "jdbc:h2:file:./data/exams";

        // Администратор только создаёт таблицы и пользователя БД.
        try (Connection connection = DriverManager.getConnection(url, "sa", adminPassword);
             Statement sql = connection.createStatement()) {

            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));

            String password = appPassword.replace("'", "''");
            sql.execute("CREATE USER IF NOT EXISTS EXAM_APP PASSWORD '" + password + "'");
            sql.execute("ALTER USER EXAM_APP SET PASSWORD '" + password + "'");

            String[] tables = {"users", "exams", "exam_tasks", "exam_attempts", "task_solutions"};
            for (String table : tables) {
                sql.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON " + table + " TO EXAM_APP");
            }

            // Тестовые данные добавляются только в пустую БД.
            boolean empty;
            try (ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM exams")) {
                rows.next();
                empty = rows.getInt(1) == 0;
            }
            if (empty) ScriptUtils.executeSqlScript(connection, new ClassPathResource("data.sql"));
        }

        // Все HTTP-запросы работают уже от ограниченного пользователя EXAM_APP.
        SpringApplication.run(ExamApplication.class, args);
    }
}
