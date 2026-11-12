namespace NexusEQ.Core;

public static class Bands
{
    public const int Count = 10;
    public const float MinDb = -12f;
    public const float MaxDb = 12f;
    public static readonly float[] Centers = { 31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f };
    public static readonly string[] Labels = { "31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K" };
}

/// <summary>Estado de un perfil de ecualización (igual que en la versión Android).</summary>
public sealed record EqState
{
    public float[] Gains { get; init; } = new float[Bands.Count];
    public float PreampDb { get; init; }          // -12..+6
    public float BassBoost { get; init; }         // 0..1
    public bool AutoPreamp { get; init; } = true; // resta el pico de boost
    public string PresetName { get; init; } = "FLAT";
}

public sealed record Preset(string Name, float[] Gains);

public static class Presets
{
    private static Preset P(string name, params float[] g) => new(name, g);

    public static readonly IReadOnlyList<Preset> All = new[]
    {
        P("FLAT",    0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        P("BASS",    6, 5, 4, 2, 0, 0, 0, 0, 0, 0),
        P("METAL",   4, 3, 1, 0, -2, -1, 1, 3, 4, 3),
        P("ROCK",    4, 3, 1, -1, -1, 0, 2, 3, 3, 2),
        P("ELECTRO", 5, 4, 1, 0, -1, 1, 0, 1, 3, 4),
        P("POP",     -1, 1, 3, 4, 3, 0, -1, -1, 0, 1),
        P("VOCAL",   -2, -1, 0, 2, 4, 4, 3, 1, 0, -1),
        P("CLÁSICA", 3, 2, 1, 0, 0, 0, -1, 1, 2, 3),
        P("RUTA",    3, 2, 0, -1, -1, 0, 1, 2, 3, 2),
    };
}

public static class EqMath
{
    /// <summary>Peso del bass boost por banda (low-shelf aproximado).</summary>
    private static readonly float[] BassW = { 1f, 0.75f, 0.4f, 0.1f, 0f, 0f, 0f, 0f, 0f, 0f };
    public const float BassMaxDb = 8f;

    public static float[] EffectiveGains(EqState s)
    {
        var r = new float[Bands.Count];
        for (int i = 0; i < Bands.Count; i++)
            r[i] = Math.Clamp(s.Gains[i] + s.BassBoost * BassMaxDb * BassW[i], -12f, 20f);
        return r;
    }

    public static float PeakBoost(EqState s) => Math.Max(0f, EffectiveGains(s).Max());

    /// <summary>Preamp real: con auto-preamp se descuenta el pico para evitar clipping.</summary>
    public static float EffectivePreamp(EqState s) =>
        s.AutoPreamp ? s.PreampDb - PeakBoost(s) : s.PreampDb;

    /// <summary>Interpolación en escala log (suavizado coseno) para dibujar la respuesta.</summary>
    public static float CurveAt(float[] gains, float freq)
    {
        var c = Bands.Centers;
        if (freq <= c[0]) return gains[0];
        if (freq >= c[^1]) return gains[^1];
        int i = Array.FindIndex(c, x => x >= freq) - 1;
        double t = (Math.Log10(freq) - Math.Log10(c[i])) / (Math.Log10(c[i + 1]) - Math.Log10(c[i]));
        double s = (1 - Math.Cos(t * Math.PI)) / 2.0;
        return (float)(gains[i] + (gains[i + 1] - gains[i]) * s);
    }
}
