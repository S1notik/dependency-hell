package main

import (
    "context"
    "encoding/json"
    "errors"
    "io"
    "net/http"
    "net/http/httptest"
    "strings"
    "testing"
    "time"
)

type timeoutFailure struct{}
func (timeoutFailure) Error() string { return "temporary network timeout" }
func (timeoutFailure) Timeout() bool { return true }
func (timeoutFailure) Temporary() bool { return true }
type roundTripFunc func(*http.Request) (*http.Response, error)
func (f roundTripFunc) RoundTrip(request *http.Request) (*http.Response, error) { return f(request) }
type contextBody struct { ctx context.Context; reader io.Reader }
func (body *contextBody) Read(buffer []byte) (int, error) {
    if err := body.ctx.Err(); err != nil { return 0, err }
    return body.reader.Read(buffer)
}
func (body *contextBody) Close() error { return nil }

func TestRetryAfterTimeoutForBothRegistryPaths(t *testing.T) {
    for _, stage3 := range []bool{false, true} {
        calls := 0
        client := &http.Client{Transport: roundTripFunc(func(request *http.Request) (*http.Response, error) {
            calls++
            if calls == 1 { return nil, timeoutFailure{} }
            return &http.Response{StatusCode: 200, Header: make(http.Header), Request: request,
                Body: &contextBody{ctx: request.Context(), reader: strings.NewReader(`{"dist-tags":{"latest":"1.0.0"},"versions":{"1.0.0":{"dependencies":{"child":"^1"}}}}`)}}, nil
        })}
        input := RegistryRequest{PackageName: "retry-test", Repository: "https://example.invalid"}
        if stage3 {
            result, status, err := stage3Versions(input, client)
            if err != nil || status != http.StatusOK || result.DistTags["latest"] != "1.0.0" { t.Fatalf("versions: %#v %d %v", result, status, err) }
        } else {
            result, status, err := fetchDirect(input, client)
            if err != nil || status != http.StatusOK || result.Version != "1.0.0" || len(result.Dependencies) != 1 { t.Fatalf("direct: %#v %d %v", result, status, err) }
        }
        if calls != 2 { t.Fatalf("expected two attempts, got %d", calls) }
    }
}

func TestRegistryDoesNotRetryNonTimeoutError(t *testing.T) {
    calls := 0
    client := &http.Client{Transport: roundTripFunc(func(*http.Request) (*http.Response, error) {
        calls++
        return nil, errors.New("permanent connection error")
    })}
    _, status, err := fetchDirect(RegistryRequest{PackageName: "retry-test", Repository: "https://example.invalid"}, client)
    if err == nil || status != http.StatusBadGateway || calls != 1 { t.Fatalf("status=%d calls=%d error=%v", status, calls, err) }
}

func TestFetchLatestAndDirectDependencies(t *testing.T) {
    var requestedURI, accept string
    registry := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
        requestedURI, accept = r.RequestURI, r.Header.Get("Accept")
        w.Header().Set("Content-Type", "application/json")
        _, _ = w.Write([]byte(`{"dist-tags":{"latest":"2.0.0"},"versions":{"1.0.0":{"dependencies":{"old":"1.0.0"}},"2.0.0":{"dependencies":{"z":"^1.0.0","a":"~2.0.0"}}}}`))
    }))
    defer registry.Close()
    result, status, err := fetchDirect(RegistryRequest{PackageName: "@scope/pkg", Repository: registry.URL}, registry.Client())
    if err != nil || status != http.StatusOK {
        t.Fatalf("unexpected result status=%d error=%v", status, err)
    }
    if !strings.Contains(strings.ToLower(requestedURI), "%2f") || !strings.Contains(accept, "application/vnd.npm.install-v1+json") {
        t.Fatalf("incorrect URI or Accept: %q %q", requestedURI, accept)
    }
    if result.Version != "2.0.0" || len(result.Dependencies) != 2 || result.Dependencies[0].Name != "a" || result.Dependencies[1].VersionSpec != "^1.0.0" {
        t.Fatalf("unexpected dependencies: %#v", result)
    }
}

func TestFetchNoDependencies(t *testing.T) {
    registry := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
        _, _ = w.Write([]byte(`{"dist-tags":{"latest":"1.0.0"},"versions":{"1.0.0":{}}}`))
    }))
    defer registry.Close()
    result, _, err := fetchDirect(RegistryRequest{PackageName: "empty", Repository: registry.URL}, registry.Client())
    if err != nil || len(result.Dependencies) != 0 {
        t.Fatalf("expected empty dependencies: %#v %v", result, err)
    }
    encoded, _ := json.Marshal(result)
    if !strings.Contains(string(encoded), `"dependencies":[]`) {
        t.Fatalf("expected array rather than null: %s", encoded)
    }
}

func TestMissingPackageAndMalformedMetadata(t *testing.T) {
    for _, test := range []struct{ status int; body string; want int }{
        {http.StatusNotFound, "missing", http.StatusNotFound},
        {http.StatusOK, "not json", http.StatusBadGateway},
        {http.StatusOK, `{"dist-tags":{"latest":"3.0.0"},"versions":{}}`, http.StatusBadGateway},
    } {
        registry := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
            w.WriteHeader(test.status)
            _, _ = w.Write([]byte(test.body))
        }))
        _, status, err := fetchDirect(RegistryRequest{PackageName: "sample", Repository: registry.URL}, &http.Client{Timeout: time.Second})
        registry.Close()
        if err == nil || status != test.want {
            t.Fatalf("expected error and status %d, got %d %v", test.want, status, err)
        }
    }
}

func TestHTTPHandler(t *testing.T) {
    handler := makeHandler(&http.Client{Timeout: time.Second})
    req := httptest.NewRequest(http.MethodPost, "/api/dependencies", strings.NewReader(`{"packageName":"Bad Name","repository":"https://registry.npmjs.org"}`))
    response := httptest.NewRecorder()
    handler.ServeHTTP(response, req)
    if response.Code != http.StatusBadRequest || !strings.Contains(response.Body.String(), "error") {
        t.Fatalf("unexpected response: %d %s", response.Code, response.Body.String())
    }
}
