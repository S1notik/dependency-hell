using DependencyHell.Versions;

if (args.Length > 0 && args[0] == "--self-test")
    return SelfChecks.Run();
if (args.Length > 0 && args[0] == "--healthcheck")
{
    try
    {
        using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(2) };
        return http.GetAsync("http://127.0.0.1:8083/health").GetAwaiter().GetResult().IsSuccessStatusCode ? 0 : 1;
    }
    catch { return 1; }
}

var builder = WebApplication.CreateBuilder(args);
var app = builder.Build();
app.MapGet("/health", () => Results.Ok(new { status = "UP" }));
app.MapPost("/api/resolve", (ResolveRequest input) =>
{
    try
    {
        if (input.Spec is null || input.Versions is null)
            throw new ArgumentException("Нужны spec и versions");
        var result = VersionResolver.Resolve(input.Spec, input.Versions, input.DistTags ?? new Dictionary<string, string>());
        return Results.Ok(new { version = result });
    }
    catch (ArgumentException error)
    {
        return Results.BadRequest(new { error = error.Message });
    }
});
app.Run("http://0.0.0.0:8083");
return 0;

public record ResolveRequest(string? Spec, List<string>? Versions, Dictionary<string, string>? DistTags);
