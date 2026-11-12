using System.IO;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Shapes;
using System.Windows.Threading;
using NexusEQ.Core;

namespace NexusEQ.Desktop;

public sealed class SavedSettings
{
    public bool Enabled { get; set; } = true;
    public EqState State { get; set; } = new();
}

public partial class MainWindow : Window
{
    private static SolidColorBrush B(string hex) => (SolidColorBrush)new BrushConverter().ConvertFromString(hex)!;
    private static readonly SolidColorBrush Cyan = B("#00F0FF");
    private static readonly SolidColorBrush Magenta = B("#FF2BD6");
    private static readonly SolidColorBrush Lime = B("#B6FF3B");
    private static readonly SolidColorBrush Amber = B("#FFB020");
    private static readonly SolidColorBrush Red = B("#FF3B5C");
    private static readonly SolidColorBrush Dim = B("#6B7A99");
    private static readonly SolidColorBrush LineBrush = B("#1A2335");
    private static readonly SolidColorBrush PanelBrush = B("#0B101B");
    private static readonly SolidColorBrush TextBrush = B("#E6F1FF");

    private static readonly string SettingsPath = System.IO.Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "NexusEQ", "state.json");

    private readonly ApoWriter _apo = new(ApoWriter.DefaultConfigDir());
    private readonly DispatcherTimer _debounce = new() { Interval = TimeSpan.FromMilliseconds(150) };
    private readonly Slider[] _faders = new Slider[Bands.Count];
    private readonly TextBlock[] _faderValues = new TextBlock[Bands.Count];
    private readonly Dictionary<string, Button> _presetButtons = new();

    private EqState _s = new();
    private bool _enabled = true;
    private bool _loading = true;   // true hasta terminar de construir la UI

    public MainWindow()
    {
        InitializeComponent();
        Load();
        BuildFaders();
        BuildPresets();
        _debounce.Tick += (_, _) => { _debounce.Stop(); Apply(); };
        Closing += (_, _) => Save();
        RefreshUi();
        Apply();
    }

    /* ───── Construcción de UI ───── */

    private void BuildFaders()
    {
        for (int i = 0; i < Bands.Count; i++)
        {
            int idx = i;
            var value = new TextBlock
            {
                Text = "0", Foreground = Dim, FontSize = 11,
                HorizontalAlignment = HorizontalAlignment.Center,
            };
            var slider = new Slider
            {
                Orientation = Orientation.Vertical, Minimum = Bands.MinDb, Maximum = Bands.MaxDb,
                TickFrequency = 0.5, IsSnapToTickEnabled = true, Height = 190,
                HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 4, 0, 4),
            };
            slider.ValueChanged += (_, e) => { if (!_loading) OnBand(idx, (float)e.NewValue); };
            slider.MouseDoubleClick += (_, _) => slider.Value = 0;
            var label = new TextBlock
            {
                Text = Bands.Labels[i], Foreground = Dim, FontSize = 11,
                HorizontalAlignment = HorizontalAlignment.Center,
            };

            var col = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center };
            col.Children.Add(value);
            col.Children.Add(slider);
            col.Children.Add(label);
            FaderGrid.Children.Add(col);
            _faders[i] = slider;
            _faderValues[i] = value;
        }
    }

    private void BuildPresets()
    {
        foreach (var p in Presets.All)
        {
            var preset = p;
            var btn = new Button
            {
                Content = p.Name, Margin = new Thickness(0, 0, 8, 8), Padding = new Thickness(14, 7, 14, 7),
                Background = PanelBrush, Foreground = TextBrush, BorderBrush = LineBrush, BorderThickness = new Thickness(1),
            };
            btn.Click += (_, _) => ApplyPreset(preset);
            PresetPanel.Children.Add(btn);
            _presetButtons[p.Name] = btn;
        }
    }

    /* ───── Cambios de estado ───── */

    private void OnBand(int i, float db)
    {
        var g = (float[])_s.Gains.Clone();
        g[i] = db;
        _s = _s with { Gains = g, PresetName = "CUSTOM" };
        Changed();
    }

    private void ApplyPreset(Preset p)
    {
        _s = _s with { Gains = (float[])p.Gains.Clone(), PresetName = p.Name };
        RefreshUi();
        Changed();
    }

    private void PreampSlider_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (_loading) return;
        _s = _s with { PreampDb = (float)e.NewValue };
        Changed();
    }

    private void BassSlider_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (_loading) return;
        _s = _s with { BassBoost = (float)e.NewValue };
        Changed();
    }

    private void AutoPreampCheck_Changed(object sender, RoutedEventArgs e)
    {
        if (_loading) return;
        _s = _s with { AutoPreamp = AutoPreampCheck.IsChecked == true };
        Changed();
    }

    private void PowerBtn_Click(object sender, RoutedEventArgs e)
    {
        _enabled = !_enabled;
        Changed();
    }

    private void CurveCanvas_SizeChanged(object sender, SizeChangedEventArgs e) => DrawCurve();

    private void Changed()
    {
        RefreshLabels();
        DrawCurve();
        _debounce.Stop();
        _debounce.Start();
    }

    /* ───── Aplicar a Equalizer APO ───── */

    private void Apply()
    {
        var result = _apo.Write(ApoConfig.Build(_s, _enabled));
        switch (result.Status)
        {
            case WriteStatus.Ok:
                StatusText.Text = _enabled
                    ? "ENLAZADO · Equalizer APO · " + ApoWriter.FileName
                    : "BYPASS · el ecualizador está desactivado";
                StatusText.Foreground = _enabled ? Lime : Amber;
                break;
            case WriteStatus.NotInstalled:
                StatusText.Text = "EQUALIZER APO NO DETECTADO · instálalo (gratis) desde sourceforge.net/projects/equalizerapo "
                    + "y elige tu dispositivo de salida en el Configurator. Carpeta esperada: " + _apo.ConfigDir;
                StatusText.Foreground = Amber;
                break;
            default:
                StatusText.Text = result.Message;
                StatusText.Foreground = Red;
                break;
        }
    }

    /* ───── Refresco visual ───── */

    private void RefreshUi()
    {
        _loading = true;
        for (int i = 0; i < Bands.Count; i++) _faders[i].Value = _s.Gains[i];
        PreampSlider.Value = _s.PreampDb;
        BassSlider.Value = _s.BassBoost;
        AutoPreampCheck.IsChecked = _s.AutoPreamp;
        _loading = false;
        RefreshLabels();
        DrawCurve();
    }

    private void RefreshLabels()
    {
        for (int i = 0; i < Bands.Count; i++)
        {
            var v = _s.Gains[i];
            _faderValues[i].Text = v == 0 ? "0" : (v > 0 ? "+" : "") + v.ToString("0.#", System.Globalization.CultureInfo.InvariantCulture);
            _faderValues[i].Foreground = v > 0 ? Cyan : v < 0 ? Magenta : Dim;
        }
        PreampText.Text = FormatDb(_s.PreampDb);
        BassText.Text = ((int)Math.Round(_s.BassBoost * 100)) + "%";
        EffPreampText.Text = "Preamp aplicado: " + FormatDb(EqMath.EffectivePreamp(_s))
            + (_s.AutoPreamp ? "  (auto: -" + EqMath.PeakBoost(_s).ToString("0.0", System.Globalization.CultureInfo.InvariantCulture) + " dB de pico)" : "");
        CurveTitle.Text = "RESPUESTA · " + _s.PresetName;
        PowerBtn.Content = _enabled ? "ON" : "OFF";
        PowerBtn.Foreground = _enabled ? Lime : Dim;
        PowerBtn.BorderBrush = _enabled ? Lime : Dim;
        foreach (var kv in _presetButtons)
        {
            bool sel = kv.Key == _s.PresetName;
            kv.Value.BorderBrush = sel ? Cyan : LineBrush;
            kv.Value.Foreground = sel ? Cyan : TextBrush;
        }
    }

    private static string FormatDb(float v) =>
        (v > 0 ? "+" : "") + v.ToString("0.0", System.Globalization.CultureInfo.InvariantCulture) + " dB";

    private void DrawCurve()
    {
        double w = CurveCanvas.ActualWidth, h = CurveCanvas.ActualHeight;
        if (w < 10 || h < 10) return;
        CurveCanvas.Children.Clear();

        const double dbMax = 20, dbMin = -12;
        double Y(double db) => (dbMax - Math.Clamp(db, dbMin, dbMax)) / (dbMax - dbMin) * h;
        double fMin = Math.Log10(20), fMax = Math.Log10(20000);

        foreach (var db in new[] { -12.0, -6.0, 6.0, 12.0, 18.0 })
            CurveCanvas.Children.Add(new Line { X1 = 0, X2 = w, Y1 = Y(db), Y2 = Y(db), Stroke = LineBrush, StrokeThickness = 1, StrokeDashArray = new DoubleCollection { 4, 4 } });
        CurveCanvas.Children.Add(new Line { X1 = 0, X2 = w, Y1 = Y(0), Y2 = Y(0), Stroke = Dim, StrokeThickness = 1 });
        foreach (var f in Bands.Centers)
        {
            double x = (Math.Log10(f) - fMin) / (fMax - fMin) * w;
            CurveCanvas.Children.Add(new Line { X1 = x, X2 = x, Y1 = 0, Y2 = h, Stroke = LineBrush, StrokeThickness = 1, StrokeDashArray = new DoubleCollection { 4, 4 } });
        }

        var gains = EqMath.EffectiveGains(_s);
        var pts = new PointCollection();
        const int steps = 120;
        for (int i = 0; i <= steps; i++)
        {
            double x = w * i / steps;
            double f = Math.Pow(10, fMin + (fMax - fMin) * i / steps);
            pts.Add(new Point(x, Y(EqMath.CurveAt(gains, (float)f))));
        }
        var color = _enabled ? Cyan : Dim;
        CurveCanvas.Children.Add(new Polyline { Points = pts, Stroke = color, StrokeThickness = 8, Opacity = 0.2, StrokeLineJoin = PenLineJoin.Round });
        CurveCanvas.Children.Add(new Polyline { Points = pts, Stroke = color, StrokeThickness = 2, StrokeLineJoin = PenLineJoin.Round });
    }

    /* ───── Persistencia ───── */

    private void Load()
    {
        try
        {
            if (!File.Exists(SettingsPath)) return;
            var saved = JsonSerializer.Deserialize<SavedSettings>(File.ReadAllText(SettingsPath));
            if (saved?.State?.Gains is { Length: Bands.Count })
            {
                _s = saved.State;
                _enabled = saved.Enabled;
            }
        }
        catch { /* archivo dañado: se parte desde cero */ }
    }

    private void Save()
    {
        try
        {
            Directory.CreateDirectory(System.IO.Path.GetDirectoryName(SettingsPath)!);
            File.WriteAllText(SettingsPath, JsonSerializer.Serialize(new SavedSettings { Enabled = _enabled, State = _s }));
        }
        catch { /* no crítico */ }
    }
}
