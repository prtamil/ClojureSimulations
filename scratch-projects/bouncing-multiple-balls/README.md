# Bouncing Multiple Balls

A deterministic Clojure simulation of circular bodies with elastic wall and
pairwise collisions.

## Design

```text
seed ---> non-overlapping balls ---> wall motion
                                      |
                               collision passes
                                      |
                                immutable world
                                      |
                           ASCII / terminal / Quil
```

- `sim.model.ball` owns seeded placement, overlap queries, and wall motion.
- `sim.model.collision` owns separation and impulse resolution.
- `sim.model.core` composes substeps, collision passes, lifecycle, and controls.

The separation makes the physics readable without a monolithic indexed loop.
Substeps and collision iterations remain configurable to keep fast bodies
stable, and batched clock updates are sequential composition of the same pure
tick.

## Run

```powershell
clojure -M:run
clojure -M:terminal
clojure -M:gui
clojure -M:test
clojure -M:dev
clojure -T:build jar
clojure -T:build uber
```

## Controls

| Key | Action |
| --- | --- |
| `Space` | Pause / resume |
| `r` | Reset the seeded arrangement |
| `+` / `-` | Increase / decrease speed |
| `t` / `T` | Next / previous theme |
| `]` / `[` | Raise / lower simulation frequency |
| `q`, `Esc`, or `Ctrl-C` | Quit |

## REPL

```clojure
(require '[sim.model.core :as model])

(def before (model/initial-world 42))
(def after (model/update-world-steps before 120 (/ 1.0 60.0)))
(take 3 (:balls after))
(assert (= before (model/initial-world 42)))
```
