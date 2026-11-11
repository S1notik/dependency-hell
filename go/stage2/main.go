package main

import (
    "encoding/json"
    "fmt"
    "io"
    "net/http"
    "net/url"
    "regexp"
    "sort"
    "strings"
    "time"
)

type RegistryRequest struct {
    PackageName string `json:"packageName"`
    Repository  string `json:"repository"`
}

type Dependency struct {
    Name        string `json:"name"`
    VersionSpec string `json:"versionSpec"`
}

type DirectDependencies struct {
    PackageName  string       `json:"packageName"`
    Version      string       `json:"version"`
    Dependencies []Dependency `json:"dependencies"`
}

type Metadata struct {
    DistTags map[string]string          `json:"dist-tags"`
    Versions map[string]json.RawMessage `json:"versions"`
}

var packagePattern = regexp.MustCompile(`^(?:@[a-z0-9][a-z0-9._~-]*/)?[a-z0-9][a-z0-9._~-]*$`)

// Получаем метаданные без npm CLI и библиотек для разрешения зависимостей.
func fetchDirect(input RegistryRequest, client *http.Client) (DirectDependencies, int, error) {
    empty := DirectDependencies{}
    if len(input.PackageName) > 214 || !packagePattern.MatchString(input.PackageName) {
        return empty, http.StatusBadRequest, fmt.Errorf("некорректное имя npm-пакета")
    }
    base, err := url.Parse(input.Repository)
    if err != nil || base == nil || (base.Scheme != "http" && base.Scheme != "https") || base.Hostname() == "" || base.User != nil || base.RawQuery != "" || base.Fragment != "" {
        return empty, http.StatusBadRequest, fmt.Errorf("некорректный URL реестра")
    }
    endpoint := strings.TrimRight(input.Repository, "/") + "/" + url.PathEscape(input.PackageName)
    request, err := http.NewRequest(http.MethodGet, endpoint, nil)
    if err != nil {
        return empty, http.StatusBadRequest, fmt.Errorf("не удалось сформировать URL: %w", err)
    }
    request.Header.Set("Accept", "application/vnd.npm.install-v1+json, application/json")
    response, err := client.Do(request)
    if err != nil {
        return empty, http.StatusBadGateway, fmt.Errorf("ошибка обращения к реестру: %w", err)
    }
    defer response.Body.Close()
    if response.StatusCode == http.StatusNotFound {
        return empty, http.StatusNotFound, fmt.Errorf("пакет %s не найден в реестре", input.PackageName)
    }
    if response.StatusCode != http.StatusOK {
        return empty, http.StatusBadGateway, fmt.Errorf("реестр вернул HTTP %d", response.StatusCode)
    }
    const maxBytes = 64 << 20
    raw, err := io.ReadAll(io.LimitReader(response.Body, maxBytes+1))
    if err != nil {
        return empty, http.StatusBadGateway, fmt.Errorf("ошибка чтения метаданных: %w", err)
    }
    if len(raw) > maxBytes {
        return empty, http.StatusBadGateway, fmt.Errorf("метаданные пакета превышают 64 МиБ")
    }
    var metadata Metadata
    if err := json.Unmarshal(raw, &metadata); err != nil {
        return empty, http.StatusBadGateway, fmt.Errorf("реестр вернул некорректный JSON: %w", err)
    }
    version := metadata.DistTags["latest"]
    document, found := metadata.Versions[version]
    if version == "" || !found {
        return empty, http.StatusBadGateway, fmt.Errorf("в метаданных нет версии из dist-tags.latest")
    }
    var versionData struct {
        Dependencies map[string]string `json:"dependencies"`
    }
    if err := json.Unmarshal(document, &versionData); err != nil {
        return empty, http.StatusBadGateway, fmt.Errorf("некорректные данные версии %s: %w", version, err)
    }
    names := make([]string, 0, len(versionData.Dependencies))
    for name := range versionData.Dependencies {
        names = append(names, name)
    }
    sort.Strings(names)
    dependencies := make([]Dependency, 0, len(names))
    for _, name := range names {
        dependencies = append(dependencies, Dependency{Name: name, VersionSpec: versionData.Dependencies[name]})
    }
    return DirectDependencies{PackageName: input.PackageName, Version: version, Dependencies: dependencies}, http.StatusOK, nil
}

func sendError(w http.ResponseWriter, status int, message string) {
    w.Header().Set("Content-Type", "application/json; charset=utf-8")
    w.WriteHeader(status)
    _ = json.NewEncoder(w).Encode(map[string]string{"error": message})
}

func makeHandler(client *http.Client) http.Handler {
    mux := http.NewServeMux()
    mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
        if r.Method != http.MethodGet {
            sendError(w, http.StatusMethodNotAllowed, "используйте GET")
            return
        }
        w.Header().Set("Content-Type", "application/json")
        _ = json.NewEncoder(w).Encode(map[string]string{"status": "UP"})
    })
    mux.HandleFunc("/api/dependencies", func(w http.ResponseWriter, r *http.Request) {
        if r.Method != http.MethodPost {
            sendError(w, http.StatusMethodNotAllowed, "используйте POST")
            return
        }
        defer r.Body.Close()
        var input RegistryRequest
        decoder := json.NewDecoder(http.MaxBytesReader(w, r.Body, 1<<20))
        decoder.DisallowUnknownFields()
        if err := decoder.Decode(&input); err != nil {
            sendError(w, http.StatusBadRequest, "некорректный JSON запроса")
            return
        }
        result, status, err := fetchDirect(input, client)
        if err != nil {
            sendError(w, status, err.Error())
            return
        }
        w.Header().Set("Content-Type", "application/json; charset=utf-8")
        _ = json.NewEncoder(w).Encode(result)
    })
    return mux
}

func main() {
    client := &http.Client{Timeout: 20 * time.Second}
    fmt.Println("Go registry adapter listening on :8081")
    server := &http.Server{Addr: ":8081", Handler: makeHandler(client), ReadHeaderTimeout: 5 * time.Second}
    if err := server.ListenAndServe(); err != nil {
        panic(err)
    }
}
