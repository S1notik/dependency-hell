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
import ru.dependencyhell.model.ApiModels.DirectDependencies;
import ru.dependencyhell.model.ApiModels.RegistryRequest;

@Component
public class RegistryClient {
    private final RestClient client;

    @Autowired
    public RegistryClient(@Value("${registry.adapter.url:http://localhost:8081}") String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public RegistryClient(RestClient client) {
        this.client = client;
    }

    public DirectDependencies fetch(String name, String repository) {
        try {
            DirectDependencies result = client.post().uri("/api/dependencies")
                    .body(new RegistryRequest(name, repository)).retrieve().body(DirectDependencies.class);
            if (result == null || result.packageName() == null || !result.packageName().equals(name)
                    || result.version() == null || result.version().isBlank() || result.dependencies() == null
                    || result.dependencies().stream().anyMatch(d -> d == null || d.name() == null || d.name().isBlank() || d.versionSpec() == null)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Go-сервис вернул неполные данные о пакете");
            }
            return result;
        } catch (RestClientResponseException error) {
            HttpStatus status = error.getStatusCode().value() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, "Ошибка Go/npm: " + error.getResponseBodyAsString(), error);
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось обратиться к Go-сервису: " + error.getMessage(), error);
        }
    }
}
