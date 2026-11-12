package ru.dependencyhell;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.dependencyhell.model.ApiModels.Visualization;
import ru.dependencyhell.service.VisualizationService;

class VisualizationControllerTest {
    static class FakeVisualization extends VisualizationService {
        String usedId;
        FakeVisualization() { super(null, null, null, null, null, null); }
        @Override public Visualization build(String id) { usedId = id; return new Visualization("flowchart TD\n    n0[\"A\"]", "A"); }
        @Override public Visualization getSaved(String id) { usedId = id; return new Visualization("flowchart TD\n    n0[\"A\"]", "A"); }
    }

    @Test void mapsPostAndGetVisualization() throws Exception {
        FakeVisualization service = new FakeVisualization();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new VisualizationController(service)).build();
        mvc.perform(post("/api/runs/sample/visualization"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("flowchart TD")));
        assertEquals("sample", service.usedId);
        mvc.perform(get("/api/runs/sample/visualization"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("asciiTree")));
        assertEquals("sample", service.usedId);
    }
}
