using System.Numerics;
using System.Text.RegularExpressions;

namespace DependencyHell.Versions;

// Учебный разбор npm-диапазонов; данные пакетов по сети этот код не получает.
public static class VersionResolver
{
    private const long MaxPart = 9007199254740991;

    private sealed record Sem(long Major, long Minor, long Patch, string? Pre, string Raw) : IComparable<Sem>
    {
        public int CompareTo(Sem? other)
        {
            if (other is null) return 1;
            int n = Major.CompareTo(other.Major);
            if (n != 0) return n;
            n = Minor.CompareTo(other.Minor);
            if (n != 0) return n;
            n = Patch.CompareTo(other.Patch);
            if (n != 0) return n;
            if (Pre is null && other.Pre is null) return 0;
            if (Pre is null) return 1;
            if (other.Pre is null) return -1;
            var left = Pre.Split('.');
            var right = other.Pre.Split('.');
            for (int i = 0; i < Math.Min(left.Length, right.Length); i++)
            {
                bool leftNumber = left[i].All(char.IsAsciiDigit);
                bool rightNumber = right[i].All(char.IsAsciiDigit);
                BigInteger a = leftNumber ? BigInteger.Parse(left[i]) : BigInteger.Zero;
                BigInteger b = rightNumber ? BigInteger.Parse(right[i]) : BigInteger.Zero;
                if (leftNumber && rightNumber) n = a.CompareTo(b);
                else if (leftNumber) n = -1;
                else if (rightNumber) n = 1;
                else n = string.CompareOrdinal(left[i], right[i]);
                if (n != 0) return n;
            }
            return left.Length.CompareTo(right.Length);
        }
    }

    private sealed record Partial(long Major, long Minor, long Patch, int Precision, string? Pre)
    {
        public Sem Lower() => new(Major, Minor, Patch, Pre, "");
        public Sem NextPartial() => Precision switch
        {
            0 => new Sem(MaxPart, 0, 0, null, ""),
            1 => new Sem(Major + 1, 0, 0, null, ""),
            _ => new Sem(Major, Minor + 1, 0, null, "")
        };
    }

    private sealed record Rule(string Op, Sem Bound);

    public static string Resolve(string spec, IReadOnlyList<string> versions, IReadOnlyDictionary<string, string> tags)
    {
        spec = spec.Trim();
        if (tags.TryGetValue(spec, out var tagged))
        {
            if (!versions.Contains(tagged)) throw new ArgumentException("Тег указывает на отсутствующую версию: " + spec);
            return tagged;
        }
        var candidates = versions.Select(TryVersion).Where(v => v is not null).Cast<Sem>().OrderByDescending(v => v).ThenByDescending(v => v.Raw, StringComparer.Ordinal).ToList();
        var groups = spec.Split("||", StringSplitOptions.None).Select(ParseGroup).ToList();
        foreach (var version in candidates)
        {
            foreach (var group in groups)
            {
                if (Matches(version, group)) return version.Raw;
            }
        }
        throw new ArgumentException("Нет опубликованной версии, подходящей под диапазон: " + spec);
    }

    private static Sem? TryVersion(string raw)
    {
        try
        {
            var p = ParsePartial(raw);
            return p.Precision == 3 ? new Sem(p.Major, p.Minor, p.Patch, p.Pre, raw) : null;
        }
        catch (ArgumentException) { return null; }
    }

    private static Partial ParsePartial(string text)
    {
        var match = Regex.Match(text.Trim(), @"^[vV]?(?<core>(?:0|[1-9]\d*|[xX*])(?:\.(?:0|[1-9]\d*|[xX*])){0,2})(?:-(?<pre>[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$");
        if (!match.Success) throw new ArgumentException("Неподдерживаемая версия или диапазон: " + text);
        string[] parts = match.Groups["core"].Value.Split('.');
        long[] values = { 0, 0, 0 };
        int precision = 0;
        bool wildcard = false;
        for (int i = 0; i < parts.Length; i++)
        {
            if (parts[i] == "*" || parts[i].Equals("x", StringComparison.OrdinalIgnoreCase)) { wildcard = true; continue; }
            if (wildcard || !long.TryParse(parts[i], out values[i]) || values[i] > MaxPart)
                throw new ArgumentException("Неверная версия: " + text);
            precision++;
        }
        string? pre = match.Groups["pre"].Success ? match.Groups["pre"].Value : null;
        if (pre is not null && precision != 3) throw new ArgumentException("Prerelease допустим только с полной версией: " + text);
        if (pre is not null && pre.Split('.').Any(part => part.Length > 1 && part[0] == '0' && part.All(char.IsDigit)))
            throw new ArgumentException("Неверная prerelease-версия: " + text);
        return new Partial(values[0], values[1], values[2], precision, pre);
    }

