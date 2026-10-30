# Reading the Basic Simulation Model

Start with `src/sim/model/core.clj`. The complete simulation is intentionally
small enough to read in one pass:

```text
initial-world
  -> validate configuration
  -> start the clock
  -> copy configured particles

advance-world
  -> advance clock
  -> move every particle
  -> wrap positions at grid edges
```

## Persistent runtime shape

```clojure
{:config {...}
 :seed 2026
 :clock {:tick 0
         :elapsed-seconds 0.0
         :paused? false
         :simulation-fps 60}
 :theme-index 0
 :particles [{:id 1 :x 5.0 :y 10.0 :vx 8.0 :vy 0.0}]}
```

`empty-world` and `empty-clock` document the persistent shape. A particle is a
short map documented beside `move-particle`; it does not need an `empty-*`
value because it is created from validated configuration and has no separate
lifecycle.

## Why these boundaries exist

- `model/core.clj` owns particles because they construct, advance, and reset
  with the world.
- `model/config.clj` is separate because all constructors and renderers depend
  on one validated set of adjustable rules.
- The renderers read the world directly. There is no shared projection
  namespace because the example has no additional visibility policy.
- `app/runtime.clj` contains the process atom; the model remains pure.

## REPL walkthrough

```clojure
(require '[sim.model.core :as model]
         '[sim.render.ascii :as ascii])

(def start (model/initial-world 42))
(def moved
  (model/advance-world-steps start 60 (/ 1.0 60.0)))

(:clock moved)
(:particles moved)
(ascii/render moved)

(= moved
   (model/advance-world-steps (model/initial-world 42)
                              60
                              (/ 1.0 60.0)))
```
