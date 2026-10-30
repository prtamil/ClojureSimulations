# Basic Clojure Simulation Framework

A deliberately small reference application showing how to structure an
immutable, renderer-independent Clojure simulation. Two particles move at
fixed velocities and wrap around a grid.

## Design

```text
config ---> immutable world ---> particle movement
                  |                    |
                  `------ clock <------'
                           |
                ASCII / terminal / Quil
```

- `sim.model.core` owns the world lifecycle and the small particle motion rule.
- Particles are ordinary maps because they always advance and reset with the
  world; a separate particle subsystem would add navigation without clarity.
- Renderers only project supplied world values.
- `sim.app.runtime` is the single stateful boundary used by interactive views.

The example carries only data it actually uses—there is no placeholder noise,
random permutation, cache, or renderer state in the model.

## Source layout

```text
src/sim/
|-- model/
|   |-- config.clj   adjustable rules and staged validation
|   `-- core.clj     world, clock, particle motion, and controls
|-- render/{ascii,lanterna,quil}.clj
|-- app/{controller,runtime}.clj
|-- terminal/framework.clj
`-- utility/{clock,theme}.clj
```

## Run

```powershell
clojure -M:run       # headless preview
clojure -M:terminal  # interactive terminal
clojure -M:gui       # Quil window
clojure -M:test
clojure -M:dev
clojure -T:build jar
clojure -T:build uber
```

## Controls

| Key | Action |
| --- | --- |
| `Space` | Pause / resume |
| `r` | Reset |
| `t` / `T` | Next / previous theme |
| `]` / `[` | Raise / lower simulation frequency |
| `q`, `Esc`, or `Ctrl-C` | Quit |

## REPL

```clojure
(require '[sim.model.core :as model]
         '[sim.render.ascii :as ascii])

(def before (model/initial-world 42))
(def after (model/advance-world-steps before 60 (/ 1.0 60.0)))
(ascii/render after)

(assert (= before (model/initial-world 42)))
```

See [learning.md](learning.md) for the runtime shape, abstraction rationale,
and a guided REPL walkthrough.
