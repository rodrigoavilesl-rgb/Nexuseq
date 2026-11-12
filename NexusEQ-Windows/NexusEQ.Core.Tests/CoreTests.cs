using NexusEQ.Core;
using Xunit;

namespace NexusEQ.Core.Tests;

public class EqMathTests
{
    private static EqState State(float[]? gains = null, float preamp = 0f, float bass = 0f, bool auto = true) =>
        new() { Gains = gains ?? new float[Bands.Count], PreampDb = preamp, BassBoost = bass, AutoPreamp = auto };

    [Fact]
    public void Flat_NoAplicaNada()
    {
        var s = State();
        Assert.Equal(0f, EqMath.PeakBoost(s));
        Assert.Equal(0f, EqMath.EffectivePreamp(s));
        Assert.All(EqMath.EffectiveGains(s), g => Assert.Equal(0f, g));
    }

    [Fact]
    public void AutoPreamp_RestaElPicoDeBoost()
    {
        var g = new float[Bands.Count]; g[2] = 6f;
        Assert.Equal(-6f, EqMath.EffectivePreamp(State(g)));
        Assert.Equal(1.5f, EqMath.EffectivePreamp(State(g, preamp: 1.5f, auto: false)));
        Assert.Equal(-4f, EqMath.EffectivePreamp(State(g, preamp: 2f)));
    }

    [Fact]
    public void AutoPreamp_IgnoraCortes()
    {
        var g = Enumerable.Repeat(-5f, Bands.Count).ToArray();
        Assert.Equal(0f, EqMath.PeakBoost(State(g)));
    }

    [Fact]
    public void BassBoost_SoloGraves_YAcotado()
    {
        var eff = EqMath.EffectiveGains(State(bass: 1f));
        Assert.Equal(EqMath.BassMaxDb, eff[0], 3);
        Assert.Equal(0f, eff[4]);
        var max = EqMath.EffectiveGains(State(Enumerable.Repeat(12f, Bands.Count).ToArray(), bass: 1f));
        Assert.Equal(20f, max[0]);
        var min = EqMath.EffectiveGains(State(Enumerable.Repeat(-12f, Bands.Count).ToArray()));
        Assert.Equal(-12f, min[0]);
    }

    [Fact]
    public void CurveAt_PasaPorLosCentros_YSeSatura()
    {
        var g = Enumerable.Range(0, Bands.Count).Select(i => (float)i).ToArray();
        for (int i = 0; i < Bands.Count; i++)
            Assert.Equal(g[i], EqMath.CurveAt(g, Bands.Centers[i]), 3);
        Assert.Equal(g[0], EqMath.CurveAt(g, 20f));
        Assert.Equal(g[^1], EqMath.CurveAt(g, 20000f));
    }

    [Fact]
    public void Presets_TienenDiezBandas_NombresUnicos_Y_EnRango()
    {
        Assert.All(Presets.All, p => Assert.Equal(Bands.Count, p.Gains.Length));
        Assert.Equal(Presets.All.Count, Presets.All.Select(p => p.Name).Distinct().Count());
        Assert.All(Presets.All, p => Assert.All(p.Gains, g => Assert.InRange(g, Bands.MinDb, Bands.MaxDb)));
    }
}

public class ApoTests
{
    [Fact]
    public void Build_Activo_TieneDiezFiltros_PreampYPuntoDecimal()
    {
        var g = new float[Bands.Count]; g[0] = 3.5f;
        var text = ApoConfig.Build(new EqState { Gains = g }, enabled: true);
        Assert.Equal(Bands.Count, text.Split('\n').Count(l => l.StartsWith("Filter ")));
        Assert.Contains("Preamp: -3.5 dB", text);
        Assert.Contains("Filter 1: ON PK Fc 31 Hz Gain 3.5 dB Q 1.41", text);
        Assert.DoesNotContain(",", text.Replace("\r", "").Replace("\n", ""));
    }

    [Fact]
    public void Build_Bypass_NoTieneFiltros()
    {
        var text = ApoConfig.Build(new EqState(), enabled: false);
        Assert.DoesNotContain("Filter", text);
        Assert.DoesNotContain("Preamp", text);
    }

    private static string TempDir()
    {
        var d = Path.Combine(Path.GetTempPath(), "nexuseq_" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(d);
        return d;
    }

    [Fact]
    public void Writer_EscribeArchivo_E_Include_UnaSolaVez()
    {
        var dir = TempDir();
        File.WriteAllText(Path.Combine(dir, "config.txt"), "Preamp: 0 dB");   // sin salto final
        var w = new ApoWriter(dir);
        Assert.Equal(WriteStatus.Ok, w.Write("hola").Status);
        Assert.Equal(WriteStatus.Ok, w.Write("hola2").Status);
        Assert.Equal("hola2", File.ReadAllText(Path.Combine(dir, ApoWriter.FileName)));
        var cfg = File.ReadAllText(Path.Combine(dir, "config.txt"));
        Assert.Equal(1, cfg.Split('\n').Count(l => l.Trim() == "Include: nexus_eq.txt"));
        Assert.StartsWith("Preamp: 0 dB", cfg);
    }

    [Fact]
    public void Writer_SinCarpeta_DiceNoInstalado()
    {
        var w = new ApoWriter(Path.Combine(Path.GetTempPath(), "no_existe_" + Guid.NewGuid().ToString("N")));
        Assert.False(w.IsInstalled);
        Assert.Equal(WriteStatus.NotInstalled, w.Write("x").Status);
    }
}
