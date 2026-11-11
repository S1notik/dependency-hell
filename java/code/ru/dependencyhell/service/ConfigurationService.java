package ru.dependencyhell.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.dependencyhell.model.ApiModels.Configuration;
import ru.dependencyhell.model.ApiModels.Run;
import ru.dependencyhell.repository.RunRepository;

@Service
public class ConfigurationService {
    private final RunRepository repository;

    public ConfigurationService(RunRepository repository) {
        this.repository = repository;
    }

    public Run create(Configuration input) {
        List<String> problems = Validate_CONFIG(input.packageName(), input.repository(), input.testMode(), input.asciiTree(), 214, 2048, 1, "Ошибка: ");
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        Run run = new Run(UUID.randomUUID().toString(), input.packageName(), input.repository(), input.testMode(), input.asciiTree(), "CONFIGURED", 1, null);
        repository.insert(run);
        return run;
    }

    public Run find(String id) {
        return repository.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Запуск не найден"));
    }

    // Проверка параметров намеренно оставлена одной большой функцией для перечня нарушений.
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
