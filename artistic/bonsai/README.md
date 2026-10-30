# Bonsai — Generative ASCII Garden

A Clojure-first generative tree simulation inspired by the visual language of
`artistic/bonsai.c`. A seed and style produce immutable botanical geometry;
seasonal themes project that geometry into semantic character cells; ASCII,
Lanterna, and Quil render the same world.

The implementation derives its behavior from the artwork rather than copying
the C program's structs, fixed buffers, or main-loop control flow.

## Styles and seasons

Five classical silhouettes are included:

- **Chokkan** — formal upright
- **Moyogi** — informal upright
- **Shakan** — slanting
- **Kengai** — cascade
- **Bunjin** — literati

Spring, Summer, Autumn, Winter, Cherry, and Mono themes provide seasonal
palettes and leaf alphabets. Winter presents the same generated tree without
foliage and places snow at its terminal branch tips; changing season never
changes the underlying branch structure.

## Clojure model

- Tree data tells the botanical story directly: a trunk contains its segments,
  possible branch sites, and tip; a crown contains branches, foliage, and terminal
  tips; the pot records where the tree meets the container.
- Rasterization produces a sparse coordinate map containing only occupied
  semantic cells rather than allocating a full character grid.
- Random state is threaded explicitly through generation, so a seed and style
  always produce the same geometry.
- Growth reveal and wind rustle are frame-level projections in
  `bonsai.render.cells`; the core model contains no renderer policy.
- Style or seed changes regenerate geometry. Theme changes only rebuild the
  semantic presentation.
- Normal animation steps update elapsed time and growth progress without
  mutating or regenerating the tree.

The persistent shape is explicit in `empty-world` and `empty-clock`:

```clojure
{:config {...} :seed 2026 :generation 0
 :clock {:tick 0 :elapsed-seconds 0.0
         :paused? false :simulation-fps 60}
 :style-index 1 :theme-index 0
 :pot {:top-row 37}
 :tree {:trunk {...} :crown {...}}
 :cells {...}
 :wind 1.0 :growth-progress 1.0}
```

`growth` is separate for recursive continuous geometry; `raster` is separate
for Bresenham lines, foliage ellipses, overlap, and layering on actual grid
cells; `render.cells` is separate because every renderer shares its reveal and
wind projection. These boundaries do not own the world lifecycle or devices.

## Architecture

```text
seed + style + config ---> pot -> trunk -> crown
                                                |
theme -----------------> rasterize trunk -> crown -> pot
                                                |
time + wind -----------> render/cells ---> visible frame glyphs
                                          /        |        \
                                      ASCII    Lanterna     Quil
```

```text
src/bonsai/
|-- model/
|   |-- config.clj       style parameters, settings, and validation
|   |-- growth.clj       calculate the pot, trunk, and crown
|   |-- raster.clj       place the trunk, crown, and pot onto cells
|   `-- core.clj         readable construction and world-advance story
|-- render/
|   |-- cells.clj        growth reveal and deterministic leaf rustle
|   |-- ascii.clj        headless text renderer
|   |-- lanterna.clj     interactive colored terminal renderer
|   `-- quil.clj         interactive graphical renderer
|-- app/
|   |-- controller.clj   renderer-independent keyboard commands
|   `-- runtime.clj      shared running-world atom
|-- terminal/
|   `-- framework.clj    lifecycle, input, timing, and buffered drawing
`-- utility/
    |-- clock.clj        fixed-step wall-clock accumulator
    |-- random.clj       explicit deterministic random stream
    `-- theme.clj        seasonal palettes and color conversion
```

## Run

From this project directory:

```powershell
# Headless ASCII preview
clojure -M:run

# Interactive Lanterna view
clojure -M:terminal

# Interactive Quil window
clojure -M:gui

# Tests
clojure -M:test

# CIDER / nREPL
clojure -M:dev

# Build artifacts
clojure -T:build jar
clojure -T:build uber
```

## Controls

| Key | Action |
| --- | --- |
| `n` / `N` | Next / previous bonsai style |
| `t` / `T` | Next / previous seasonal theme |
| `r` | Generate a new seeded tree |
| `g` | Replay the growth reveal |
| `+` / `-` | Increase / decrease wind |
| `]` / `[` | Raise / lower simulation Hz |
| `Space` | Pause or resume animation |
| `q`, `Esc`, or `Ctrl-C` | Close the active renderer |

## REPL sketchbook

```clojure
(require '[bonsai.model.core :as model]
         '[bonsai.render.ascii :as ascii])

(def tree (model/initial-world 42))
(ascii/render tree)

(def cascade (model/cycle-style tree 2))
(ascii/render cascade)

;; A theme changes cells, but never geometry.
(def winter (model/cycle-theme tree 3))
(assert (= (:tree tree) (:tree winter)))

;; Seeded worlds remain complete data values and compare structurally.
(assert (= (model/initial-world 42)
           (model/initial-world 42)))
```

## Verification

The suite tests deterministic generation, all five silhouettes, sparse
rasterization, theme-independent geometry, Winter snow, pot completion,
growth replay, wind determinism, controls, timing, and configuration ranges.
Character-level snapshots cover every style/theme combination.

## License

MIT; see [LICENSE](LICENSE).
