# Reglas del proyecto

## Entregables de juegos y apps visuales

Todo juego o app visual que se genere con Claude Code debe incluir:

1. **Versión web jugable o visualizable**, en un solo archivo HTML, publicada como Artifact del usuario. Así se puede ver con un clic, sin instalar nada, igual que los demás juegos del usuario.
2. **Build nativo** cuando aplique (APK para Android, JAR para escritorio), además de la versión web.

La versión web es obligatoria aunque el usuario no la pida. Si no es viable, decirlo de forma explícita en la respuesta, con el motivo.

Reglas para mantener la paridad:

- La versión web replica la lógica del core (física, daño, IA, reglas) con las mismas constantes. Si cambias una constante o regla en el core, cámbiala también en la versión web y republícala.
- Publicar el Artifact con el mismo `file_path` para actualizar el mismo enlace.
- Verificar que el HTML no tenga errores de sintaxis ni de ejecución antes de publicar (`node --check` sobre el script y una carga en Chromium headless).

## Estructura de proyectos con core compartido

Cuando la lógica es pura (sin Android ni Swing), va en un módulo `core/` sin dependencias de plataforma. Cada plataforma (app Android, escritorio, web) solo aporta render y entrada.
