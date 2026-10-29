# Refactoring

Refactor the model layer of this Clojure project from first principles.

Project: <PROJECT_PATH>
Primary model directory: <MODEL_DIRECTORY>

## Goal

Make the model read like a simple domain story that a Clojure beginner can
follow. Preserve the project's behavior unless a structural improvement
intentionally requires a change.

Do not merely rename existing complicated functions or add thin wrapper
functions. Simplify the underlying data flow, function boundaries, data
structures, and terminology.

Start by inspecting:

- Model namespaces
- Configuration
- Renderers or projections consuming model data
- Tests and visual snapshots
- Documentation and REPL examples
- Every call site of the current model API

Run the complete test suite before editing to establish a baseline.

## Core principles

### 1. Make the top-level model read like pseudocode

The central namespace should show the domain workflow directly.

For example:

    (defn calculate-thing [world]
      (let [part-a (calculate-part-a world)
            part-b (calculate-part-b world part-a)]
        {:part-a part-a
         :part-b part-b}))

    (defn place-thing [world]
      (assoc world :cells
             (-> {}
                 (place-part-a world)
                 (place-part-b world)
                 (place-container world))))

    (defn construct-thing [world]
      (let [calculated (calculate-thing world)]
        (-> world
            (merge calculated)
            place-thing)))

Important workflows such as initialization, regeneration, theme changes, time
advancement, and user actions should also read as small stories using thread
macros, let expressions, and well-named pure functions.

### 2. Make runtime data explicit

Do not force readers to reconstruct data shapes from scattered assoc, update,
and destructuring expressions.

Define and use visible empty data values near the model that owns them:

    (def empty-part-a
      {:segments []
       :attachment-sites []
       :tip nil})

    (def empty-part-b
      {:branches []
       :foliage []
       :tips []})

    (def empty-model
      {:part-a empty-part-a
       :part-b empty-part-b})

Initialize the world with these values so its complete shape is visible before
calculation.

Add short examples of individual dynamic values:

    ;; segment         {:start [x y] :end [x y] :depth 0}
    ;; attachment site {:position [x y] :fraction 0.5}
    ;; foliage         {:center [x y] :radius 3.0 :depth 6}
    ;; tip             {:position [x y] :depth 6}

Prefer domain-shaped data such as:

    {:start [x y] :end [x y]}

over mechanically shaped data such as:

    {:x0 x :y0 y :x1 x :y1 y}

Use names that explain why data exists. For example, prefer branch-sites over
generic names such as points.

### 3. Separate calculation from placement

Calculation should produce renderer-independent domain data.

Placement or projection should turn that data into cells, pixels, characters,
or another presentation representation.

A theme or presentation change should re-place existing geometry without
recalculating it. A seed or structural-style change may recalculate and then
place it.

Make this distinction explicit in function names and tests.

### 4. Simplify helper functions

Private helpers should describe one understandable action.

Prefer plain names such as:

- make-segment
- point-on-trunk
- end-of-branch
- grow-children
- add-foliage-at-tip
- try-growing-side-branch
- put-cell
- choose-line-glyph
- draw-segment
- draw-foliage
- draw-pot

Avoid vague or overly technical names unless the algorithm genuinely requires
them.

Keep low-level mechanics private so they do not interrupt the public domain
story.

Replace long positional argument lists with a clearly named context or
settings map when several functions share the same inputs:

    {:config config
     :seed seed
     :style-key style-key
     :style style}

Do not turn every small function into a context-map function. Use maps only
where they make relationships clearer.

### 5. Keep state transformations pure

Use immutable maps and vectors.

Randomness should remain deterministic and explicit. Given the same
configuration, seed, and style, the result must compare structurally equal.

If separate stages should not influence each other accidentally, derive
independent deterministic random streams for them. For example, changing crown
rules should not unexpectedly change the trunk.

Avoid atoms, global mutable state, protocols, records, macros, or stateful
random generators unless the project genuinely needs them.

### 6. Use idiomatic beginner-friendly Clojure

Prefer:

