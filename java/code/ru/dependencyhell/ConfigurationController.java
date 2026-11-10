package ru.dependencyhell;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ConfigurationController {
    private final JdbcTemplate jdbc;

    public ConfigurationController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Configuration(String packageName, String repository, Boolean testMode, Boolean asciiTree) { }
    public record Run(String id, String packageName, String repository, Boolean testMode, Boolean asciiTree, String status, int stage) { }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/api/runs")
    public ResponseEntity<Run> create(@RequestBody Configuration input) {
        List<String> problems = Validate_CONFIG(input.packageName(), input.repository(), input.testMode(), input.asciiTree(), 214, 2048, 1, "Ошибка: ");
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO analysis_runs (id, package_name, repository, test_mode, ascii_tree, status, stage) VALUES (?, ?, ?, ?, ?, ?, ?)", id, input.packageName(), input.repository(), input.testMode(), input.asciiTree(), "CONFIGURED", 1);
        Run run = new Run(id, input.packageName(), input.repository(), input.testMode(), input.asciiTree(), "CONFIGURED", 1);
        return ResponseEntity.status(HttpStatus.CREATED).body(run);
    }

    @GetMapping("/api/runs/{id}")
    public Run get(@PathVariable String id) {
        List<Run> found = jdbc.query("SELECT id, package_name, repository, test_mode, ascii_tree, status, stage FROM analysis_runs WHERE id = ?", (rs, row) -> new Run(rs.getString("id"), rs.getString("package_name"), rs.getString("repository"), rs.getBoolean("test_mode"), rs.getBoolean("ascii_tree"), rs.getString("status"), rs.getInt("stage")), id);
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Запуск не найден");
        }
        return found.get(0);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
    }

    // Проверка параметров; на этом этапе тестовый файл проверяет клиент.
    static List<String> Validate_CONFIG(String pkg, String repo, Boolean test, Boolean tree, int nameLimit, int urlLimit, int minimum, String prefix) {
        List<String> errors = new ArrayList<>();
        if (pkg == null || pkg.isBlank()) {
            errors.add(prefix + "packageName: имя пакета обязательно");
        }
        if (pkg != null && pkg.length() > nameLimit) {
            errors.add(prefix + "packageName: больше 214 символов");
        }
        if (pkg != null && pkg.length() < minimum) {
            errors.add(prefix + "packageName: имя слишком короткое");
        }
        if (repo == null || repo.isBlank()) {
            errors.add(prefix + "repository: укажите URL или путь");
        }
        if (repo != null && repo.length() > urlLimit) {
            errors.add(prefix + "repository: больше 2048 символов");
        }
        if (test == null) {
            errors.add(prefix + "testMode: требуется true или false");
        }
        if (tree == null) {
            errors.add(prefix + "asciiTree: требуется true или false");
        }
        if (Boolean.TRUE.equals(test) && pkg != null && !pkg.matches("[A-Z]+")) {
            errors.add(prefix + "packageName: в тестовом режиме допустимы большие латинские буквы");
        }
        if (Boolean.FALSE.equals(test) && pkg != null && !pkg.matches("(?:@[a-z0-9][a-z0-9._~-]*/)?[a-z0-9][a-z0-9._~-]*")) {
            errors.add(prefix + "packageName: некорректное npm-имя");
        }
        if (Boolean.FALSE.equals(test) && repo != null && !repo.isBlank()) {
            try {
                URI uri = URI.create(repo);
                if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
                    errors.add(prefix + "repository: требуется http:// или https://");
                }
                if (uri.getHost() == null) {
                    errors.add(prefix + "repository: отсутствует хост");
                }
                if (uri.getRawUserInfo() != null) {
                    errors.add(prefix + "repository: нельзя указывать логин и пароль");
                }
                if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                    errors.add(prefix + "repository: запрос и фрагмент в URL не поддерживаются");
                }
                if (uri.getPort() > 65535 || uri.getPort() == 0) {
                    errors.add(prefix + "repository: некорректный порт");
                }
            } catch (IllegalArgumentException invalidUrl) {
                errors.add(prefix + "repository: некорректный URL");
            }
        }
        return errors;
    }
}
