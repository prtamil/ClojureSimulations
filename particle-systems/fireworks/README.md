# ASCII Fireworks

A deterministic Clojure particle simulation inspired by
`Graphics/C/AsciiCreativeCoding/particle_systems/fireworks.c`. Fireworks wait,
launch from the bottom of the screen, burst into colored sparks, fade under
gravity, and reload for another launch.

The model is written as a small domain story:

```text
initial-world
  -> validate configuration
  -> start clock
  -> construct show

advance-world
  -> advance fireworks
       waiting  -> wait or launch
       flying   -> fly or burst
       bursting -> show burst or reload
  -> advance clock

project-show
  -> start with an empty canvas
  -> place rockets, exhaust, and sparks
  -> return glyphs in reading order
```

Calculation and presentation are separate. The model stores positions,
velocities, lifetimes, and the explicit random state. `render/glyphs.clj`
projects those values into semantic glyphs shared by the ASCII,
Lanterna, and Quil renderers. Changing a theme therefore changes presentation
without recalculating the show.

See [learning.md](learning.md) for the complete runtime shape and a guided REPL
walkthrough.

## Runtime shape

```clojure
{:config {...}                         ; adjustable rules
 :seed 42
 :random-state 42                     ; next deterministic random value
 :theme-index 0
 :clock {:tick 0
         :elapsed-seconds 0.0
         :paused? false
         :simulation-fps 30}
 :show {:fireworks [...]              ; waiting, flying, or bursting values
        :next-id 6}}
```

`empty-world`, `empty-clock`, and `empty-show` in `model/core.clj` make this
shape visible before any calculation. The firework and spark namespaces define
their own empty values in the same way.

## Source layout

```text
src/fireworks/
|-- model/
|   |-- config.clj       defaults and staged validation
|   |-- spark.clj        create, move, and fade sparks
|   |-- firework.clj     wait, launch, fly, burst, and reload
|   `-- core.clj         construct and advance the world
|-- app/
|   |-- controller.clj   renderer-independent key commands
|   `-- runtime.clj      shared world and renderer lifecycle claims
|-- render/
|   |-- glyphs.clj       domain values to semantic glyphs
|   |-- ascii.clj        pure text snapshots
|   |-- lanterna.clj     native terminal animation
|   `-- quil.clj         graphical glyph-grid animation
|-- terminal/
|   `-- framework.clj    screen lifecycle, input, buffering, and frame loop
`-- utility/
    |-- clock.clj        fixed-step accumulator
    |-- random.clj       deterministic random stream
    `-- theme.clj        palettes and xterm-to-RGB conversion
```

## Run

Run commands from this directory:

```powershell
# Headless ASCII snapshot after 45 simulation steps
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
| `+` / `-` | Add / remove one firework (1–20) |
| `t` / `T` | Next / previous color theme |
| `Space` | Pause / resume |
| `r` | Restart the show from its seed |

## REPL example

```clojure
(require '[fireworks.model.core :as model]
         '[fireworks.render.ascii :as ascii]
         '[fireworks.render.glyphs :as glyphs])

(def start (model/initial-world 42))
(def frame (model/advance-world-steps start 45 (/ 1.0 30.0)))

(get-in frame [:show :fireworks])
(glyphs/project-show frame)
(ascii/render frame)

;; The same seed and steps produce the same complete value.
(= frame
   (model/advance-world-steps (model/initial-world 42)
                              45
                              (/ 1.0 30.0)))
```

The model never calls a renderer, sleeps, prints, or mutates an atom. Expired
sparks leave their firework's active vector. Every renderer consumes the same
glyph projection, including clipping, collision overdraw, bright young sparks,
and dim dying sparks.

## License

MIT; see [LICENSE](LICENSE).
