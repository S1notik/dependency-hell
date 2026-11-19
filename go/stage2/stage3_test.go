package main

import (
    "net/http"
    "net/http/httptest"
    "sync/atomic"
    "testing"
)

func TestStage3VersionsSpecificAndCache(t *testing.T) {
    var requests atomic.Int32
    registry := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
        requests.Add(1)
        _, _ = w.Write([]byte(`{"dist-tags":{"latest":"2.0.0"},"versions":{"1.0.0":{"dependencies":{"old":"^1"}},"2.0.0":{"dependencies":{"new":"~2"}}}}`))
    }))
    defer registry.Close()
    input := RegistryRequest{PackageName: "sample", Repository: registry.URL}
    versions, status, err := stage3Versions(input, registry.Client())
    if err != nil || status != 200 || len(versions.Versions) != 2 || versions.DistTags["latest"] != "2.0.0" { t.Fatalf("versions: %#v %d %v", versions, status, err) }
    deps, status, err := stage3Specific(VersionRequest{PackageName: "sample", Repository: registry.URL, Version: "1.0.0"}, registry.Client())
    if err != nil || status != 200 || len(deps.Dependencies) != 1 || deps.Dependencies[0].Name != "old" { t.Fatalf("version: %#v %d %v", deps, status, err) }
    if requests.Load() != 1 { t.Fatalf("ожидался один запрос к реестру, получено %d", requests.Load()) }
    _, status, err = stage3Specific(VersionRequest{PackageName: "sample", Repository: registry.URL, Version: "9.9.9"}, registry.Client())
    if err == nil || status != 404 { t.Fatalf("отсутствующая версия: %d %v", status, err) }
}

func TestStage3MetadataReportsRegistryErrors(t *testing.T) {
    cases := []struct {
        name       string
        status     int
        body       string
        wantStatus int
    }{
        {name: "missing package", status: http.StatusNotFound, wantStatus: http.StatusNotFound},
        {name: "registry error", status: http.StatusInternalServerError, wantStatus: http.StatusBadGateway},
        {name: "invalid JSON", status: http.StatusOK, body: "{", wantStatus: http.StatusBadGateway},
        {name: "no versions", status: http.StatusOK, body: `{"versions":{}}`, wantStatus: http.StatusBadGateway},
    }
    for _, tc := range cases {
        t.Run(tc.name, func(t *testing.T) {
            registry := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
                w.WriteHeader(tc.status)
                _, _ = w.Write([]byte(tc.body))
            }))
            defer registry.Close()
            input := RegistryRequest{PackageName: "sample", Repository: registry.URL}
            _, status, err := getStage3Metadata(input, registry.Client())
            if err == nil || status != tc.wantStatus {
                t.Fatalf("expected status %d and error, got %d, %v", tc.wantStatus, status, err)
            }
        })
    }
}
