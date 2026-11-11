package ru.dependencyhell;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import ru.dependencyhell.client.RegistryClient;
import ru.dependencyhell.model.ApiModels.Dependency;
import ru.dependencyhell.model.ApiModels.DirectDependencies;
import ru.dependencyhell.model.ApiModels.Run;
import ru.dependencyhell.repository.RunRepository;
import ru.dependencyhell.service.ConfigurationService;
import ru.dependencyhell.service.DependencyService;

class DependenciesControllerTest {
    static class FakeRepository extends RunRepository {
        Run run;
        DirectDependencies saved;
        int writes;
        FakeRepository(boolean testMode) {
            super(new JdbcTemplate());
            run = new Run("sample-id", testMode ? "A" : "express", "https://registry.npmjs.org", testMode, true, "CONFIGURED", 1, null);
        }
        @Override public Optional<Run> find(String id) { return "sample-id".equals(id) ? Optional.of(run) : Optional.empty(); }
        @Override public void saveDependencies(String id, DirectDependencies report) {
            writes++;
            saved = report;
            run = new Run(run.id(), run.packageName(), run.repository(), run.testMode(), run.asciiTree(), "COMPLETED", 2, report.version());
        }
        @Override public List<Dependency> findDependencies(String id) { return saved.dependencies(); }
    }

    static class FakeRegistry extends RegistryClient {
        boolean fail;
        FakeRegistry() { super("http://127.0.0.1:8081"); }
        @Override public DirectDependencies fetch(String name, String repository) {
            if (fail) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Пакет не найден");
            return new DirectDependencies(name, "5.0.0", List.of(new Dependency("accepts", "^2.0.0")));
        }
    }

    private MockMvc mvc(FakeRepository repository, FakeRegistry registry) {
        ConfigurationService configuration = new ConfigurationService(repository);
        DependencyService dependencies = new DependencyService(repository, configuration, registry);
        return MockMvcBuilders.standaloneSetup(new ConfigurationController(configuration, dependencies))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test void analyzesAndReturnsSavedDirectDependencies() throws Exception {
        FakeRepository repository = new FakeRepository(false);
        MockMvc mvc = mvc(repository, new FakeRegistry());
        mvc.perform(post("/api/runs/sample-id/dependencies"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("accepts")))
            .andExpect(content().string(containsString("^2.0.0")));
        assertEquals(1, repository.writes);
        mvc.perform(get("/api/runs/sample-id/dependencies"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("5.0.0")));
    }

    @Test void rejectsUnfinishedTestModeAndDoesNotWrite() throws Exception {
        FakeRepository repository = new FakeRepository(true);
        mvc(repository, new FakeRegistry()).perform(post("/api/runs/sample-id/dependencies"))
            .andExpect(status().isBadRequest());
        assertEquals(0, repository.writes);
    }

    @Test void reportsMissingPackageWithoutSaving() throws Exception {
        FakeRepository repository = new FakeRepository(false);
        FakeRegistry registry = new FakeRegistry();
        registry.fail = true;
        mvc(repository, registry).perform(post("/api/runs/sample-id/dependencies"))
            .andExpect(status().isNotFound())
            .andExpect(content().string(containsString("Пакет не найден")));
        assertEquals(0, repository.writes);
    }

    @Test void readingBeforeAnalysisReturnsConflict() throws Exception {
        mvc(new FakeRepository(false), new FakeRegistry()).perform(get("/api/runs/sample-id/dependencies"))
            .andExpect(status().isConflict());
    }
}
