package ru.dependencyhell;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import ru.dependencyhell.client.RegistryClient;
import ru.dependencyhell.client.VersionClient;
import ru.dependencyhell.model.ApiModels.*;
import ru.dependencyhell.service.ResolutionService;

class ResolutionServiceTest {
    static class FakeRegistry extends RegistryClient {
        String name;
        FakeRegistry() { super("http://127.0.0.1:8081"); }
        @Override public RegistryVersions versions(String name, String repository) {
            this.name = name;
            return new RegistryVersions(name, Map.of("latest","2.0.0"), List.of("1.0.0","2.0.0"));
        }
        @Override public DirectDependencies version(String name, String repository, String version) {
            return new DirectDependencies(name, version, List.of(new Dependency("dep", "^1")));
        }
    }
    static class FakeVersions extends VersionClient {
        String spec;
        FakeVersions() { super("http://127.0.0.1:8083"); }
        @Override public String resolve(String spec, List<String> versions, Map<String,String> tags) { this.spec = spec; return "1.0.0"; }
    }
    @Test void resolvesAliasUsingRealPackageAndRange() {
        FakeRegistry registry = new FakeRegistry();
        FakeVersions versions = new FakeVersions();
        ResolvedPackage result = new ResolutionService(registry, versions).resolve(new ResolutionRequest("alias", "npm:@scope/real@^1", "https://registry.npmjs.org"));
        assertEquals("@scope/real", registry.name);
        assertEquals("^1", versions.spec);
        assertEquals("1.0.0", result.version());
        assertEquals("dep", result.dependencies().get(0).name());
    }
    @Test void rejectsUnsupportedGitSource() {
        var service = new ResolutionService(new FakeRegistry(), new FakeVersions());
        assertThrows(ResponseStatusException.class, () -> service.resolve(new ResolutionRequest("package", "git+https://example.org/repo", "https://registry.npmjs.org")));
    }
}
