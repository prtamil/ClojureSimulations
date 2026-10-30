# Leaf Fall model reading guide

The model is easiest to understand as two stories:

    construct:
      calculate trunk
      -> calculate crown
      -> rasterize trunk
      -> rasterize crown
      -> scatter leaf templates

    animate:
      display tree
      -> start leaves falling
      -> wait on an empty reset frame
      -> construct the next seeded tree

Every arrow accepts immutable data and returns new immutable data. Rendering,
keyboard input, wall-clock measurement, and mutable application ownership stay
outside the model.

## 1. Start with core

The construction story is visible at the top of leaf-fall.model.core:

    (defn calculate-tree [world]
      (let [settings (construction-settings world)
            trunk (growth/calculate-trunk settings)
            crown (growth/calculate-crown settings trunk)]
        {:trunk trunk
         :crown crown}))

    (defn rasterize-tree [world]
      (let [scene (-> raster/empty-scene
                      (raster/place-trunk world)
                      (raster/place-crown world)
                      (raster/scatter-leaves world))]
        (merge world scene)))

    (defn construct-tree [world]
      (-> world
          (assoc :tree (calculate-tree world))
          rasterize-tree))

calculate-tree creates renderer-independent geometry. rasterize-tree creates
actual discrete bark cells and immutable leaf templates from that geometry.

## 2. Read the world as domain data

A completed display world contains:

    {:config {...}
     :seed 42
     :cycle 1
     :phase :display

     :clock {:tick 0
             :elapsed-seconds 0.0
             :paused? false
             :simulation-fps 60}

     :tree
     {:trunk
      {:base [40 47]
       :top [16 47]
       :height 24
       :segments [...]
       :marks [...]}

      :crown
      {:branches [...]
       :foliage [...]}}

     :cells {...}
     :leaf-templates [...]
     :falling-leaves []
     :phase-elapsed 0.0}

The empty versions of these structures are executable values:

    growth/empty-trunk
    growth/empty-crown
    growth/empty-tree
    raster/empty-scene

There is deliberately no `empty-falling-leaf`: it is a short dynamic value
created completely from a validated leaf template, not persistent world shape.

Individual values use small, visible shapes:

    segment
    {:start [row column]
     :end [row column]
     :depth 2}

    mark
    {:position [row column]
     :glyph \/}

    foliage cloud
    {:center [row column]
     :radius 3}

    leaf template
    {:position [row column]
     :glyph \*
     :delay-seconds 1.5
     :speed 9.0}

    falling leaf
    {:origin [row column]
     :position [floating-row column]
     :glyph \*
     :delay-remaining 1.5
     :speed 9.0}

## 3. Calculate the tree

leaf-fall.model.growth has two public calculations:

    (def settings
      {:config config/default-config
       :seed 42})

    (def trunk (growth/calculate-trunk settings))
    (def crown (growth/calculate-crown settings trunk))

### Trunk

calculate-trunk:

1. Chooses a deterministic height from the seed.
2. Locates the base and top.
3. Creates the central trunk, side flare segments, and root marks.

The result retains base, top, and height because those are meaningful trunk
facts needed when the crown is calculated.

### Crown

calculate-crown:

1. Chooses attachment heights along the trunk.
2. Creates paired side branches and three top branches.
3. Recursively continues or forks each branch.
4. Adds small foliage clouds at branch tips and selected deep branches.

The recursive details stay private. The public function only says that a crown
is calculated from a trunk.

Random state is explicit and deterministic. The refactor preserves the
original draw order, so the same seed produces exactly the same tree.

## 4. Rasterize the scene

leaf-fall.model.raster begins with the complete projected scene:

    {:cells {}
     :leaf-templates []}

The public placement pipeline is:

    (-> raster/empty-scene
        (raster/place-trunk world)
        (raster/place-crown world)
        (raster/scatter-leaves world))

place-trunk draws trunk segments and root marks. place-crown draws recursive
branches. scatter-leaves visits the foliage clouds and creates deterministic
leaf templates without overwriting bark.

The bark canvas is sparse:

    {[row column]
     {:row row
      :column column
      :glyph \|
      :role :trunk}}

Only occupied coordinates exist. Bresenham traversal, drawing priority,
foliage ellipses, density decisions, and glyph selection are private helpers.

## 5. Start and move falling leaves

During display, leaf templates stay fixed in the crown.

start-fall transforms every template with leaf/start:

    leaf template -> falling leaf

A falling leaf first consumes its delay. It then increases the row in its
current position using rows per second:

    distance = speed * active-seconds

The shared cell projection derives the visible head and trail cells from each
falling leaf. The model removes a falling leaf only after its complete trail
has left the grid.

The major boundaries each protect different rules: `growth` owns recursive
continuous geometry, `raster` owns discrete line/ellipse placement and leaf
scattering, `leaf` owns delayed motion shared by lifecycle and projection, and
`render.cells` owns the grid-cell visibility rules shared by every renderer.

## 6. Follow the phase lifecycle

The phase update reads directly:

    (case (:phase world)
      :display (advance-display world seconds)
      :falling (advance-falling world seconds)
      :reset (advance-reset world seconds)
      world)

The important invariants are:

| Change | Tree geometry | Bark cells | Leaf state |
| --- | --- | --- | --- |
| Time during display | preserve | preserve | templates stay fixed |
| Start falling | preserve | preserve | templates become falling leaves |
| Time while falling | preserve | preserve | falling leaves move |
| Enter reset | preserve | hidden by renderer | falling leaves are empty |
| Start next cycle | recalculate | replace | new templates |
| Pause | preserve | preserve | everything freezes |

## 7. Configuration

Configuration contains adjustable rules, not runtime tree data.

Validation is a small pipeline:

    (-> simulation-config
        validate-values!
        validate-tree-limits!
        validate-clock!)

Each stage returns the unchanged configuration or throws ex-info containing
the invalid path and value.

## 8. REPL path

    (require '[leaf-fall.model.core :as model]
             '[leaf-fall.render.ascii :as ascii])

    (def display (model/initial-world 42))
    (keys (:tree display))
    (count (get-in display [:tree :trunk :segments]))
    (count (get-in display [:tree :crown :branches]))
    (count (:leaf-templates display))
    (ascii/render display)

    ;; Falling changes leaves, not the tree.
    (def falling (model/skip-phase display))
    (= (:tree display) (:tree falling))   ; true
    (= (:cells display) (:cells falling)) ; true

    (def later
      (model/advance-world-steps falling 120 (/ 1.0 60.0)))
    (ascii/render later)

    ;; Seeded construction remains structurally repeatable.
    (= display (model/initial-world 42))   ; true
