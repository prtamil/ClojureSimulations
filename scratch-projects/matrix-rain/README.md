# Matrix Rain

A deterministic, Clojure-first falling-code simulation. Independent streams
enter above the field, move at different speeds, mutate glyphs, leave a
variable-length trail, and respawn after clearing the bottom.

## Model story

```text
initial-world -> validate config -> spawn deterministic streams
advance-world -> move + mutate or respawn streams -> advance clock
visible-glyphs -> clip stored trails -> ASCII / terminal / Quil
```

- `sim.model.core` owns construction, fixed-step time, controls, and reset.
- `sim.model.drop` earns a boundary because seeded spawn, mutation probability,
  and respawn form a substantial stream lifecycle with explicit random state.
- `sim.model.projection` earns a small boundary because all three renderers use
  the same visibility and clipping rule. It returns semantic glyphs, not
  renderer-specific cells.
- `sim.model.config` owns adjustable settings and staged validation.
- Glyphs are model data, so rendering the same world is always stable.

Glyph changes are configured as events per second and converted into a
per-step probability. Changing simulation frequency therefore changes
integration granularity rather than the intended mutation rate.

Each stream stores only its head, column, speed, and glyph vector. Trail length
is derived from the glyph vector instead of being duplicated as mutable state.

The persistent world shape is explicit in `empty-world` and `empty-clock`:

```clojure
{:config {...}
 :seed 2026
 :rng-state 12345
 :clock {:tick 0
         :elapsed-seconds 0.0
         :paused? false
         :simulation-fps 60}
 :speed-multiplier 1.0
 :theme-index 0
 :drops [{:id 0 :column 8 :head-y -3.0 :speed 12.5
          :glyphs [\M \A \T \R \I \X]}]}
```

Actual random values depend on the seed; the shape does not. Screen scaling,
colors, and terminal cells remain renderer concerns.

## Run

```powershell
clojure -M:run
clojure -M:terminal
clojure -M:gui
clojure -M:test
clojure -M:dev
clojure -T:build jar
clojure -T:build uber
```

## Controls

| Key | Action |
| --- | --- |
| `Space` | Pause / resume |
| `r` | Reset the seeded rain |
| `+` / `-` | Increase / decrease fall speed |
| `t` / `T` | Next / previous theme |
| `]` / `[` | Raise / lower simulation frequency |
| `q`, `Esc`, or `Ctrl-C` | Quit |

## REPL

```clojure
(require '[sim.model.core :as model]
         '[sim.render.ascii :as ascii])

(def before (model/initial-world 42))
(def after (model/advance-world-steps before 60 (/ 1.0 60.0)))
(:clock after)
(take 3 (:drops after))
(ascii/render after)
(assert (= before (model/initial-world 42)))
```

See `learning.md` for the guided reading order and projection example.
