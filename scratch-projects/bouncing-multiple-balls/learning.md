# Reading the Multiple-Ball Model

Begin with `src/sim/model/core.clj` and follow its lifecycle:

```text
initial-world
  -> validate config
  -> start clock
  -> spawn deterministic non-overlapping balls

advance-world
  -> for every fixed step
     -> perform configured wall-motion substeps
     -> perform configured pair-collision passes
  -> advance clock
```

## Persistent runtime shape

```clojure
{:config {...}
 :seed 2026
 :rng-state 545291967
 :clock {:tick 0
         :elapsed-seconds 0.0
         :paused? false
         :simulation-fps 60}
 :speed-multiplier 1.0
 :theme-index 0
 :balls [{:id 0 :x 12.3 :y 7.4 :vx 5.1 :vy -2.2
          :radius 0.5 :mass 0.25 :band-index 2}]}
```

The exact sample numbers vary by seed; the keys do not. Random state is
explicit, so equal configuration and seed produce structurally equal worlds.

## Why the boundaries exist

- `model/core.clj` owns the world lifecycle and physics sequencing.
- `model/ball.clj` owns non-overlapping spawn and wall motion. Both contain
  enough rules and mathematics to justify a focused boundary.
- `model/collision.clj` owns only pair separation and impulses. The core
  decides how many passes to run.
- `model/config.clj` owns settings and validation, never live entities.
- Renderers read the same balls directly. No shared scene projection is needed
  because they do not share another visibility or geometry policy.
- `app/runtime.clj` contains the process atom; model transformations stay pure.

## REPL walkthrough

```clojure
(require '[sim.model.core :as model]
         '[sim.render.ascii :as ascii])

(def start (model/initial-world 42))
(def moved
  (model/advance-world-steps start 120 (/ 1.0 60.0)))

(:clock moved)
(take 3 (:balls moved))
(ascii/render moved)

(assert (= start (model/initial-world 42)))
(assert (= moved
           (model/advance-world-steps (model/initial-world 42)
                                      120
                                      (/ 1.0 60.0))))
```