    private static List<Rule> ParseGroup(string group)
    {
        group = group.Trim();
        if (group.Length == 0 || group == "*") return new List<Rule>();
        var hyphen = Regex.Match(group, @"^(\S+)\s+-\s+(\S+)$");
        if (hyphen.Success)
        {
            var from = ParsePartial(hyphen.Groups[1].Value);
            var to = ParsePartial(hyphen.Groups[2].Value);
            var result = new List<Rule>();
            if (from.Precision > 0) result.Add(new Rule(">=", from.Lower()));
            if (to.Precision == 3) result.Add(new Rule("<=", to.Lower()));
            else if (to.Precision > 0) result.Add(new Rule("<", to.NextPartial()));
            return result;
        }
        group = Regex.Replace(group, @"(<=|>=|<|>|=|\^|~>?)\s+", "$1");
        var rules = new List<Rule>();
        foreach (string token in group.Split((char[]?)null, StringSplitOptions.RemoveEmptyEntries))
        {
            var match = Regex.Match(token, @"^(?<op>\^|~>|~|<=|>=|<|>|=)?(?<version>.+)$");
            if (!match.Success) throw new ArgumentException("Неверный диапазон: " + token);
            string op = match.Groups["op"].Value;
            var part = ParsePartial(match.Groups["version"].Value);
            if (part.Precision == 0)
            {
                if (op is ">" or "<") rules.Add(new Rule("never", part.Lower()));
                continue;
            }
            if (op == "^")
            {
                rules.Add(new Rule(">=", part.Lower()));
                Sem upper = part.Major > 0 || part.Precision == 1 ? new Sem(part.Major + 1, 0, 0, null, "") :
                    part.Minor > 0 || part.Precision == 2 ? new Sem(0, part.Minor + 1, 0, null, "") : new Sem(0, 0, part.Patch + 1, null, "");
                rules.Add(new Rule("<", upper));
            }
            else if (op is "~" or "~>")
            {
                rules.Add(new Rule(">=", part.Lower()));
                Sem upper = part.Precision == 1 ? new Sem(part.Major + 1, 0, 0, null, "") : new Sem(part.Major, part.Minor + 1, 0, null, "");
                rules.Add(new Rule("<", upper));
            }
            else if ((op == "" || op == "=") && part.Precision < 3)
            {
                rules.Add(new Rule(">=", part.Lower()));
                rules.Add(new Rule("<", part.NextPartial()));
            }
            else if (op == ">" && part.Precision < 3) rules.Add(new Rule(">=", part.NextPartial()));
            else if (op == "<=" && part.Precision < 3) rules.Add(new Rule("<", part.NextPartial()));
            else rules.Add(new Rule(op == "" ? "=" : op, part.Lower()));
        }
        return rules;
    }

    private static bool Matches(Sem candidate, List<Rule> rules)
    {
        if (candidate.Pre is not null && !rules.Any(r => r.Bound.Pre is not null && r.Bound.Major == candidate.Major && r.Bound.Minor == candidate.Minor && r.Bound.Patch == candidate.Patch)) return false;
        foreach (var rule in rules)
        {
            int n = candidate.CompareTo(rule.Bound);
            bool ok = rule.Op switch { "=" => n == 0, ">" => n > 0, ">=" => n >= 0, "<" => n < 0, "<=" => n <= 0, _ => false };
            if (!ok) return false;
        }
        return true;
    }
}
