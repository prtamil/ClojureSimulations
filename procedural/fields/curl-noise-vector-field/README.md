# Curl Noise Vector Field

A deterministic Clojure exploration of curl noise. Particles follow the
divergence-free velocity field of a scalar potential, leave fading trails, and
can be viewed through several live interpretations of the same field.

The visual idea is shared with `curl_noise_vector_field.c`, but the model is a
functional design built from immutable values and small domain operations.

## Model story

The top-level workflow is deliberately direct:

```text
initial-world
  -> validate configuration
  -> start the clock and controls
  -> shuffle a field permutation
  -> spawn particles
  -> calculate the selected static view, when needed

advance particle view
  -> advance clock, field, and flash
  -> move or respawn particles
  -> fade the trail
  -> deposit new particle hits

advance static view
  -> advance clock, field, and flash for the complete batch
  -> sample the selected field view once

project-world
  -> select the visible trail or static samples
  -> expose only visible particle data
  -> return one renderer-independent scene
```

The world contains calculation data, not terminal characters or Quil drawing
commands:

```clojure
{:config {...}
 :random-state 123456789                  ; next deterministic random value
 :clock {:tick 0
         :paused? false
         :simulation-fps 60}
 :field {:time 0.0
         :permutation [...]}
 :particles [{:position [x y]
              :band 2
              :age-seconds 0.4
              :lifetime-seconds 3.7}]
 :trail {[x y] {:position [x y]
                :intensity 0.82
                :band 2}}
 :view {:pattern :particles
        :samples {}}
 :particle-speed 8
 :drift-multiplier 1
 :theme-index 0
 :flash-intensity 1.0}
```

`empty-world`, `empty-clock`, and `empty-view` in `model/core.clj` document the
complete persistent runtime shape. Individual particle, trail, and field sample
shapes are documented beside the functions that create them.

## Calculation and projection

The model uses precise names for different concepts:

- `:trail` is calculated history left by moving particles.
- `[:view :samples]` contains calculated scalar or vector field samples for a
  static view.
- `render/projection.clj` selects those values into a visible `scene`.
- ASCII, Lanterna, and Quil decide how to draw that scene.

The former `:cells` name mixed trails, field samples, and terminal cells. The
new names keep those meanings separate. Actual terminal cells and Quil pixel
cell sizes still use the word `cell` where it is accurate.

Particle lifetimes are measured in seconds rather than update counts. Changing
the simulation frequency changes numerical granularity, not the intended
lifetime or speed.

## Source layout

```text
src/sim/
|-- model/
|   |-- config.clj       parameters and staged validation
|   |-- field.clj        potential, curl, and sampled field values
|   `-- core.clj         world, particles, trails, advancement, and controls
|-- render/
|   |-- projection.clj   model values to a shared visible scene
|   |-- ascii.clj        pure headless renderer
|   |-- lanterna.clj     interactive terminal adapter
|   `-- quil.clj         interactive graphical adapter
|-- terminal/
|   `-- framework.clj    terminal lifecycle and fixed-step loop
|-- app/
|   |-- controller.clj   key events to data commands
|   `-- runtime.clj      shared runtime atom
`-- utility/
    |-- clock.clj        fixed-step timing
    |-- noise.clj        Perlin, fBm, warping, and curl mathematics
    |-- random.clj       explicit deterministic random streams
    `-- theme.clj        palettes
```

Only the application boundary is stateful. A seeded world and its evolution
remain structurally repeatable.

See [learning.md](learning.md) for a guided reading order and REPL walkthrough.

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
         '[sim.render.ascii :as ascii]
         '[sim.render.projection :as projection])

(def before (model/initial-world 42))
(def after
  (model/advance-world-steps before 60 (/ 1.0 60.0)))

(ascii/render after)
(model/field-time after)
(take 3 (:particles after))
(take 3 (:trail after))
(projection/project-world after)

;; Pure, seeded construction and evolution are repeatable.
(assert (= before (model/initial-world 42)))
```
