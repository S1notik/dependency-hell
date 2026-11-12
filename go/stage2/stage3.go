package main

import (
    "encoding/json"
    "fmt"
    "io"
    "net/http"
    "net/url"
    "sort"
    "strings"
    "sync"
    "time"
)

type VersionsResult struct {
    PackageName string            `json:"packageName"`
    DistTags    map[string]string `json:"distTags"`
    Versions    []string          `json:"versions"`
}

type VersionRequest struct {
    PackageName string `json:"packageName"`
    Repository  string `json:"repository"`
    Version     string `json:"version"`
}

type cachedMetadata struct {
    value   Metadata
    expires time.Time
    size    int
}

var stage3Cache = struct {
    sync.Mutex
    items map[string]cachedMetadata
    bytes int
}{items: make(map[string]cachedMetadata)}

// Кэш нужен, чтобы не скачивать весь документ npm дважды на каждую версию.
func getStage3Metadata(input RegistryRequest, client *http.Client) (Metadata, int, error) {
    empty := Metadata{}
    if len(input.PackageName) > 214 || !packagePattern.MatchString(input.PackageName) {
        return empty, http.StatusBadRequest, fmt.Errorf("некорректное имя npm-пакета")
    }
    base, err := url.Parse(input.Repository)
    if err != nil || base == nil || (base.Scheme != "http" && base.Scheme != "https") || base.Hostname() == "" || base.User != nil || base.RawQuery != "" || base.Fragment != "" {
        return empty, http.StatusBadRequest, fmt.Errorf("некорректный URL реестра")
    }
    key := strings.TrimRight(input.Repository, "/") + "|" + input.PackageName
    stage3Cache.Lock()
    saved, found := stage3Cache.items[key]
    if found && time.Now().Before(saved.expires) {
        stage3Cache.Unlock()
        return saved.value, http.StatusOK, nil
    }
    stage3Cache.Unlock()
    endpoint := strings.TrimRight(input.Repository, "/") + "/" + url.PathEscape(input.PackageName)
    request, err := http.NewRequest(http.MethodGet, endpoint, nil)
    if err != nil {
        return empty, http.StatusBadRequest, fmt.Errorf("некорректный URL: %w", err)
    }
    request.Header.Set("Accept", "application/vnd.npm.install-v1+json, application/json")
    response, err := client.Do(request)
    if err != nil {
        return empty, http.StatusBadGateway, fmt.Errorf("ошибка обращения к реестру: %w", err)
    }
    defer response.Body.Close()
    if response.StatusCode == http.StatusNotFound {
        return empty, http.StatusNotFound, fmt.Errorf("пакет %s не найден", input.PackageName)
    }
    if response.StatusCode != http.StatusOK {
        return empty, http.StatusBadGateway, fmt.Errorf("реестр вернул HTTP %d", response.StatusCode)
    }
    const limit = 64 << 20
    data, err := io.ReadAll(io.LimitReader(response.Body, limit+1))
    if err != nil || len(data) > limit {
        return empty, http.StatusBadGateway, fmt.Errorf("ошибка чтения или слишком большой ответ реестра")
    }
    var result Metadata
    if err := json.Unmarshal(data, &result); err != nil || len(result.Versions) == 0 {
        return empty, http.StatusBadGateway, fmt.Errorf("неверный JSON или отсутствуют версии пакета")
    }
    stage3Cache.Lock()
    if old, ok := stage3Cache.items[key]; ok {
        stage3Cache.bytes -= old.size
        delete(stage3Cache.items, key)
    }
    if len(stage3Cache.items) >= 128 || stage3Cache.bytes+len(data) > 64<<20 {
        stage3Cache.items = make(map[string]cachedMetadata)
        stage3Cache.bytes = 0
    }
    if len(data) <= 16<<20 {
        stage3Cache.items[key] = cachedMetadata{value: result, expires: time.Now().Add(2 * time.Minute), size: len(data)}
        stage3Cache.bytes += len(data)
    }
    stage3Cache.Unlock()
    return result, http.StatusOK, nil
}

func stage3Versions(input RegistryRequest, client *http.Client) (VersionsResult, int, error) {
    meta, status, err := getStage3Metadata(input, client)
    if err != nil { return VersionsResult{}, status, err }
    names := make([]string, 0, len(meta.Versions))
    for version := range meta.Versions { names = append(names, version) }
    sort.Strings(names)
    if meta.DistTags == nil { meta.DistTags = make(map[string]string) }
    return VersionsResult{PackageName: input.PackageName, DistTags: meta.DistTags, Versions: names}, http.StatusOK, nil
}

func stage3Specific(input VersionRequest, client *http.Client) (DirectDependencies, int, error) {
    meta, status, err := getStage3Metadata(RegistryRequest{PackageName: input.PackageName, Repository: input.Repository}, client)
    if err != nil { return DirectDependencies{}, status, err }
    raw, found := meta.Versions[input.Version]
    if !found { return DirectDependencies{}, http.StatusNotFound, fmt.Errorf("версия %s не найдена у %s", input.Version, input.PackageName) }
    var versionData struct { Dependencies map[string]string `json:"dependencies"` }
    if string(raw) == "null" || json.Unmarshal(raw, &versionData) != nil {
        return DirectDependencies{}, http.StatusBadGateway, fmt.Errorf("неверные данные выбранной версии")
    }
    names := make([]string, 0, len(versionData.Dependencies))
    for name := range versionData.Dependencies { names = append(names, name) }
    sort.Strings(names)
    dependencies := make([]Dependency, 0, len(names))
    for _, name := range names { dependencies = append(dependencies, Dependency{Name: name, VersionSpec: versionData.Dependencies[name]}) }
    return DirectDependencies{PackageName: input.PackageName, Version: input.Version, Dependencies: dependencies}, http.StatusOK, nil
}

func registerStage3(mux *http.ServeMux, client *http.Client) {
    mux.HandleFunc("/api/versions", func(w http.ResponseWriter, r *http.Request) {
        if r.Method != http.MethodPost { sendError(w, http.StatusMethodNotAllowed, "используйте POST"); return }
        defer r.Body.Close()
        var input RegistryRequest
        if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 1<<20)).Decode(&input); err != nil { sendError(w, http.StatusBadRequest, "некорректный JSON"); return }
        result, status, err := stage3Versions(input, client)
        if err != nil { sendError(w, status, err.Error()); return }
        w.Header().Set("Content-Type", "application/json; charset=utf-8")
        _ = json.NewEncoder(w).Encode(result)
    })
    mux.HandleFunc("/api/version", func(w http.ResponseWriter, r *http.Request) {
        if r.Method != http.MethodPost { sendError(w, http.StatusMethodNotAllowed, "используйте POST"); return }
        defer r.Body.Close()
        var input VersionRequest
        if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 1<<20)).Decode(&input); err != nil { sendError(w, http.StatusBadRequest, "некорректный JSON"); return }
        result, status, err := stage3Specific(input, client)
        if err != nil { sendError(w, status, err.Error()); return }
        w.Header().Set("Content-Type", "application/json; charset=utf-8")
        _ = json.NewEncoder(w).Encode(result)
    })
}
