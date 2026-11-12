# NEXUS.EQ para Windows

Interfaz neón (WPF, .NET 8) para **Equalizer APO**, el ecualizador de sistema gratuito de Windows.
Ecualiza todo el audio del PC: Spotify, Deezer, Apple Music, navegador, etc.

## Instalar
1. Instala [Equalizer APO](https://sourceforge.net/projects/equalizerapo/) y en su Configurator marca tu dispositivo de salida (parlantes/audífonos). Reinicia Windows.
2. Descarga `NexusEQ.exe` (GitHub → Actions → Build Windows → artefacto `NexusEQ-Windows`) y ábrelo.
3. Mueve los faders. Si dice "Sin permiso", ábrelo como administrador (clic derecho → Ejecutar como administrador).

## Cómo funciona
Escribe `nexus_eq.txt` en `C:\Program Files\EqualizerAPO\config` y agrega una vez `Include: nexus_eq.txt` a `config.txt`. Equalizer APO recarga el archivo al instante.
Perfil: 10 bandas de octava (31 Hz-16 kHz, ±12 dB), preamp, bass boost y auto-preamp (igual que la versión Android).

## Límites
- No incluye limitador: el auto-preamp evita el clipping restando el pico de boost.
- Windows 10/11 de 64 bits.
- Es una versión de prueba sin firma: SmartScreen puede avisar ("Más información → Ejecutar de todas formas").

## Probar
`dotnet test NexusEQ-Windows/NexusEQ.Core.Tests`
