package ru.dependencyhell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.dependencyhell.client.RegistryClient;
import ru.dependencyhell.model.ApiModels.DirectDependencies;

class RegistryClientTest {
    @Test void exchangesJsonWithAdapter() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://registry:8081");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://registry:8081/api/dependencies"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"packageName\":\"@scope/pkg\",\"repository\":\"https://registry.npmjs.org\"}"))
                .andRespond(withSuccess("{\"packageName\":\"@scope/pkg\",\"version\":\"1.2.3\",\"dependencies\":[{\"name\":\"dep\",\"versionSpec\":\"^1.0.0\"}]}", MediaType.APPLICATION_JSON));
        RegistryClient client = new RegistryClient(builder.build());
        DirectDependencies result = client.fetch("@scope/pkg", "https://registry.npmjs.org");
        assertEquals("1.2.3", result.version());
        assertEquals("dep", result.dependencies().get(0).name());
        server.verify();
    }
}
