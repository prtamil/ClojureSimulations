# Bouncing Multiple Balls

A deterministic multi-body simulation in which circular balls bounce from the
field boundary and collide elastically with one another. It follows the same
REPL-first architecture as the basic framework and curl-noise project.

```text
config/random/noise -> model -> ascii -> repl -> driver -> quil
                     theme -> model/runtime ----------^
                              clock -> runtime --------^
                         controller ------------------^
```

## Responsibilities

| Namespace | Responsibility |
| --- | --- |
| `sim.config` | Validated world, spawning, physics, clock, and render settings |
| `sim.random` | Explicit SplitMix64 state, integers, doubles, and permutations |
| `sim.noise` | Seeded Perlin/fBm utility for derived simulations |
| `sim.clock` | Pure wall-clock accumulation into fixed model ticks |
| `sim.model` | Spawning, wall motion, circle collision, and world updates |
| `sim.ascii` | Pure headless rendering (`o` one ball, `*` shared preview cell) |
| `sim.theme` | Renderer-independent palettes |
| `sim.controller` | Pure keyboard commands |
| `sim.runtime` | One shared process-level world atom and GUI lifecycle |
| `driver` | Stable REPL, ASCII-animation, and GUI commands |
| `sim.quil` | Graphical adapter over the shared world |

## Physics model

Initial balls are placed deterministically from a seed with no overlaps. Each
ball carries position, velocity, radius, mass, and palette index. Mass is
proportional to radius squared.

Every fixed tick is divided into configurable substeps to reduce collision
tunneling. A substep:

1. Advances every ball and consumes all wall overshoot.
2. Finds each unique ball pair.
3. Separates overlapping circles according to inverse mass.
4. Applies a normal impulse only when the pair is approaching.
5. Repeats the collision solver and confines corrected balls to the field.

The default restitution is `1.0`, so isolated collisions conserve momentum and
kinetic energy apart from floating-point rounding. This remains a small
educational solver rather than a full continuous-collision physics engine.

## REPL-first workflow

The comment guides in `sim.model`, `sim.ascii`, and `repl` begin with copyable
`require` forms. From a development REPL prompt:

```clojure
(require '[sim.model :as model]
         '[sim.ascii :as ascii]
         :reload)

(def world (model/initial-world 42))
(def later (model/update-world-steps world 60 (/ 1.0 60.0)))
(ascii/render later)
```

Run the model tests from the same development REPL:

```clojure
(require '[clojure.test :as test]
         '[sim.model-test]
         :reload)

(test/run-tests 'sim.model-test)
```

## Configuration

Every world owns the validated configuration that created it:

```clojure
(require '[sim.config :as config]
         '[sim.model :as model])

(def small-config
  (-> config/default-config
      (assoc-in [:grid :width] 24)
      (assoc-in [:grid :height] 12)
      (assoc-in [:balls :count] 6)
      (assoc-in [:balls :radius :max] 0.6)))

(def small-world (model/initial-world small-config 42))
```

Dense configurations may be impossible to place without overlap. In that case,
increase the grid, reduce count/radius, or increase `:balls :spawn-attempts`.

## Run

```powershell
# Headless preview
clojure -M:run

# Quil window
clojure -M -e "(require '[driver :as d]) (d/start-gui!)"

# Development nREPL
clojure -M:dev

# Tests
clojure -M:test

# JAR
clojure -T:build jar
```

## Controls

| Key | Action |
| --- | --- |
| `r` | Reset using the current config and seed |
| `Space` | Pause or resume |
| `t` / `T` | Next or previous theme |
| `+` / `-` | Double or halve motion speed |
| `]` / `[` | Raise or lower simulation tick rate |
| `q` or `Esc` | Close the Quil window |
