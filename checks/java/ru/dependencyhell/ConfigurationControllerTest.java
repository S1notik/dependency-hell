package ru.dependencyhell;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.dependencyhell.client.RegistryClient;
import ru.dependencyhell.repository.RunRepository;
import ru.dependencyhell.service.ConfigurationService;
import ru.dependencyhell.service.DependencyService;

class ConfigurationControllerTest {
    static class CountingJdbc extends JdbcTemplate {
        int writes = 0;
        @Override public int update(String sql, Object... args) { writes++; return 1; }
        @Override public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) { return List.of(); }
    }

    private MockMvc mvc(CountingJdbc jdbc) {
        RunRepository repository = new RunRepository(jdbc);
        ConfigurationService configuration = new ConfigurationService(repository);
        DependencyService dependencies = new DependencyService(repository, configuration, new RegistryClient("http://127.0.0.1:8081"));
        return MockMvcBuilders.standaloneSetup(new ConfigurationController(configuration, dependencies))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test void acceptedConfigIsPersistedAndReturned() throws Exception {
        CountingJdbc jdbc = new CountingJdbc();
        mvc(jdbc).perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content("""
                {"packageName":"express","repository":"https://registry.npmjs.org","testMode":false,"asciiTree":true}
                """))
            .andExpect(status().isCreated())
            .andExpect(content().string(containsString("CONFIGURED")))
            .andExpect(content().string(containsString("express")));
        assertEquals(1, jdbc.writes);
    }

    @Test void badConfigIsRejectedBeforeDatabaseWrite() throws Exception {
        CountingJdbc jdbc = new CountingJdbc();
        mvc(jdbc).perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content("""
                {"packageName":"Bad package","repository":"not a url","testMode":false,"asciiTree":true}
                """))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("packageName")))
            .andExpect(content().string(containsString("repository")));
        assertEquals(0, jdbc.writes);
    }

    @Test void missingBooleansAreRejectedBeforeDatabaseWrite() throws Exception {
        CountingJdbc jdbc = new CountingJdbc();
        mvc(jdbc).perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content("""
                {"packageName":"express","repository":"https://registry.npmjs.org"}
                """))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("testMode")))
            .andExpect(content().string(containsString("asciiTree")));
        assertEquals(0, jdbc.writes);
    }

    @Test void missingRunReturnsNotFound() throws Exception {
        mvc(new CountingJdbc()).perform(get("/api/runs/unknown")).andExpect(status().isNotFound());
    }
}
