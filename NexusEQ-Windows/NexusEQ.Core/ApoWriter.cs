namespace NexusEQ.Core;

public enum WriteStatus { Ok, NotInstalled, AccessDenied, Error }

public sealed record WriteResult(WriteStatus Status, string Message);

/// <summary>Escribe nexus_eq.txt en la carpeta config de Equalizer APO y la enlaza desde config.txt.</summary>
public sealed class ApoWriter
{
    public const string FileName = "nexus_eq.txt";
    private const string IncludeLine = "Include: " + FileName;

    public string ConfigDir { get; }

    public ApoWriter(string configDir) => ConfigDir = configDir;

    public static string DefaultConfigDir() =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "EqualizerAPO", "config");

    public bool IsInstalled => Directory.Exists(ConfigDir);

    public WriteResult Write(string content)
    {
        if (!IsInstalled)
            return new WriteResult(WriteStatus.NotInstalled, "Equalizer APO no detectado.");
        try
        {
            File.WriteAllText(Path.Combine(ConfigDir, FileName), content);
            EnsureInclude();
            return new WriteResult(WriteStatus.Ok, "Enlazado con Equalizer APO.");
        }
        catch (UnauthorizedAccessException)
        {
            return new WriteResult(WriteStatus.AccessDenied,
                "Sin permiso para escribir en la carpeta de Equalizer APO. Abre NEXUS.EQ como administrador.");
        }
        catch (Exception ex)
        {
            return new WriteResult(WriteStatus.Error, ex.Message);
        }
    }

    /// <summary>Agrega "Include: nexus_eq.txt" a config.txt una sola vez (idempotente).</summary>
    private void EnsureInclude()
    {
        var main = Path.Combine(ConfigDir, "config.txt");
        var text = File.Exists(main) ? File.ReadAllText(main) : "";
        var already = text.Split('\n')
            .Any(l => l.Trim().Equals(IncludeLine, StringComparison.OrdinalIgnoreCase));
        if (already) return;
        var sep = text.Length == 0 || text.EndsWith('\n') ? "" : Environment.NewLine;
        File.AppendAllText(main, sep + IncludeLine + Environment.NewLine);
    }
}
