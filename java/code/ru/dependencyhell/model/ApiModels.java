package ru.dependencyhell.model;

import java.util.List;

public final class ApiModels {
    private ApiModels() { }

    public record Configuration(String packageName, String repository, Boolean testMode, Boolean asciiTree) { }

    public record Run(String id, String packageName, String repository, Boolean testMode, Boolean asciiTree,
                      String status, int stage, String packageVersion) { }

    public record Dependency(String name, String versionSpec) { }

    public record DirectDependencies(String packageName, String version, List<Dependency> dependencies) { }

    public record RegistryRequest(String packageName, String repository) { }
}
