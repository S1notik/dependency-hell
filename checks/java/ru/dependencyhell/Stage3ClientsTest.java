package ru.dependencyhell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.dependencyhell.client.GraphClient;
import ru.dependencyhell.client.VersionClient;
import ru.dependencyhell.model.ApiModels.GraphRequest;

class Stage3ClientsTest {
    @Test void rustGraphContractMatchesJson() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://graph:8082");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://graph:8082/api/graph"))
            .andExpect(content().json("{\"root\":\"A\",\"testMode\":true,\"testGraph\":{\"A\":[\"B\"]},\"repository\":\"graph.json\"}"))
            .andRespond(withSuccess("{\"root\":\"A\",\"nodes\":[{\"id\":\"A\",\"name\":\"A\",\"version\":null},{\"id\":\"B\",\"name\":\"B\",\"version\":null}],\"edges\":[{\"from\":\"A\",\"to\":\"B\",\"cycle\":false}],\"visitOrder\":[\"A\",\"B\"]}", MediaType.APPLICATION_JSON));
        var graph = new GraphClient(builder.build()).build(new GraphRequest("A", true, Map.of("A", List.of("B")), "graph.json"));
        assertEquals(List.of("A", "B"), graph.visitOrder());
        assertEquals("B", graph.edges().get(0).to());
        server.verify();
    }

    @Test void csharpVersionContractMatchesJson() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://versions:8083");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://versions:8083/api/resolve"))
            .andExpect(content().json("{\"spec\":\"^1.0.0\",\"versions\":[\"1.0.0\",\"1.2.0\"],\"distTags\":{\"latest\":\"1.2.0\"}}"))
            .andRespond(withSuccess("{\"version\":\"1.2.0\"}", MediaType.APPLICATION_JSON));
        assertEquals("1.2.0", new VersionClient(builder.build()).resolve("^1.0.0", List.of("1.0.0", "1.2.0"), Map.of("latest", "1.2.0")));
        server.verify();
    }
}
