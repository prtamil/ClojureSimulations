# Bonsai model reading guide

The model is a sequence of pure data transformations:

    world -> calculate pot -> calculate trunk -> calculate crown
          -> place trunk -> place crown -> place pot -> completed world

Each function receives immutable data and returns new immutable data. Window
management, keyboard events, atoms, sleeping, and drawing APIs remain outside
the model.

## 1. Start with construct-tree

The main story lives in bonsai.model.core:

    (defn calculate-tree [world]
      (let [context (construction-context world)
            pot (growth/calculate-pot context)
            trunk (growth/calculate-trunk context pot)
            crown (growth/calculate-crown context trunk)]
        {:pot pot
         :tree {:trunk trunk
                :crown crown}}))

    (defn place-tree [world]
      (assoc world :cells
             (-> {}
                 (raster/place-trunk world)
                 (raster/place-crown world)
                 (raster/place-pot world))))

    (defn construct-tree [world]
      (let [{:keys [pot tree]} (calculate-tree world)]
        (-> world
            (assoc :pot pot :tree tree)
            place-tree)))

calculate-tree creates renderer-independent geometry. place-tree projects
existing geometry into character cells for the selected season.

## 2. Read the data as a story

A completed world contains explicit pot, trunk, and crown values:

    {:pot {:top-row 37}

     :tree
     {:trunk
      {:segments [...]
       :branch-sites [{:position [48.0 37.0] :fraction 0.0}
                      ...]
       :tip [45.2 12.9]}

      :crown
      {:branches [...]
       :foliage [...]
       :tips [...]}}

     :cells {...}}

The collections have one clear purpose:

- Trunk segments draw the main silhouette.
- Branch sites are possible attachment positions along the trunk.
- The trunk tip is where the apex foliage grows.
- Crown branches contain every side-branch segment.
- Foliage values describe ellipse-like leaf clouds.
- Crown tips let Winter place snow without changing tree geometry.

## 3. Tree calculation

bonsai.model.growth exposes three public calculations:

    (def context
      {:config config/default-config
       :seed 42
       :style-key :moyogi
       :style (config/style :moyogi)})

    (def pot (growth/calculate-pot context))
    (def trunk (growth/calculate-trunk context pot))
    (def crown (growth/calculate-crown context trunk))

### Trunk

calculate-trunk starts at the pot, samples the selected silhouette from bottom
to top, and joins consecutive points with segments.

- Chokkan is upright with a small seeded wobble.
- Moyogi follows a sine curve.
- Shakan leans steadily.
- Kengai rises briefly and then cascades.
- Bunjin forms a tall, sparse literati silhouette.

### Crown

calculate-crown visits the calculated branch sites. Sites inside the
configured branch band may sprout a side branch. A branch either continues,
forks, or ends in foliage.

Recursive branching stops when the branch becomes too short or reaches the
style maximum depth. Random state stays explicit and local to crown
calculation, so the same seed and style always produce equal values.

The trunk and crown use separate deterministic random streams. Changing crown
rules therefore cannot accidentally change the trunk silhouette.

## 4. Placement

bonsai.model.raster exposes the three placement stages:

    (def cells
      (-> {}
          (raster/place-trunk world)
          (raster/place-crown world)
          (raster/place-pot world)))

- place-trunk rasterizes the main trunk segments.
- place-crown places branches, foliage, and optional Winter snow.
- place-pot runs last so the pot remains visually in front.

The result is a sparse coordinate map containing only occupied cells:

    {[36 48] {:row 36
              :column 48
              :glyph \H
              :role :trunk-dark
              :color-index 0}
     ...}

Low-level details such as Bresenham line traversal, foliage ellipses, bounds
checking, and glyph selection are private helpers. They do not interrupt the
public placement story.

## 5. World changes

The user actions in bonsai.model.core describe their effects directly:

    (defn cycle-style [world direction]
      (-> world
          (select-style direction)
          restart-growth
          construct-tree))

    (defn cycle-theme [world direction]
      (-> world
          (select-theme direction)
          place-tree))

The important invariant is:

| Change | Recalculate tree? | Place cells again? |
| --- | --- | --- |
| Seed | yes | yes |
| Style | yes | yes |
| Theme | no | yes |
| Wind | no | no |
| Time step | no | no |
| Replay growth | no | no |

## 6. Animation

Tree geometry does not change every frame. A time step only advances the clock
and growth progress:

    (-> world
        (advance-clock tick-count elapsed-seconds)
        (advance-growth elapsed-seconds))

bonsai.render.cells uses that progress to reveal the already placed cells and
uses wind to select frame-specific leaf glyphs.

## 7. REPL path

    (require '[bonsai.model.core :as model]
             '[bonsai.render.ascii :as ascii])

    (def world (model/initial-world 42))
    (keys (:tree world))
    (count (get-in world [:tree :trunk :segments]))
    (count (get-in world [:tree :crown :branches]))
    (ascii/render world)

    ;; A season changes cells, not geometry.
    (def winter (model/cycle-theme world 3))
    (= (:tree world) (:tree winter))       ; true
    (not= (:cells world) (:cells winter))  ; true

    ;; A style creates new geometry and restarts its reveal.
    (def shakan (model/cycle-style world 1))
    (model/current-style-key shakan)       ; :shakan
    (:growth-progress shakan)              ; 0.0
