package ru.dependencyhell.client;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import ru.dependencyhell.model.ApiModels.GraphRequest;
import ru.dependencyhell.model.ApiModels.GraphResult;
import ru.dependencyhell.model.ApiModels.LoadOrder;

@Component
public class GraphClient {
    private final RestClient client;

    @Autowired
    public GraphClient(@Value("${graph.service.url:http://localhost:8082}") String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(590));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public GraphClient(RestClient client) { this.client = client; }

    public GraphResult build(GraphRequest request) {
        try {
            GraphResult graph = client.post().uri("/api/graph").body(request).retrieve().body(GraphResult.class);
            if (graph == null || graph.root() == null || graph.nodes() == null || graph.edges() == null || graph.visitOrder() == null)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Rust вернул неполный граф");
            return graph;
        } catch (RestClientResponseException error) {
            HttpStatus status = error.getStatusCode().is4xxClientError() ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, "Ошибка Rust: " + error.getResponseBodyAsString(), error);
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось обратиться к Rust: " + error.getMessage(), error);
        }
    }

    public LoadOrder order(GraphResult graph) {
        try {
            LoadOrder result = client.post().uri("/api/order").body(graph).retrieve().body(LoadOrder.class);
            if (result == null || result.possible() == null || result.packages() == null || result.cycleEdges() == null)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Rust вернул неполный порядок загрузки");
            return result;
        } catch (RestClientResponseException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ошибка Rust: " + error.getResponseBodyAsString(), error);
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось обратиться к Rust: " + error.getMessage(), error);
        }
    }
}
