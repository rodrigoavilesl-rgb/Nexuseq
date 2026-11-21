# WRECK ARENA — vertical slice Android

Demolición arcade top-down en Kotlin nativo (SurfaceView, sin motor externo). Mínimo Android 8.0 (API 26).

## Compilar
```
Abrir la carpeta WreckArena en Android Studio (Koala o superior) → Sync → Run
o desde terminal: ./gradlew :app:assembleDebug   (APK en app/build/outputs/apk/debug/)
```

## Controles (landscape)
- **Izquierda (45%)**: arrastrar para dirigir.
- **GAS** (derecha): acelerar.
- **BRK** (centro-derecha): frenar / reversa cuando estás detenido.
- **NOS**: turbo mientras esté pulsado (consume la barra de NOS).

## Mecánicas
- Daño por zona (frente, atrás, lados). Un panel a 0 se desprende y reduce velocidad (frente) o agarre (atrás).
- Casco 0 = destrucción. Tu destrucción termina la ronda (WRECKED).
- Atropellar peatones: +50 × combo. Destruir IA: +500 × combo y cámara lenta breve.
- Combo x1–x8, se mantiene 4 s entre impactos.
- Pickups: REPAIR (+casco y paneles), NOS (+turbo).
- Rondas de 90 s; el récord (HI) se conserva durante la sesión.

## Estructura
```
app/src/main/java/cl/nexus/wreck/
  Vec.kt        vector 2D y ángulos
  Model.kt      Car, Ped, Pickup, Fx, Input, Palette, Layout (botones)
  World.kt      simulación: física, colisiones, daño, IA, peatones, reglas
  Renderer.kt   mundo neón + HUD telemetría (velocímetro, zonas, combo)
  GameView.kt   loop 60 fps y multitáctil
  MainActivity.kt
```

## Pendiente (siguiente iteración)
- Sonido y háptica (no incluidos).
- Más mapas, modos (campaña, deathmatch por equipos) y garaje con mejoras.
- Multijugador, sistema de logros y guardado persistente.
- Ajuste fino de balance: los valores están en `World.kt` (companion object y constantes de `damage`/`drive`).
