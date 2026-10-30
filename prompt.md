# Model Refactoring Prompt

Refactor the model layer of this Clojure project from first principles.

Project: `<PROJECT_PATH>`

Primary model directory: `<MODEL_DIRECTORY>`

## Objective

Make the model read like a clear domain story that a Clojure beginner can
follow without reconstructing behavior from scattered implementation details.

Preserve observable behavior by default. Change behavior, output, or public
APIs only when the improvement is intentional, justified, applied to every
consumer, and reported explicitly.

This is an implementation task. Do not stop after reviewing the code or
proposing a design. Complete the refactor and verify it.

Do not mistake superficial change for simplification. Renaming complicated
functions, adding forwarding wrappers, or moving the same complexity into more
namespaces is not sufficient. Simplify the data flow, responsibilities,
terminology, and public story.

## Audit before editing

Inspect the complete behavior surface:

- Model namespaces and their public APIs
- Runtime data shapes
- Configuration and validation
- Controllers and application state
- Renderers, projections, serializers, or other model consumers
- Tests, deterministic fixtures, and visual snapshots
- Drivers, REPL examples, documentation, and debug displays
- Every call site of fields or functions that may change

Preserve unrelated worktree changes.

Before editing:

1. Run the complete test suite.
2. Run the normal build or compile command.
3. Record deterministic or visual baselines when output must remain stable.
4. Identify existing failures and distinguish them from later regressions.
5. Find invariants, performance-sensitive paths, and compatibility constraints.

## Design standard

### 1. Make the public model read like a domain story

The central model namespace should expose the important workflows directly:

```clojure
(defn construct-world [config seed]
  (-> empty-world
      (start-clock config)
      (create-domain-values config seed)
      prepare-current-view))

(defn advance-world [world seconds]
  (-> world
      (advance-clock seconds)
      (advance-domain-values seconds)
      refresh-derived-values))
```

Initialization, reset, time advancement, regeneration, view changes, and user
actions should read as small stories composed from well-named pure functions.
The top-level workflow should not be obscured by low-level mechanics.

### 2. Use the smallest justified abstraction

A domain noun does not automatically deserve a public function, record,
protocol, or namespace. Use the smallest boundary that expresses the concept
clearly:

```text
data key -> private function -> public function -> namespace
```

Move right only when the concept has independent behavior, protects an
invariant, serves multiple consumers, contains substantial rules or
mathematics, or has a meaningful reason to change separately.

Every abstraction must answer:

- `WHY`: What problem does this boundary solve?
- `DATA`: What values does it own?
- `OPERATIONS`: What can happen to those values?
- `BOUNDARY`: What does it deliberately not own?

For each remaining major namespace or public boundary, capture those answers
in a concise docstring. For a small private helper, its name, inputs, output,
and position in the caller should make the reason obvious without a long
explanation.

Concepts that construct, advance, reset, and disappear together usually belong
as ordinary data and private functions in the same lifecycle namespace.
Separate them only when the new boundary reduces conceptual load.

Remove or inline abstractions that cannot justify themselves. Do not create one
namespace, manager, service, protocol, record, or public API per domain noun.

### 3. Make persistent runtime data explicit

A reader should be able to see the complete persistent world shape near the
model that owns it.

Use visible empty values when they clarify persistent structures:

```clojure
(def empty-clock
  {:tick 0
   :paused? false})

(def empty-world
  {:config nil
   :clock empty-clock
   :entities []
   :view {:mode :default
          :samples {}}})
```

Initialize from those values rather than assembling the world through
unrelated `assoc` calls whose final shape is difficult to discover.

Do not create ceremonial `empty-*` values for transient reductions, private
constructor results, or every small map. Document short dynamic shapes beside
the constructor instead:

```clojure
;; Entity {:position [x y] :velocity [dx dy] :age-seconds 0.0}
```

Prefer domain-shaped data:

```clojure
{:start [x y] :end [x y]}
```

over mechanically shaped data:

```clojure
{:x0 x :y0 y :x1 x :y1 y}
```

Avoid parallel collections whose indexes must remain synchronized. Keep values
that describe one domain object together unless measured performance requires a
different representation.

### 4. Separate calculation from presentation when applicable

The model should calculate renderer-independent domain values. A projection,
rasterization, serialization, or view layer may then translate those values
into a representation for consumers.

Do not introduce a projection layer when the project has no real presentation
boundary. When multiple renderers or consumers share visibility rules, a small
shared projection is justified.

Name projected values after what they represent:

- Use `glyph` for a visible character plus semantic style.
- Use `sample` for a sampled scalar or vector value.
- Use `scene` for the complete renderer-independent visible collection.
- Reserve `cell` for an actual discrete grid slot, such as a terminal or raster
  cell.

Avoid vague names such as `render-repr`, `item`, or `data` when a precise domain
term exists.

Presentation-only changes should not recalculate domain geometry or simulation
state unless the project genuinely requires it. Make calculation and projection
responsibilities explicit in function names and tests.

### 5. Keep functions simple and domain-oriented

Private helpers should perform one understandable action and use plain names.
Low-level traversal, clipping, interpolation, and mathematical mechanics should
remain private so they do not interrupt the public story.

Prefer:

