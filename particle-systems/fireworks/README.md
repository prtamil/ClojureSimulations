# ASCII Fireworks

A Clojure-first particle simulation inspired by the visual behavior of
`Graphics/C/AsciiCreativeCoding/particle_systems/fireworks.c`. Rockets rise
from the bottom of the screen, lose upward velocity, burst into multicolored
sparks, drift under gravity, and relaunch after a random delay.

The simulation uses the repository conventions demonstrated by
`artistic/bonsai` and `scratch-projects/basic-clojure-simulation-framework`:
pure immutable model updates, explicit random state, a shared runtime atom,
headless ASCII inspection, a reusable fixed-step terminal loop, and separate
Lanterna and Quil adapters. The implementation is derived from the behavior,
not from the C program's memory layout or control flow.

```text
model/core ----------> ASCII renderer
    |               `-> Lanterna renderer -> terminal/framework
    |               `-> Quil renderer
controller ----------------^       ^
    `------------------------------'
```

## Behavioral goals

- A `waiting -> ascending -> bursting -> waiting` rocket lifecycle.
- Six active rockets at startup, adjustable from 1 to 20.
- 80 sparks per burst and the original launch, drag, gravity, spread, life,
  and fade timing at the default 30 Hz.
- The original ASCII spark alphabet and all ten xterm-256 color themes.
- Fixed-step simulation with a capped 100 ms frame delta.
- Immediate speed, rocket-count, and theme controls.
- Terminal-size simulation coordinates and scene reconstruction after resize.
- A measured terminal FPS readout and HUD drawn over the animation.

The implementation uses seconds and terminal cells as its units. Lifetimes,
launch delays, and gravity therefore remain stable when the simulation tick
rate changes. Randomness is an explicit seeded value stored in the world, so
every headless run and test is reproducible.

## Source layout

```text
src/fireworks/
|-- model/
|   |-- config.clj       units, defaults, and validation
|   |-- spark.clj        burst creation, gravity, and fading
|   |-- rocket.clj       waiting, ascent, and burst transitions
|   `-- core.clj         world stepping and interactive controls
|-- app/
|   |-- controller.clj   renderer-independent key commands
|   `-- runtime.clj      shared world and renderer lifecycle claims
|-- render/
|   |-- ascii.clj        pure text snapshots
|   |-- cells.clj        model-to-character projection and overdraw
|   |-- lanterna.clj     native terminal animation
|   `-- quil.clj         graphical character-cell animation
|-- terminal/
|   `-- framework.clj    screen lifecycle, input, buffering, and frame loop
`-- utility/
    |-- clock.clj        fixed-step accumulator
    |-- random.clj       deterministic random stream
    `-- theme.clj        C palettes and xterm-to-RGB conversion
```

## Run

Run commands from this directory:

```powershell
# Headless ASCII snapshot after 45 simulation ticks
clojure -M:run

# Interactive terminal animation
clojure -M:terminal

# Interactive Quil window
clojure -M:gui

# Tests
clojure -M:test

# Build a library JAR or standalone JAR
clojure -T:build jar
clojure -T:build uber
```

On Windows, `:terminal` enables native console access for resize and key-event
handling. An IDE console without a native terminal falls back to Lanterna's
terminal emulator.

## Controls

| Key | Action |
| --- | --- |
| `q`, `Esc`, `Ctrl-C` | Quit the active renderer |
| `]` / `[` | Raise / lower simulation rate by 5 Hz (10–60 Hz) |
| `+` / `-` | Enable / disable one rocket (1–20) |
| `t` / `T` | Next / previous color theme |
| `Space` | Pause / resume |
| `r` | Restart the show from its seed |

The first four rows reproduce the C controls. Pause and deterministic reset
are small framework-friendly additions shared by the terminal and Quil views.

## REPL examples

```clojure
(require '[fireworks.model.core :as model]
         '[fireworks.render.ascii :as ascii])

(def start (model/initial-world 42))
(def frame (model/update-world-steps start 45 (/ 1.0 30.0)))
(ascii/render frame)

;; The same seed and steps always produce the same complete value.
(= frame
   (model/update-world-steps (model/initial-world 42)
                             45 (/ 1.0 30.0)))
```

## Architecture notes

The model never calls a renderer, sleeps, prints, or mutates an atom. Each
simulation tick receives the world plus a fixed duration and returns the next
world. The world contains only active rockets; waiting rockets carry a delay,
ascending rockets carry position and velocity, and bursting rockets carry a
vector of live sparks. Expired sparks leave that vector instead of remaining
as inactive placeholders.

Lanterna uses the actual terminal columns and rows as logical simulation
coordinates. On resize it rebuilds the scene while preserving theme, speed,
enabled count, and accumulated run information. Quil and the headless renderer
consume the same `visible-cells` projection, including collision overdraw,
bright young sparks, and dim dying sparks.

## License

MIT; see [LICENSE](LICENSE).
