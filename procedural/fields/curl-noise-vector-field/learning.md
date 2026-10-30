# Reading the Curl Noise Model

Start with `src/sim/model/core.clj`. It shows the complete immutable world,
particle movement, trail persistence, controls, and advancement in one place.
Read `field.clj` next for the independent curl mathematics. Finish with
`render/projection.clj` to see how model values become a visible scene.

## The public story

Construction begins with explicit empty values:

```text
empty world
  -> validate configuration
  -> initialize clock and controls
  -> shuffle the noise permutation
  -> spawn the particle population
  -> calculate samples for the selected static view
  -> completed world
```

A particle step is another small story:

```text
advance clock
  -> advance field time
  -> fade the flash
  -> move or respawn particles
  -> fade the old trail
  -> deposit new particle hits
```

Static views do not need particle integration. A batch advances the clock,
field, and flash by the total elapsed time, then samples the final field once.

## Complete runtime data

```clojure
{:config {...}
 :random-state 123456789                  ; next deterministic random value
 :clock {:tick 0
         :paused? false
         :simulation-fps 60}
 :field {:time 0.0
         :permutation [...]}
 :particles []
 :trail {}
 :view {:pattern :particles
        :samples {}}
 :particle-speed 8
 :drift-multiplier 1
 :theme-index 0
 :flash-intensity 1.0}
```

The public `empty-world`, `empty-clock`, and `empty-view` values make the
persistent shape visible before any calculation. Particle, trail-point, and
field-sample shapes are short comments beside the private constructors that
create them; they do not need separate public abstractions.

## Why these abstractions exist

The project uses the smallest boundary that explains an independent reason to
change:

- `model/core.clj` exists because field time, particles, and trails advance as
  one simulation lifecycle. Particles and trails stay here as ordinary data.
- `model/field.clj` exists because substantial potential and curl mathematics
  is shared by particle movement and three static views.
- `render/projection.clj` exists because three renderers need the same visible
  scene without seeing particle age, lifetime, or view-selection mechanics.
- `model/config.clj` exists because adjustable rules must be validated before
  they enter the runtime world.

Each remaining namespace documents its `WHY`, `DATA`, `OPERATIONS`, and
`BOUNDARY`. A domain noun alone is not enough reason to create a namespace.

Individual values have domain-shaped data:

```clojure
;; Particle
{:position [23.4 11.8]
 :band 2
 :age-seconds 0.4
 :lifetime-seconds 3.7}

;; Trail point or scalar field sample
{:position [23 11]
 :intensity 0.82
 :band 2}

;; Vector field sample
{:position [24 12]
 :intensity 0.64
 :band 2
 :velocity [0.31 -0.44]}
```

## Calculation versus projection

The model calculates the field, particles, trails, and selected static field
samples. None of those namespaces prints text, opens a window, or knows how a
glyph or pixel is drawn.

`render/projection.clj` produces this shared scene:

```clojure
{:pattern :particles
 :samples {...}
 :particles [{:position [23.4 11.8] :band 2}]
 :flash-intensity 0.5}
```

For particle views, `:samples` refers to the calculated trail. For static
views, it refers to `[:view :samples]`. Domain-only particle fields such as age
and lifetime are removed from the projected scene.

The renderers then interpret the same scene differently:

- ASCII maps intensity and velocity to characters.
- Lanterna maps model positions into real terminal cells.
- Quil maps model positions into pixels, rectangles, circles, and arrows.

This is why the shared values are called samples and a scene rather than
cells. A terminal cell is only one possible output representation.

## Deterministic randomness

Randomness is an ordinary `:random-state` value. Functions receive the current
state and return the next state with their result:

```clojure
[next-random-state value]
```

The same configuration, seed, elapsed steps, and controls therefore produce
structurally equal worlds and identical rendered frames.

## What recalculates data?

- Advancing a particle view moves particles and updates the trail.
- Advancing a static view samples the final field once per fixed-step batch.
- Changing views clears the old trail and immediately calculates static
  samples when the new view needs them.
- Resetting reshuffles the field and respawns particles while preserving
  interactive controls.
- Changing flow speed affects later field and particle advancement.
- Changing the theme changes presentation only; the projected scene remains
  structurally equal.
- Pausing and changing simulation FPS update only clock or control data.

## REPL walkthrough

```clojure
(require '[sim.model.core :as model]
         '[sim.render.ascii :as ascii]
         '[sim.render.projection :as projection])

(def start (model/initial-world 42))

;; Inspect the complete initial shape.
(select-keys start [:random-state :clock :field :view])
(take 2 (:particles start))

;; Advance one second at 60 fixed steps.
(def moved
  (model/advance-world-steps start 60 (/ 1.0 60.0)))
(get-in moved [:clock :tick])
(take 3 (:trail moved))

;; Projection exposes only values needed by renderers.
(def scene (projection/project-world moved))
(select-keys scene [:pattern :flash-intensity])
(take 3 (:particles scene))
(ascii/render moved)

;; Move from PARTICLES to VECTOR and inspect calculated samples.
(def vector-world (model/cycle-view moved 1))
(take 3 (vals (get-in vector-world [:view :samples])))
(ascii/render vector-world)

;; Seeded construction and advancement are repeatable.
(= moved
   (model/advance-world-steps (model/initial-world 42)
                              60
                              (/ 1.0 60.0)))
```
