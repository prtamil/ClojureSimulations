# Matrix Rain

A deterministic, Clojure-first falling-code simulation. Independent streams
enter above the field, move at different speeds, mutate glyphs, leave a
variable-length trail, and respawn after clearing the bottom.

## Design

```text
seed ---> streams ---> move + mutate + respawn ---> immutable world
                                                    |
                                         ASCII / terminal / Quil
```

- `sim.model.drop` owns seeded stream creation and evolution.
- `sim.model.core` composes fixed steps, lifecycle, and controls.
- Glyphs are model data, so rendering the same world is always stable.

Glyph changes are configured as events per second and converted into a
per-step probability. Changing simulation frequency therefore changes
integration granularity rather than the intended mutation rate.

Each stream stores only its head, column, speed, and glyph vector. Trail length
is derived from the glyph vector instead of being duplicated as mutable state.

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
(def after (model/update-world-steps before 60 (/ 1.0 60.0)))
(take 3 (:drops after))
(ascii/render after)
(assert (= before (model/initial-world 42)))
```
