# Reading the Matrix Rain Model

Start with `src/sim/model/core.clj` and follow this lifecycle:

```text
initial-world
  -> validate configuration
  -> start clock
  -> spawn deterministic streams

advance-world
  -> move and mutate each stream
  -> respawn streams that cleared the field
  -> carry explicit random state forward
  -> advance clock

visible-glyphs
  -> expand stored stream glyphs
  -> clip them to the configured field
```

## Persistent runtime shape

```clojure
{:config {...}
 :seed 2026
 :rng-state 12345
 :clock {:tick 0
         :elapsed-seconds 0.0
         :paused? false
         :simulation-fps 60}
 :speed-multiplier 1.0
 :theme-index 0
 :drops [{:id 0 :column 8 :head-y -3.0 :speed 12.5
          :glyphs [\M \A \T \R \I \X]}]}
```

The example values vary with the seed. `empty-world` and `empty-clock` make the
persistent keys explicit; a stream remains an ordinary map because it has no
independent identity or mutation mechanism.

## Why the boundaries exist

- `model/core.clj` owns the world lifecycle and controls.
- `model/drop.clj` owns deterministic stream spawn, mutation, and respawn.
- `model/projection.clj` owns the clipping rule shared by every renderer. A
  projected glyph is `{:position [column row] :depth n :stream-length n
  :character ch}`. It is not a terminal or raster cell.
- `model/config.clj` owns settings and validation, not live state.
- Renderers own scaling, actual cells, colors, and drawing.
- `app/runtime.clj` contains the process atom; model transformations stay pure.

## REPL walkthrough

```clojure
(require '[sim.model.core :as model]
         '[sim.model.projection :as projection]
         '[sim.render.ascii :as ascii])

(def start (model/initial-world 42))
(def moved
  (model/advance-world-steps start 60 (/ 1.0 60.0)))

(:clock moved)
(take 3 (:drops moved))
(take 10 (projection/visible-glyphs moved))
(ascii/render moved)

(assert (= moved
           (model/advance-world-steps (model/initial-world 42)
                                      60
                                      (/ 1.0 60.0))))
```
