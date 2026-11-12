package ru.dependencyhell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.dependencyhell.client.TreeClient;
import ru.dependencyhell.model.ApiModels.*;

class TreeClientTest {
    @Test void exchangesGraphAndTreeAsJson() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://tree:8084");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://tree:8084/api/ascii-tree"))
            .andExpect(content().json("{\"root\":\"A\",\"nodes\":[{\"id\":\"A\",\"name\":\"A\",\"version\":null}],\"edges\":[],\"visitOrder\":[\"A\"]}"))
            .andRespond(withSuccess("{\"asciiTree\":\"A\"}", MediaType.APPLICATION_JSON));
        GraphResult graph = new GraphResult("A", List.of(new GraphNode("A", "A", null)), List.of(), List.of("A"));
        assertEquals("A", new TreeClient(builder.build()).render(graph));
        server.verify();
    }
}
