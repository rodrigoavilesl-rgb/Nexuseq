# NEXUS.EQ v1.0 — Ecualizador DSP para Spotify (Android)

App nativa en Kotlin y Jetpack Compose que procesa el audio de Spotify igual que Poweramp Equalizer: se engancha a la sesión de audio que Spotify publica y le aplica `DynamicsProcessing`.

## Cadena DSP
`Preamp (InputGain)` → `EQ 10 bandas (31 Hz–16 kHz, ±12 dB) + Bass boost` → `Limitador (-1 dBFS)`

- **Auto-preamp:** resta el pico de boost para que la señal no sature.
- **Perfiles por salida:** parlante, cable, Bluetooth y USB guardan cada uno su propio ajuste. El cambio es automático al conectar o desconectar un dispositivo.
- **Presets:** FLAT, BASS, METAL, ROCK, ELECTRO, POP, VOCAL, CLÁSICA y RUTA (compensa el ruido en el auto).
- **Modo global (experimental):** aplica el efecto a la sesión 0. Funciona según el fabricante del teléfono.

## Compilar
1. Abre la carpeta `NexusEQ` en Android Studio (Koala o superior).
2. Deja que sincronice Gradle (8.7, AGP 8.5, JDK 17).
3. Conecta el teléfono con depuración USB activada y presiona Run. Para generar el APK: Build › Build APK(s).

Requisito: Android 9 (API 28) o superior.

## Pruebas
`./gradlew :app:testDebugUnitTest` (o desde Android Studio, clic derecho en `app/src/test`). Cubren la matemática de la curva: cortes de banda, auto-preamp, límites del bass boost e interpolación.

## Uso con Spotify
1. Abre NEXUS.EQ una vez. Queda corriendo con una notificación fija.
2. Reproduce algo en Spotify.
3. En Spotify ve a **Configuración › Calidad del audio › Ecualizador** y elige NEXUS.EQ.
4. El tile **ENLACE** cambia a `1 SES.` en verde. El EQ ya está activo.

## Notas de campo
- En Samsung, desactiva Dolby Atmos y Adapt Sound para no apilar dos ecualizadores.
- Si ENLACE queda en ESPERA, pausa y reanuda Spotify. Spotify reenvía la sesión al iniciar la reproducción.
- Excluye la app del ahorro de batería. Si el sistema mata el servicio, se pierde el enganche.
- Los efectos solo se aplican mientras el servicio está vivo. Esto es una limitación de Android y también afecta a Poweramp.

## Estructura
```
app/src/main/java/cl/nexus/eq/
  EqModel.kt     bandas, presets, matemática de curva, estado y persistencia
  EqEngine.kt    DynamicsProcessing por sesión
  EqService.kt   servicio foreground, sesiones, rutas de salida, notificación
  MainActivity.kt
  ui/Theme.kt    paleta neón
  ui/EqScreen.kt telemetría, curva, faders LED, presets y toggles
```

## Fuera del MVP (v1.1+)
Visualizador de espectro (requiere el permiso RECORD_AUDIO), compresor multibanda (la etapa MBC ya está disponible en `DynamicsProcessing`), presets propios con nombre, y AutoEQ por modelo de audífono.
