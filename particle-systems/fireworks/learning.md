# Reading the Fireworks Model

Start in `src/fireworks/model/core.clj`. It owns the complete world and tells
the high-level story. Read `firework.clj` next for the lifecycle of one
firework, then `spark.clj` for one burst. Finish with `render/glyphs.clj` to see
how model values become characters.

## The story

Creating a world is deliberately small:

```text
validate config -> start clock -> construct show -> completed world
```

Advancing it is equally direct:

```text
advance every firework -> save the new random state -> advance the clock
```

One firework moves through three stages:

```text
waiting -> flying -> bursting -> waiting
```

- A waiting firework counts down and launches.
- A flying firework moves upward until it slows or reaches the ceiling.
- A bursting firework moves and fades its sparks, then waits to relaunch.

## Complete runtime data

Configuration contains only adjustable rules. Live data is kept in the world:

```clojure
{:config {...}
 :seed 42
 :random-state 42
 :theme-index 0
 :clock {:tick 0
         :elapsed-seconds 0.0
         :paused? false
         :simulation-fps 30}
 :show {:fireworks []
        :next-id 0}}
```

The public `empty-world`, `empty-clock`, and `empty-show` values document this
shape in executable Clojure. `initial-world` starts from them and fills in the
validated values.

Each firework stage has only the data it needs:

```clojure
;; Waiting
{:id 0
 :stage :waiting
 :delay-seconds 0.5}

;; Flying
{:id 0
 :stage :flying
 :position [42.0 37.0]
 :velocity [0.0 -30.0]
 :color-index 3}

;; Bursting
{:id 0
 :stage :bursting
 :sparks [{:position [42.0 12.0]
           :velocity [8.0 -4.0]
           :life 0.8
           :fade-rate 1.2
           :glyph \*
           :color-index 3}]}
```

These short dynamic shapes are documented beside their constructors. They do
not have ceremonial `empty-*` values because each is created completely during
a stage transition and is not persistent world structure.

## Calculation and placement

The model calculates renderer-independent values. It knows about seconds,
positions, velocities, life, and color indexes, but not terminal drawing or
pixels.

`render/glyphs.clj` performs projection:

```text
empty canvas
  -> place every firework
       flying   -> place rocket -> place exhaust
       bursting -> place sparks
  -> order visible glyphs
```

A canvas is a map from `[column row]` to a semantic glyph. Using a map gives a
simple painter's rule: a later value at the same position replaces the earlier
one. `project-show` returns the final glyphs as a row-and-column ordered vector.

```clojure
{[10 12] {:column 10
          :row 12
          :glyph \*
          :role :spark
          :color-index 2
          :life 0.7
          :bold? true
          :dim? false}}
```

The ASCII, Lanterna, and Quil adapters all consume this same projection.

## Deterministic randomness

Randomness is an ordinary value named `:random-state`. Functions that need a
random choice receive the current value and return the next one with their
result:

```clojure
[next-random-state result]
```

There is no hidden random generator. The same configuration, seed, step count,
and step duration therefore produce structurally equal worlds and identical
ASCII frames.

## What recalculates the show?

- Advancing time moves fireworks and sparks.
- Adding a firework constructs one new waiting value.
- Removing a firework drops it from the active show.
- Restarting reconstructs the show from the original seed.
- Resizing reconstructs the show for the new grid dimensions.
- Changing the theme only changes `:theme-index`; geometry stays equal.
- Pausing and changing simulation speed only update the clock.

## REPL walkthrough

```clojure
(require '[fireworks.model.core :as model]
         '[fireworks.render.glyphs :as glyphs]
         '[fireworks.render.ascii :as ascii])

(def start (model/initial-world 42))

;; See the full shape before anything launches.
(select-keys start [:seed :random-state :clock :show])

;; One step launches the first firework.
(def launched (model/advance-world start (/ 1.0 30.0)))
(get-in launched [:show :fireworks 0])

;; Advance far enough to see both flight and a burst.
(def frame (model/advance-world-steps start 45 (/ 1.0 30.0)))
(frequencies (map :stage (get-in frame [:show :fireworks])))

;; Inspect placement data before viewing the text image.
(take 10 (glyphs/project-show frame))
(ascii/render frame)

;; Repeatability is structural, not merely visual.
(= frame
   (model/advance-world-steps (model/initial-world 42)
                              45
                              (/ 1.0 30.0)))
```
