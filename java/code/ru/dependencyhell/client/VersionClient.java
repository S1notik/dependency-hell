package ru.dependencyhell.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import ru.dependencyhell.model.ApiModels.VersionRequest;
import ru.dependencyhell.model.ApiModels.VersionResponse;

@Component
public class VersionClient {
    private final RestClient client;

    @Autowired
    public VersionClient(@Value("${version.resolver.url:http://localhost:8083}") String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public VersionClient(RestClient client) { this.client = client; }

    public String resolve(String spec, List<String> versions, Map<String, String> tags) {
        try {
            VersionResponse response = client.post().uri("/api/resolve").body(new VersionRequest(spec, versions, tags))
                    .retrieve().body(VersionResponse.class);
            if (response == null || response.version() == null || !versions.contains(response.version()))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "C# вернул отсутствующую версию");
            return response.version();
        } catch (RestClientResponseException error) {
            HttpStatus status = error.getStatusCode().is4xxClientError() ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, "Ошибка диапазона C#: " + error.getResponseBodyAsString(), error);
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось обратиться к C#: " + error.getMessage(), error);
        }
    }
}