- Plain maps and vectors
- let
- Thread-first pipelines
- assoc
- update
- update-in
- map
- reduce
- Small recursive functions only where recursion reflects the domain

Avoid clever abstractions, unnecessary transducers, deeply nested anonymous
functions, and excessive threading through temporary implementation details.

Necessary mathematical or recursive complexity may remain, but it should live
behind simple domain functions.

### 7. Make configuration readable

Configuration should contain adjustable rules and parameters, not dynamic
runtime tree or entity data.

Organize configuration by domain concepts.

Refactor validation into named stages:

    (defn validate! [config]
      (-> config
          validate-values!
          validate-structural-limits!
          validate-clock!
          validate-animation!
          validate-styles!))

Each validation stage should either return the unchanged configuration or
throw ex-info containing a useful path and value.

### 8. Make projection data explicit

At the top of the raster or projection namespace, document the representation
it produces.

For example:

    ;; A canvas maps [row column] to a semantic cell:
    ;;
    ;; {[row column] {:row row
    ;;                :column column
    ;;                :glyph character
    ;;                :role :foliage
    ;;                :color-index 0}}

Use a small constructor such as make-cell if it prevents repeated,
inconsistent map literals.

The public projection API should read as a story:

    (-> {}
        (place-trunk world)
        (place-crown world)
        (place-pot world))

Keep clipping, line traversal, ellipse distance, character selection, and
similar mechanics private.

### 9. Preserve behavior carefully

Before changing code:

- Run the full test suite.
- Identify determinism tests.
- Identify structural invariants.
- Identify visual or character-level snapshots.
- Find all consumers of the old data shape and API.

After each structural step:

- Compile the affected namespaces.
- Run focused tests.
- Run the full suite.
- Inspect representative rendered output.

Do not update snapshots simply to silence failures. First inspect the output
and confirm that any visual change is intentional.

When the refactor is intended to preserve appearance, all visual snapshots
should remain unchanged.

### 10. Update all consumers

When changing the model shape, update:

- Renderers
- Controllers
- REPL code
- Tests
- Snapshot tests
- Documentation
- Debug displays and counters
- Any code using old nested paths

Search for every old field and function name after the migration. No stale
references should remain.

### 11. Improve tests around the new story

Tests should verify:

- Empty values document the complete data shape.
- Construction follows the intended stages.
- The same seed produces structurally equal results.
- Every supported style produces valid geometry.
- All coordinates are finite.
- Presentation changes preserve geometry.
- Structural changes regenerate geometry.
- Placement produces valid semantic cells.
- Visual snapshots remain correct.

Prefer tests that access meaningful paths such as:

    [:tree :trunk :segments]
    [:tree :trunk :branch-sites]
    [:tree :crown :branches]
    [:tree :crown :foliage]
    [:tree :crown :tips]
    [:pot :top-row]

### 12. Update the reading guide

Document the new model starting with its public story, followed by its data.

The guide should explain:

    world
      -> calculate part A
      -> calculate part B
      -> place part A
      -> place part B
      -> completed world

Include:

- The complete world or model shape
- Examples of individual geometry values
- Calculation versus placement
- Deterministic randomness
- Which user actions recalculate data
- Which actions only re-project it
- A short REPL walkthrough

## Implementation workflow

1. Inspect the current architecture and call sites.
2. Run and record the baseline tests.
3. Propose the target data shape and public pipeline.
4. Implement the data shape first.
5. Refactor calculation around that shape.
6. Refactor projection around that shape.
7. Rewrite the central model namespace as the high-level story.
8. Simplify configuration validation.
9. Update consumers and tests.
10. Inspect representative output.
11. Update snapshots only when justified.
12. Update documentation.
13. Run the full test suite, build, and git diff --check.

Do not stop after providing recommendations. Implement the refactor and verify
it.

## Final response

Report:

- The new top-level model story
- The new runtime data shape
- Important helper-name and responsibility changes
- Whether output or snapshots changed
- Test and build results
- Any intentional compatibility-breaking API changes
