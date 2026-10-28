# Bonsai model reading guide

The program is easiest to understand as three pure transformations followed
by renderer adapters:

```text
seed + style -> tree geometry -> semantic cells -> visible glyphs
```

Each arrow accepts data and returns data. Windows, atoms, sleeping, keyboard
events, and drawing APIs stay outside these transformations.

## 1. Tree geometry

Start with `bonsai.model.growth/grow-tree`:

```clojure
(require '[bonsai.model.config :as config]
         '[bonsai.model.growth :as growth])

(def generated
  (growth/grow-tree config/default-config 42 :moyogi))
```

The result contains the pot boundary and a tree:

```clojure
{:pot-top 37
 :tree {:segments [...]
        :foliage [...]
        :tips [...]}}
```

A segment is renderer-independent line geometry:

```clojure
{:x0 48.0 :y0 37.0
 :x1 48.7 :y1 36.2
 :depth 0}
```

Foliage is an ellipse-like cloud centered on a terminal branch:

```clojure
{:cx 35.4 :cy 17.8
 :radius 2.7
 :depth-at-tip 6}
```

Tips remain explicit so a bare Winter presentation can place snow without
reconstructing or changing the tree.

### Trunk generation

The selected style supplies a height fraction and silhouette parameters.
`grow-tree` walks from the pot upward through a bounded number of samples.
Each sample adds one trunk segment and may sprout a side branch inside the
configured branch band.

The five styles alter only the trunk path and branching parameters:

- Chokkan adds small seeded horizontal wobble.
- Moyogi follows a sine curve.
- Shakan leans steadily.
- Kengai rises briefly and then cascades.
- Bunjin grows a tall, sparse literati trunk.

### Recursive branches

`grow-branch` is bounded by two domain rules:

- stop when a branch is shorter than `:min-branch-length`;
- stop after the style's `:max-depth`.

At a stopping point it records a tip and a foliage cloud. Otherwise it adds a
segment, makes a seeded continue-or-fork decision, shortens the child branch,
and recurs. Persistent vectors grow naturally; no storage-capacity checks are
part of the botanical algorithm.

## 2. Semantic cells

`bonsai.model.raster/bake-tree` projects geometry into character cells:

```clojure
(require '[bonsai.model.raster :as raster]
         '[bonsai.utility.theme :as theme])

(def spring-cells
  (raster/bake-tree config/default-config
                    (:tree generated)
                    (:pot-top generated)
                    (first theme/themes)
                    42))
```

The result is a sparse map. Only occupied coordinates exist:

```clojure
{[36 48] {:row 36 :column 48
          :glyph \H :role :trunk-dark :color-index 0}
 ...}
```

Rasterization uses four passes:

1. Paint branch segments with Bresenham line traversal.
2. Paint soft-edged foliage ellipses unless the theme is bare.
3. Paint snow at terminal tips when the theme supplies a snow glyph.
4. Paint the pot last so it remains visually in front.

The sparse map gives constant-time coordinate lookup for ASCII rendering and
direct iteration for Lanterna and Quil. It also makes blank space implicit.

## 3. Visible frame glyphs

`bonsai.render.cells` derives the glyph visible in one animation frame.

- Growth progress reveals bark upward from the pot.
- Foliage cells appear later with seeded per-cell timing jitter.
- Wind may swap a leaf glyph using a coordinate/time hash.
- The same world value always produces the same visible glyphs.

These decisions are presentation policy, so they do not live in the world
or the tree-generation model.

## 4. World orchestration

`bonsai.model.core` owns user-facing state:

```clojure
{:seed 42
 :style-index 1
 :theme-index 0
 :wind 1
 :growth-progress 1.0
 :tree {...}
 :cells {...}}
```

The important rebuild rules are:

| Change | Geometry | Semantic cells |
| --- | --- | --- |
| Seed | regenerate | rebuild |
| Style | regenerate | rebuild |
| Theme | preserve | rebuild |
| Wind | preserve | preserve |
| Time step | preserve | preserve |
| Growth replay | preserve | preserve |

That separation is the central invariant of the design: botany is determined
by seed and style; season and animation determine presentation.

## 5. Suggested REPL path

```clojure
(require '[bonsai.model.core :as model]
         '[bonsai.render.ascii :as ascii]
         '[bonsai.render.cells :as cells])

(def world (model/initial-world 42))
(select-keys world [:seed :style-index :theme-index :wind])
(count (get-in world [:tree :segments]))
(count (:cells world))
(ascii/render world)

(def winter (model/cycle-theme world 3))
(= (:tree world) (:tree winter)) ; true
(not= (:cells world) (:cells winter)) ; true

(def growing (model/replay-growth world))
(count (cells/visible-cells growing))
(def later (model/update-world-steps growing 30 (/ 1.0 30.0)))
(count (cells/visible-cells later))
```
