package ru.dependencyhell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;

class ConfigurationControllerTest {
    static class CountingJdbc extends JdbcTemplate {
        int writes = 0;

        @Override
        public int update(String sql, Object... args) {
            writes++;
            return 1;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            return List.of();
        }
    }

    @Test
    void acceptedConfigIsPersistedAndReturned() throws Exception {
        CountingJdbc jdbc = new CountingJdbc();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConfigurationController(jdbc)).build();
        mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content("""
                {"packageName":"express","repository":"https://registry.npmjs.org","testMode":false,"asciiTree":true}
                """))
            .andExpect(status().isCreated())
            .andExpect(content().string(containsString("CONFIGURED")))
            .andExpect(content().string(containsString("express")));
        assertEquals(1, jdbc.writes);
    }

    @Test
    void badConfigIsRejectedBeforeDatabaseWrite() throws Exception {
        CountingJdbc jdbc = new CountingJdbc();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConfigurationController(jdbc)).build();
        mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content("""
                {"packageName":"Bad package","repository":"not a url","testMode":false,"asciiTree":true}
                """))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("packageName")))
            .andExpect(content().string(containsString("repository")));
        assertEquals(0, jdbc.writes);
    }

    @Test
    void missingBooleansAreRejectedBeforeDatabaseWrite() throws Exception {
        CountingJdbc jdbc = new CountingJdbc();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConfigurationController(jdbc)).build();
        mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content("""
                {"packageName":"express","repository":"https://registry.npmjs.org"}
                """))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("testMode")))
            .andExpect(content().string(containsString("asciiTree")));
        assertEquals(0, jdbc.writes);
    }

    @Test
    void missingRunReturnsNotFound() throws Exception {
        CountingJdbc jdbc = new CountingJdbc();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConfigurationController(jdbc)).build();
        mvc.perform(get("/api/runs/unknown"))
            .andExpect(status().isNotFound());
    }
}
