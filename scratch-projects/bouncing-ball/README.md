# Bouncing Ball

A single-ball elastic-collision simulation built on the repository's reference
architecture. The domain remains deliberately small while demonstrating
time-based motion, radius-aware wall collisions, immutable state, deterministic
seeds, headless verification, and a shared ASCII/Quil runtime.

```text
config/random/noise -> model -> ascii -> repl -> driver -> quil
                     theme -> model/runtime ----------^
                              clock -> runtime --------^
                         controller ------------------^
```

## Responsibilities

| Namespace | Responsibility |
| --- | --- |
| `sim.config` | Validated grid, ball, clock, noise, and render settings |
| `sim.random` | Explicit deterministic SplitMix64 state |
| `sim.noise` | Seeded Perlin/fBm utility for derived simulations |
| `sim.clock` | Pure wall-clock accumulation into fixed simulation steps |
| `sim.model` | Ball state, motion, elastic collision, and controls |
| `sim.ascii` | Pure headless rendering and debugging output |
| `sim.theme` | Renderer-independent palettes and theme cycling |
| `sim.controller` | Pure keyboard-event-to-command translation |
| `sim.runtime` | One shared process-level world atom and GUI lifecycle |
| `driver` | Stable REPL, ASCII, and GUI commands |
| `sim.quil` | Graphical adapter over the shared world |

The model has no Quil dependency. A world is ordinary immutable Clojure data,
and both renderers observe the same value. Seeded RNG/noise state is included
even though the basic ball rule does not require randomness, making this demo a
useful starting point for more complex bouncing simulations.

## Physics

Ball velocity is measured in grid cells per simulated second. The center is
restricted by its radius, so the visible circle never crosses the field edge.
Each axis consumes all collisions within an update, including overshoot and
long batched steps. Collisions are perfectly elastic: the relevant velocity
component changes sign without losing magnitude.

Quil renders at its own configured rate while `sim.clock` earns fixed model
ticks from elapsed nanoseconds. Long stalls are capped to prevent a catch-up
spiral. `model/update-world-steps` is the common stepping boundary used by
Quil, the driver, the REPL examples, and tests.

## REPL-first workflow

The `(comment ...)` guides in `sim.model`, `sim.ascii`, and `repl` begin with
copyable `require` forms. At a development REPL prompt, start with:

```clojure
(require '[sim.model :as model]
         '[sim.ascii :as ascii]
         :reload)

(def world (model/initial-world 42))
(def later (model/update-world-steps world 30 (/ 1.0 60.0)))
(ascii/render later)
```

To run the model tests from that prompt:

```clojure
(require '[clojure.test :as test]
         '[sim.model-test]
         :reload)

(test/run-tests 'sim.model-test)
```

## Custom configuration

Every world carries its validated configuration:

```clojure
(require '[sim.config :as config]
         '[sim.model :as model])

(def small-config
  (-> config/default-config
      (assoc-in [:grid :width] 24)
      (assoc-in [:grid :height] 12)
      (assoc-in [:ball :initial :x] 4.0)
      (assoc-in [:ball :initial :y] 3.0)
      (assoc-in [:ball :initial :speed] 8.0)))

(def small-world (model/initial-world small-config 42))
```

## Run

From this directory:

```powershell
# Headless ASCII preview
clojure -M:run

# Interactive Quil window
clojure -M -e "(require '[driver :as d]) (d/start-gui!)"

# Development nREPL
clojure -M:dev

# Test suite
clojure -M:test

# Build a JAR
clojure -T:build jar
```

## Controls

| Key | Action |
| --- | --- |
| `r` | Reset with the current config and seed |
| `Space` | Pause or resume |
| `t` / `T` | Next or previous color theme |
| `+` / `-` | Double or halve ball speed |
| `]` / `[` | Raise or lower simulation tick rate |
| `q` or `Esc` | Close the Quil window |
