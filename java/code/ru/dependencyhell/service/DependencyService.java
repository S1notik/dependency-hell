package ru.dependencyhell.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.dependencyhell.client.RegistryClient;
import ru.dependencyhell.model.ApiModels.DirectDependencies;
import ru.dependencyhell.model.ApiModels.Run;
import ru.dependencyhell.repository.RunRepository;

@Service
public class DependencyService {
    private final RunRepository repository;
    private final ConfigurationService configurations;
    private final RegistryClient registry;

    public DependencyService(RunRepository repository, ConfigurationService configurations, RegistryClient registry) {
        this.repository = repository;
        this.configurations = configurations;
        this.registry = registry;
    }

    @Transactional
    public DirectDependencies analyze(String id) {
        Run run = configurations.find(id);
        if (Boolean.TRUE.equals(run.testMode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Тестовый граф будет реализован на этапе 3; для этапа 2 укажите test_mode=false");
        }
        DirectDependencies result = registry.fetch(run.packageName(), run.repository());
        repository.saveDependencies(id, result);
        return result;
    }

    public DirectDependencies getSaved(String id) {
        Run run = configurations.find(id);
        if (run.stage() < 2 || run.packageVersion() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Для этого запуска зависимости ещё не получены");
        }
        return new DirectDependencies(run.packageName(), run.packageVersion(), repository.findDependencies(id));
    }
}
