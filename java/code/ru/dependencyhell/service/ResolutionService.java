package ru.dependencyhell.service;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.dependencyhell.client.RegistryClient;
import ru.dependencyhell.client.VersionClient;
import ru.dependencyhell.model.ApiModels.DirectDependencies;
import ru.dependencyhell.model.ApiModels.RegistryVersions;
import ru.dependencyhell.model.ApiModels.ResolutionRequest;
import ru.dependencyhell.model.ApiModels.ResolvedPackage;

@Service
public class ResolutionService {
    private final RegistryClient registry;
    private final VersionClient versions;
    public ResolutionService(RegistryClient registry, VersionClient versions) { this.registry = registry; this.versions = versions; }

    public ResolvedPackage resolve(ResolutionRequest request) {
        if (request == null || request.name() == null || request.versionSpec() == null || request.repository() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нужны имя, диапазон и URL реестра");
        String name = request.name();
        String spec = request.versionSpec().trim();
        if (spec.startsWith("npm:")) {
            String alias = spec.substring(4);
            int separator = alias.lastIndexOf('@');
            name = separator > 0 ? alias.substring(0, separator) : alias;
            spec = separator > 0 ? alias.substring(separator + 1) : "*";
        }
        if (spec.startsWith("git+") || spec.startsWith("git:") || spec.startsWith("git@") || spec.startsWith("http:") || spec.startsWith("https:") || spec.startsWith("file:") || spec.startsWith("workspace:") || spec.startsWith("link:") || spec.startsWith("github:"))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Зависимость " + request.name() + " использует пока неподдерживаемый источник: " + spec);
        List<String> errors = ConfigurationService.Validate_CONFIG(name, request.repository(), false, true, 214, 2048, 1, "Ошибка: ");
        if (!errors.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", errors));
        RegistryVersions available = registry.versions(name, request.repository());
        String selected = versions.resolve(spec, available.versions(), available.distTags());
        DirectDependencies result = registry.version(name, request.repository(), selected);
        return new ResolvedPackage(result.packageName(), result.version(), result.dependencies());
    }
}
