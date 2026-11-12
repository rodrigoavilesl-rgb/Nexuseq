using System.Globalization;
using System.Text;

namespace NexusEQ.Core;

/// <summary>Genera la configuración de Equalizer APO a partir de un perfil.</summary>
public static class ApoConfig
{
    public const float Q = 1.41f; // ~1 octava, adecuado para bandas de octava

    public static string Build(EqState s, bool enabled)
    {
        var inv = CultureInfo.InvariantCulture;
        var sb = new StringBuilder();
        sb.AppendLine("# NEXUS.EQ - generado automaticamente - no editar a mano");
        if (!enabled)
        {
            sb.AppendLine("# BYPASS");
            return sb.ToString();
        }

        sb.AppendLine(string.Format(inv, "Preamp: {0:0.0} dB", EqMath.EffectivePreamp(s)));
        var g = EqMath.EffectiveGains(s);
        for (int i = 0; i < Bands.Count; i++)
        {
            sb.AppendLine(string.Format(inv,
                "Filter {0}: ON PK Fc {1:0} Hz Gain {2:0.0} dB Q {3:0.00}",
                i + 1, Bands.Centers[i], g[i], Q));
        }
        return sb.ToString();
    }
}
