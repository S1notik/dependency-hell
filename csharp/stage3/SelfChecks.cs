namespace DependencyHell.Versions;

public static class SelfChecks
{
    public static int Run()
    {
        try
        {
            Check("^1.2.3", "1.9.0", "1.2.2", "1.2.3", "1.9.0", "2.0.0");
            Check("^0.2.3", "0.2.9", "0.2.3", "0.2.9", "0.3.0");
            Check("^0.0.3", "0.0.3", "0.0.3", "0.0.4");
            Check("^0.0.x", "0.0.9", "0.0.9", "0.1.0");
            Check("~1.2.3", "1.2.9", "1.2.3", "1.2.9", "1.3.0");
            Check("1.2.x", "1.2.9", "1.2.0", "1.2.9", "1.3.0");
            Check(">=1.2 <2", "1.9.0", "1.1.9", "1.2.0", "1.9.0", "2.0.0");
            Check(">1.2", "1.3.0", "1.2.99", "1.3.0");
            Check("1.2.3 - 2.3", "2.3.9", "1.2.3", "2.3.9", "2.4.0");
            Check("^1.0.0 || ~3.1.0", "3.1.9", "1.9.0", "3.1.9", "3.2.0");
            Check("*", "1.0.0", "1.0.0", "2.0.0-beta.1");
            Check(">=2.0.0-beta.1 <2.0.0", "2.0.0-beta.2", "2.0.0-beta.1", "2.0.0-beta.2", "2.0.0");
            Check("1.0.0+abc", "1.0.0", "1.0.0", "1.0.1");
            string tag = VersionResolver.Resolve("next", new[] { "1.0.0", "2.0.0-beta.1" }, new Dictionary<string, string> { ["next"] = "2.0.0-beta.1" });
            if (tag != "2.0.0-beta.1") throw new Exception("Ошибка разрешения тега");
            ExpectFailure("^1.0.0", "2.0.0");
            ExpectFailure("git+https://example.com/repo", "1.0.0");
            Console.WriteLine("PASS: проверки C# semver и тегов");
            return 0;
        }
        catch (Exception error)
        {
            Console.Error.WriteLine("FAIL: " + error.Message);
            return 1;
        }
    }

    private static void Check(string spec, string expected, params string[] versions)
    {
        string got = VersionResolver.Resolve(spec, versions, new Dictionary<string, string>());
        if (got != expected) throw new Exception($"{spec}: ожидалось {expected}, получено {got}");
    }

    private static void ExpectFailure(string spec, params string[] versions)
    {
        try { VersionResolver.Resolve(spec, versions, new Dictionary<string, string>()); }
        catch (ArgumentException) { return; }
        throw new Exception("Ожидалась ошибка для " + spec);
    }
}
