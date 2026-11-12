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
import ru.dependencyhell.model.ApiModels.AsciiTree;
import ru.dependencyhell.model.ApiModels.GraphResult;

@Component
public class TreeClient {
    private final RestClient client;

    @Autowired
    public TreeClient(@Value("${tree.service.url:http://localhost:8084}") String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public TreeClient(RestClient client) { this.client = client; }

    public String render(GraphResult graph) {
        try {
            AsciiTree result = client.post().uri("/api/ascii-tree").body(graph).retrieve().body(AsciiTree.class);
            if (result == null || result.asciiTree() == null || result.asciiTree().isEmpty())
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "JavaScript вернул пустое дерево");
            return result.asciiTree();
        } catch (RestClientResponseException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ошибка JavaScript: " + error.getResponseBodyAsString(), error);
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось обратиться к JavaScript: " + error.getMessage(), error);
        }
    }
}
