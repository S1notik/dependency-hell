package ru.dependencyhell;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.dependencyhell.model.ApiModels.Configuration;
import ru.dependencyhell.model.ApiModels.DirectDependencies;
import ru.dependencyhell.model.ApiModels.Run;
import ru.dependencyhell.service.ConfigurationService;
import ru.dependencyhell.service.DependencyService;

@RestController
public class ConfigurationController {
    private final ConfigurationService configurations;
    private final DependencyService dependencies;

    public ConfigurationController(ConfigurationService configurations, DependencyService dependencies) {
        this.configurations = configurations;
        this.dependencies = dependencies;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/api/runs")
    public ResponseEntity<Run> create(@RequestBody Configuration input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configurations.create(input));
    }

    @GetMapping("/api/runs/{id}")
    public Run get(@PathVariable String id) {
        return configurations.find(id);
    }

    @PostMapping("/api/runs/{id}/dependencies")
    public DirectDependencies analyze(@PathVariable String id) {
        return dependencies.analyze(id);
    }

    @GetMapping("/api/runs/{id}/dependencies")
    public DirectDependencies getDependencies(@PathVariable String id) {
        return dependencies.getSaved(id);
    }
}
