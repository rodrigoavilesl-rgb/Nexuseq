# NEXUS.EQ Car — plan y estado (guardado 2026-11-12)

## Estado actual
- NEXUS.EQ v1.0 funciona con Spotify en el teléfono (probado por el autor).
- Rama `claude/proyectos-cowork-equalizador-09o4lr`: revisión + 4 correcciones + 8 tests de `EqMath`
  + workflow `Build APK` (GitHub Actions) que compila OK. APK en Actions → artefacto `NexusEQ-debug-apk`.
- Existe otra rama `claude/new-session-b41mh1` con una versión distinta del proyecto (también compila).
  Pendiente: comparar ambas y decidir con cuál seguir / cómo unirlas.

## Idea de producto
App Android **sin hardware** para quien escucha música en el auto con el celular
(Bluetooth/AUX/USB). Diferenciador: estética de telemetría + analizador del habitáculo.

### Hallazgos de mercado (2 búsquedas, solo EE.UU., sin datos de Google Play ni Chile)
- Las apps de audio de auto son sobre todo controles de DSP de una marca (MUSWAY, MTX ThunderTune,
  ESX, Kenwood, Taramps) y apps "blanca" para placas DSP chinas; mayormente con ficha en iOS.
- Hay indicios de que las versiones Android son más débiles que las de iOS (sin confirmar).
- Hueco probable: app Android buena, independiente de hardware, para música desde el celular.

### Funciones propuestas (v1.1+)
1. Analizador RTA con micrófono del celular + sugerencia de EQ para el habitáculo (permiso RECORD_AUDIO).
2. Modo conducción: pantalla grande, legible de reojo, tiles de telemetría.
3. Perfiles por auto, activados por el Bluetooth de cada vehículo.
4. Modelo: gratis (EQ básico) + Pro (analizador, perfiles ilimitados, presets), pago único o suscripción baja.

### Riesgos
- Depende de que Spotify siga publicando la sesión de audio; competencia fuerte (Poweramp, Wavelet).
- Google Play: justificar el servicio foreground `specialUse` y la política de audio.
- Precisión limitada del micrófono del celular: comunicar como "sugerencia", no calibración profesional.

## Validación antes de invertir meses
Preguntar a 5 instaladores o a grupos de audio car de Chile: "¿pagarías por una app que mide mi auto y
arma el EQ?". Si 2-3 dicen que sí, seguir.

## Pendientes técnicos (de la revisión)
- Detectar la ruta por salida activa, no por dispositivos conectados.
- Ícono de la app, `gradlew`/wrapper, exclusión de batería desde la app, `allowBackup=false`.
- Probar en teléfono: ENLACE `1 SES.`, cambio audible con BASS, cambio de perfil al conectar audífonos.

## Preguntas abiertas
- ¿Con qué equipo de auto se prueba (radio Android, DSP aparte, de fábrica)?
- ¿Público general o instaladores como canal de venta?
- Segunda línea de trabajo, separada: suite TITAN / telemetría operacional (herramientas de gestión).
  Meta propuesta: 30 días = 1 producto vendible; 90 días = 3 pilotos; 6 meses = primeros ingresos recurrentes.
