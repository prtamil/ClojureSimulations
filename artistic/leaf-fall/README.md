# Leaf Fall — Procedural ASCII Tree

Leaf Fall is a Clojure-first simulation inspired by the visual idea in
`artistic/leaf_fall.c`: a generated ASCII tree rests for a moment, sheds its
foliage in staggered glowing trails, clears, and grows a new deterministic
variation.

This is a fresh functional design, not a line-by-line port. It preserves the
recognizable result while expressing each concern as immutable data and pure
transformations:

- recursive, renderer-independent branch geometry;
- a two-column trunk, flared base, roots, side branches, and crown;
- sparse Bresenham rasterization with direction-aware ASCII glyphs;
- soft, terminal-aspect-correct foliage clouds;
- immutable leaf templates and elapsed-time particle motion;
- display → falling → reset → regenerated-tree phases.

There are no fixed branch stacks, leaf pools, dense model canvases, or
frame-counter emulation. Tree size is governed by the configured geometry,
and leaf motion is measured in rows per second.

## Architecture

The application structure follows the simulation framework in
`scratch-projects/basic-clojure-simulation-framework`, while its character-cell
rendering is informed by `artistic/bonsai`.

```text
seed + config -> calculate trunk -> calculate crown
                                      |
                     place trunk -> place crown -> scatter leaves
                                      |
                              immutable world
                                      |
                         display -> falling -> reset
                                      |
                               semantic cells
                              /       |        \
                         headless  terminal    Quil
```

```text
src/leaf_fall/
|-- model/
|   |-- config.clj    validated simulation parameters
|   |-- growth.clj    calculate explicit trunk and crown data
|   |-- raster.clj    place bark and scatter leaf templates
|   |-- leaf.clj      move explicit falling-leaf data
|   `-- core.clj      readable construction and lifecycle story
|-- render/
|   |-- cells.clj     model-to-semantic-cell projection
|   |-- ascii.clj     pure headless renderer
|   |-- batch.clj     renderer-side glyph batching
|   |-- lanterna.clj  interactive colored terminal adapter
|   `-- quil.clj      interactive graphical adapter
|-- terminal/
|   `-- framework.clj reusable input, timing, and drawing loop
|-- app/
|   |-- controller.clj
|   `-- runtime.clj
`-- utility/
    |-- clock.clj     fixed-step wall-clock accumulator
    |-- random.clj    explicit deterministic random streams
    `-- theme.clj     semantic RGB colors
```

The model has no Quil dependency or mutable global state. A seed determines
the tree geometry, sparse raster, and leaf templates. All renderers consume
the same painter-ordered semantic cells, so headless output, the terminal, and
the graphical window show the same simulation.

## Model data

The runtime structure exposes the domain directly:

    {:tree
     {:trunk {:base [...]
              :top [...]
              :height 25
              :segments [...]
              :marks [...]}
      :crown {:branches [...]
              :foliage [...]}}

     :cells {...}
     :leaf-templates [...]
     :falling-leaves [...]
     :phase :display}

Tree calculation is independent of placement. Starting leaf fall preserves the
tree and bark cells; only the leaf templates become moving particles. See
learning.md for the complete model walkthrough.

## Run

From `artistic/leaf-fall`:

```powershell
# Headless ASCII preview
clojure -M:run

# Interactive Lanterna animation
clojure -M:terminal

# Interactive Quil window
clojure -M:gui

# Tests
clojure -M:test

# CIDER / nREPL
clojure -M:dev

# Library or standalone JAR
clojure -T:build jar
clojure -T:build uber
```

The `:gui` alias enables native access for JOGL on current JDKs.

## Controls

| Key | Action |
| --- | --- |
| `q`, `Esc`, or `Ctrl-C` | Close the active renderer |
| `r` | Generate a new tree immediately |
| `Space` | Start leaf fall, or replace a falling/resetting tree |
| `]` / `[` | Raise / lower fixed-step simulation frequency |

The frequency control changes update granularity, not the intended duration
or speed of the animation. Falling distance is integrated from elapsed time.

## REPL sketchbook

```clojure
(require '[leaf-fall.model.core :as model]
         '[leaf-fall.render.ascii :as ascii])

(def tree (model/initial-world 42))
(ascii/render tree)

(def falling (model/skip-phase tree))
(def two-seconds-later
  (model/update-world-steps falling 120 (/ 1.0 60.0)))
(ascii/render two-seconds-later)

;; Construction and updates are ordinary repeatable value transformations.
(assert (= tree (model/initial-world 42)))
```