- Plain maps and vectors
- `let` for naming meaningful intermediate values
- Short thread-first pipelines for lifecycle stories
- `assoc`, `update`, and `update-in`
- `map`, `reduce`, and comprehensions when their intent is obvious
- Recursion only when it reflects the domain or makes the algorithm clearer

Avoid:

- Thin wrappers that only rename an existing complicated operation
- Deeply nested anonymous functions
- Excessive threading through implementation details
- Unnecessary transducers, macros, protocols, records, or multimethods
- Context maps passed everywhere without improving understanding

Use a context or settings map when several related functions genuinely share a
large group of inputs. Do not turn every small positional function into a map
API.

### 6. Keep transformations pure and randomness explicit

Use immutable values for model transformations. Keep atoms, I/O, sleeping,
window operations, and other side effects at the application boundary.

Randomness must be deterministic and explicit. Given the same configuration,
seed, inputs, and elapsed steps, the model should compare structurally equal.
Pass random state in and return the next state with the result.

When independent stages should not influence each other accidentally, derive
separate deterministic random streams. Do this only when the independence is a
real domain requirement.

### 7. Keep configuration and validation readable

Configuration should contain adjustable rules, limits, and presentation
settings, not live runtime entities or derived state.

Organize it by domain concepts. Validate it through named stages:

```clojure
(defn validate! [config]
  (-> config
      validate-values!
      validate-ranges!
      validate-relationships!
      validate-render-settings!))
```

Every validation stage should return the unchanged configuration or throw
`ex-info` containing a useful path and the invalid value or related values.

## Migration requirements

When data shapes or public functions change, update all consumers in the same
refactor:

- Renderers and projections
- Controllers and runtime state
- Drivers and REPL helpers
- Tests and fixtures
- Documentation and examples
- Debug displays, counters, and status text
- Build entry points and namespace lists

Do not leave forwarding aliases or compatibility wrappers solely to avoid
finishing the migration unless compatibility is an explicit requirement.

Search for every old field, function, and namespace after the migration. No
stale references should remain.

## Testing and behavior preservation

Prefer tests that describe observable domain behavior and justify important
boundaries. Do not mirror every private helper with a test or create one test
namespace per noun.

Test the relevant guarantees for the project, including:

- Persistent runtime shape
- Construction and lifecycle stages
- Determinism for equal seeds and inputs
- Valid values for every supported mode or style
- Finite coordinates and bounded values
- Pause, reset, resize, and control behavior
- Presentation-only changes preserving calculated state
- Structural changes recalculating the correct state
- Projection shape and visibility rules, when applicable
- Long-running invariants
- Visual or serialized output snapshots

When an unjustified namespace is removed, retain its meaningful contracts as
tests of the public world story.

Do not update a snapshot merely to silence a failure. Inspect the new output
first and update the snapshot only when the visual or serialized change is
intentional.

After each meaningful structural step:

1. Compile the affected namespaces.
2. Run focused tests.
3. Run the full suite at stable checkpoints.
4. Inspect representative output when presentation is involved.

## Documentation requirements

Update the README, reading guide, and REPL examples to describe the model that
actually exists after the refactor.

Document:

- The top-level construction and advancement story
- The complete persistent runtime shape
- Examples of important dynamic values
- Why each major abstraction exists and what it does not own
- Which concepts remain ordinary data instead of separate namespaces
- Calculation versus presentation, when applicable
- Deterministic randomness
- Which user actions recalculate, reset, or only re-project data
- A short executable REPL walkthrough

## Implementation workflow

1. Audit the architecture, behavior surface, and call sites.
2. Establish test, build, deterministic, and visual baselines.
3. Group concepts by lifecycle and identify genuine independent boundaries.
4. Define the target persistent data shape and public domain pipeline.
5. Record `WHY`, `DATA`, `OPERATIONS`, and `BOUNDARY` for every planned major
   abstraction.
6. Remove or inline abstractions that do not justify themselves.
7. Implement the persistent data shape.
8. Refactor calculation around that shape.
9. Add or simplify projection only when consumers require it.
10. Rewrite the central model namespace as the high-level story.
11. Simplify configuration validation.
12. Migrate every consumer and remove the old API.
13. Replace structure-mirroring tests with behavior-focused tests while
    preserving important coverage.
14. Inspect representative output and compare it with the baseline.
15. Update documentation and REPL examples.
16. Run stale-reference searches, the full test suite, the normal build, a
    representative runtime smoke test, and `git diff --check`.

## Completion criteria

The refactor is complete only when:

- The central model reads as a domain story.
- Persistent runtime data is explicit.
- Every major abstraction has a clear reason and boundary.
- Coupled concepts are not split into unnecessary namespaces.
- Calculation and presentation are separated only where useful.
- Randomness remains deterministic and explicit.
- All consumers use the new data shape and API.
- No stale names or compatibility leftovers remain unintentionally.
- Tests, build, and representative runtime checks pass.
- Required output and snapshots remain unchanged, or intentional differences
  are documented.
- Documentation matches the implementation.
- `git diff --check` is clean.

## Final response

Report:

- The new top-level model story
- The new persistent runtime data shape
- Why each remaining major abstraction deserves its boundary
- Which abstractions were removed or inlined, and why
- Important responsibility, terminology, and API changes
- Whether behavior, output, or snapshots changed
- Test, build, and runtime-smoke results
- Any intentional compatibility-breaking changes
