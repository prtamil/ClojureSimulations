# Basic Clojure Simulation Framework

This is the repository's reference structure for future simulations. The
example stays deliberately small: two particles move at fixed velocities and
wrap around a grid. The surrounding architecture demonstrates how a larger
simulation should separate pure rules, time, configuration, runtime state, and
rendering.

```text
config/random/noise -> model -> ascii -> repl -> driver -> quil
                     theme -> model/runtime ----------^
                              clock -> runtime --------^
                         controller ------------------^
```

## Responsibilities

| Namespace | Responsibility |
| --- | --- |
| `sim.config` | Defaults, dimensions, renderer settings, and validation |
| `sim.random` | Deterministic SplitMix64 state and shuffled permutations |
| `sim.noise` | Seeded Perlin noise and fBm sampling for models that need it |
| `sim.theme` | Renderer-independent palettes and cyclic theme selection |
| `sim.clock` | Pure wall-clock accumulation into fixed simulation steps |
| `sim.model` | Immutable world construction and update rules |
| `sim.ascii` | Headless rendering for inspection and tests |
| `sim.controller` | Pure keyboard-event-to-command translation |
| `sim.runtime` | The one shared process-level world atom and GUI lifecycle |
| `driver` | Stable REPL/headless commands |
| `sim.quil` | Graphical adapter over the same runtime world |

The model does not depend on Quil. Both ASCII and Quil render the same world,
and both REPL commands and GUI controls update `sim.runtime/world`. Random state
and the noise permutation are stored explicitly in the world, so seeded runs
are reproducible. Theme selection is also world data, so headless tools, REPL
commands, and the GUI always agree about the active palette.

## Configuration

Every world carries the validated configuration used to create it. Begin with
`sim.config/default-config` and change only the settings needed by an
experiment:

```clojure
(require '[sim.config :as config]
         '[sim.model :as model])

(def small-config
  (-> config/default-config
      (assoc-in [:grid :width] 24)
      (assoc-in [:grid :height] 12)
      (assoc-in [:particles :initial]
                [{:id 1 :x 2.0 :y 3.0 :vx 4.0 :vy 2.0}])))

(def world (model/initial-world small-config 42))
```

The seed creates both an explicit random stream and a doubled permutation for
coherent noise. The basic particle rule does not require noise, but the
facility is ready for simulations built from this reference.

## Fixed-step runtime

Quil renders independently at the configured render rate. `sim.clock`
accumulates elapsed nanoseconds and tells the model how many fixed simulation
ticks were earned. Long frame stalls are capped, preventing an uncontrolled
catch-up burst. `model/update-world-steps` is also the common stepping boundary
for the GUI, driver, REPL, and tests.

## Run

From this directory:

```powershell
# Headless ASCII preview
clojure -M:run

# Interactive Quil window
clojure -M -e "(require '[driver :as d]) (d/start-gui!)"

# CIDER/nREPL session
clojure -M:dev

# Test suite
clojure -M:test

# Build a JAR
clojure -T:build jar
```

## Controls

| Key | Action |
| --- | --- |
| `r` | Reset the model with its current config and seed |
| `Space` | Pause or resume |
| `t` / `T` | Next or previous color theme |
| `]` / `[` | Raise or lower the simulation rate |
| `q` or `Esc` | Close the Quil window |
