# Curl Noise Vector Field

A Clojure-first exploration of curl noise: particles follow the divergence-free
velocity field of a scalar potential, leave fading trails, and can be viewed
through several live interpretations of the same field.

The visual idea is shared with `curl_noise_vector_field.c`, but this project is
a fresh functional design rather than a structural port. It keeps the original
experience—flowing particles, warped flow, vector arrows, scalar potential,
curl magnitude, color themes, and interactive controls—while expressing the
simulation as small immutable transformations.

## Model

The simulation is built from four concepts:

```text
seed ---> scalar field ---> curl direction ---> particle positions
               |                                  |
               |                                  v
               `---> sampled static cells      sparse trails
                              \                 /
                               semantic cells
                                     |
                          ASCII / Lanterna / Quil
```

- `field.clj` owns field time, potential sampling, curl directions, and static
  grid views.
- `particle.clj` creates particles and advances them using elapsed seconds.
- `trail.clj` fades and deposits sparse semantic cells.
- `core.clj` composes those operations and owns controls and lifecycle.

Particle lifetimes are measured in seconds rather than update counts. Changing
the simulation frequency therefore changes numerical granularity, not the
intended lifetime or speed.

The world contains ordinary Clojure values:

```clojure
{:field {:time 0.0 :permutation [...]}
 :particles [{:position [x y]
              :band 2
              :age-seconds 0.4
              :lifetime-seconds 3.7}]
 :cells {[x y] {:position [x y]
                :intensity 0.82
                :band 2}}
 ...}
```

There are no parallel glow/color arrays, mutable particle pools, buffer-pattern
flags, or duplicated fast paths. A particle step reads naturally:

```text
advance field → fade trails → move particles → deposit new trail cells
```

Static views advance by the same elapsed time and sample the final field once
per clock batch.

## Source layout

```text
src/sim/
|-- model/
|   |-- config.clj      parameters and validation
|   |-- field.clj       potential, curl, and sampled field cells
|   |-- particle.clj    elapsed-time particles
|   |-- trail.clj       sparse fading trails
|   `-- core.clj        world composition and controls
|-- render/
|   |-- ascii.clj       pure headless renderer
|   |-- lanterna.clj    interactive terminal adapter
|   `-- quil.clj        interactive graphical adapter
|-- terminal/
|   `-- framework.clj   reusable terminal lifecycle and fixed-step loop
|-- app/
|   |-- controller.clj  key events to data commands
|   `-- runtime.clj     shared runtime atom
`-- utility/
    |-- clock.clj       fixed-step timing
    |-- noise.clj       Perlin, fBm, warping, and curl mathematics
    |-- random.clj      explicit deterministic random streams
    `-- theme.clj       palettes
```

Only the application boundary is stateful. The world stored in the runtime
atom remains immutable and deterministic for a given seed.

## Run

From `procedural/fields/curl-noise-vector-field`:

```powershell
# Headless preview
clojure -M:run

# Interactive terminal
clojure -M:terminal

# Interactive Quil window
clojure -M:gui

# Tests
clojure -M:test

# CIDER / nREPL
clojure -M:dev

# JARs
clojure -T:build jar
clojure -T:build uber
```

## Views

| View | Meaning |
| --- | --- |
| `PARTICLES` | Particles follow the ordinary curl field |
| `VECTOR` | Sampled curl directions on a lattice |
| `POTENTIAL` | Scalar-potential density |
| `CURL MAG` | Curl-speed magnitude |
| `WARPED` | Particles follow a domain-warped potential |

All views are interpretations of the same seeded, drifting field.

## Controls

| Key | Action |
| --- | --- |
| `n` / `p` | Next / previous view |
| `t` / `T` | Next / previous color theme |
| `r` | Reseed and reset |
| `Space` | Pause / resume |
| `+` / `-` | Double / halve particle speed and field drift |
| `]` / `[` | Raise / lower simulation frequency |
| `q`, `Esc`, or `Ctrl-C` | Close the active renderer |

## REPL example

```clojure
(require '[sim.model.core :as model]
         '[sim.render.ascii :as ascii])

(def before (model/initial-world 42))
(def after
  (model/update-world-steps before 60 (/ 1.0 60.0)))

(ascii/render after)
(model/field-time after)
(take 3 (:particles after))
(take 3 (:cells after))

;; Pure, seeded construction and evolution are repeatable.
(assert (= before (model/initial-world 42)))
```
