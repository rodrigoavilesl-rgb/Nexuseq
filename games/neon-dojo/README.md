# NEON DOJO — karate arcade para Android

Juego de pelea 1 contra 1 por puntos, estilo arcade de los 80 con estética synthwave/neón. HTML5 + Canvas + Web Audio en un solo archivo. No usa assets externos: los sprites, escenarios, música y efectos se generan por código.

## Jugar en Android
- **PWA (recomendado):** publica la carpeta en cualquier hosting HTTPS (GitHub Pages, por ejemplo), ábrela en Chrome y elige *Agregar a pantalla principal*. Se abre a pantalla completa en horizontal y funciona offline gracias a `sw.js`.
- **APK:** envuelve `index.html` con Capacitor (`npx cap init`, copia la carpeta a `www/` y ejecuta `npx cap add android`) o con un `WebView` mínimo.

## Controles
| Táctil | Teclado | Acción |
|---|---|---|
| Palanca izquierda (flotante) | Flechas / WASD | mover · ↑ saltar · ↓ agacharse |
| PUÑO | J / Z | golpe de puño |
| PATADA | K / X | patada |
| GUARDIA (mantener) | L / C | bloqueo alto/medio; agachado bloquea bajo/medio |
| KI (barra llena) | I / V | Ki-ken, golpe imparable |

La dirección combinada con el botón elige la técnica. → significa hacia el rival.

| Entrada | Técnica | Altura | Valor |
|---|---|---|---|
| PUÑO | Gyaku-zuki | medio | ½ |
| ← PUÑO | Uraken | alto | ½ |
| ↓ PUÑO | Gedan-zuki | medio | ½ |
| PATADA | Mae-geri | medio | ½ |
| → PATADA | Mawashi-geri | alto | 1 |
| ← PATADA | Yoko-geri | medio | 1 |
| ↓ PATADA | Ashi-barai (derriba) | bajo | 1 |
| aire + PATADA | Tobi-geri | alto | 1 |
| aire + PUÑO | Tobi-zuki | alto | ½ |
| KI | Ki-ken | imparable | 1 |

## Reglas
- Un golpe limpio detiene el intercambio y da IPPON (1 punto) o WAZA-ARI (½ punto). Golpear al rival mientras ataca suma ½ extra (contra).
- Con 2 puntos ganas el round y con 2 rounds ganas el combate. Cada round dura 30 s.
- Agacharte esquiva los golpes altos, saltar esquiva el barrido y el KI no se puede bloquear.
- Modo historia con 4 rivales y dificultad creciente: TORA, KAZE, MIZU y OMEGA. Después de los rivales 1 y 3 hay un bonus de rompimiento de tablas (tameshiwari) que se juega por timing.

## Arquitectura (`index.html`)
- **Bucle:** la lógica corre a 60 Hz con paso fijo. El render dibuja en un canvas de 480×270 que luego se escala con píxel duro y recibe bloom, scanlines y viñeta (filtro CRT, se puede desactivar).
- **Luchador:** esqueleto por ángulos con cinemática directa. Las poses se interpolan y los hitboxes salen de la posición real del puño o pie, así que lo que ves coincide con lo que golpea.
- **IA:** usa la misma interfaz de entrada que el jugador. Ajusta tiempo de reacción, bloqueo, castigo y agresividad según el rival y la dificultad.
- **Audio:** secuenciador synthwave con bajo, arpegio, pad, lead y batería, con un tema distinto por escenario. Los efectos son sintetizados (incluido un kiai con formantes) y el árbitro habla con `speechSynthesis`.
- **Persistencia:** el récord y los ajustes se guardan en `localStorage`.
