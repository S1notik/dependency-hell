package ru.dependencyhell.model;

import java.util.List;
import java.util.Map;

public final class ApiModels {
    private ApiModels() { }

    public record Configuration(String packageName, String repository, Boolean testMode, Boolean asciiTree) { }

    public record Run(String id, String packageName, String repository, Boolean testMode, Boolean asciiTree,
                      String status, int stage, String packageVersion) { }

    public record Dependency(String name, String versionSpec) { }

    public record DirectDependencies(String packageName, String version, List<Dependency> dependencies) { }

    public record RegistryRequest(String packageName, String repository) { }

    public record RegistryVersions(String packageName, Map<String, String> distTags, List<String> versions) { }
    public record RegistryVersionRequest(String packageName, String repository, String version) { }
    public record VersionRequest(String spec, List<String> versions, Map<String, String> distTags) { }
    public record VersionResponse(String version) { }
    public record ResolutionRequest(String name, String versionSpec, String repository) { }
    public record ResolvedPackage(String name, String version, List<Dependency> dependencies) { }

    public record GraphInput(Map<String, List<String>> testGraph) { }
    public record GraphRequest(String root, boolean testMode, Map<String, List<String>> testGraph, String repository) { }
    public record GraphNode(String id, String name, String version) { }
    public record GraphEdge(String from, String to, boolean cycle) { }
    public record GraphResult(String root, List<GraphNode> nodes, List<GraphEdge> edges, List<String> visitOrder) { }
    public record LoadOrder(Boolean possible, List<String> packages, List<GraphEdge> cycleEdges) { }
}
