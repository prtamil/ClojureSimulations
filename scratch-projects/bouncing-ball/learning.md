# Reading the Bouncing Ball Model

Start with `src/sim/model/core.clj`. Its public functions tell the lifecycle:

```text
initial-world
  -> validate configuration
  -> start the clock
  -> create the ball

advance-world
  -> advance clock time
  -> advance radius-aware elastic motion

project-world
  -> advance only a temporary ball value
  -> leave stored time and controls unchanged
```

## Persistent runtime shape

```clojure
{:config {...}
 :seed 2026
 :clock {:tick 0
         :elapsed-seconds 0.0
         :paused? false
         :simulation-fps 60}
 :speed-multiplier 1.0
 :theme-index 0
 :ball {:x 10.0 :y 5.0 :vx 8.49 :vy 8.49 :radius 0.45}}
```

`empty-world` and `empty-clock` make this persistent shape visible. The ball is
a compact dynamic value documented by `sim.model.ball`; it does not need a
ceremonial `empty-ball` because configuration creates it completely.

## Why each boundary exists

- `model/core.clj` owns construction, time, controls, projection, and reset.
- `model/ball.clj` isolates the substantial collision rule that can consume
  several wall impacts during one long step. It owns no world lifecycle state.
- `model/config.clj` gives every consumer one validated settings boundary.
- Renderers translate the same world into text, terminal cells, or Quil
  geometry. They do not perform physics.
- `app/runtime.clj` owns mutation through an atom; model functions stay pure.

## REPL walkthrough

```clojure
(require '[sim.model.core :as model]
         '[sim.render.ascii :as ascii])

(def start (model/initial-world 42))
(def moved
  (model/advance-world-steps start 60 (/ 1.0 60.0)))

(:clock moved)
(:ball moved)
(ascii/render moved)

(def projected (model/project-world moved 0.01))
(assert (= (dissoc moved :ball)
           (dissoc projected :ball)))

(assert (= moved
           (model/advance-world-steps (model/initial-world 42)
                                      60
                                      (/ 1.0 60.0))))
```
