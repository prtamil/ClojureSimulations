# Bouncing Multiple Balls

A deterministic Clojure simulation of circular bodies with elastic wall and
pairwise collisions.

## Model story

```text
seed ---> non-overlapping balls ---> wall motion
                                      |
                               collision passes
                                      |
                                immutable world
                                      |
                           ASCII / terminal / Quil
```

- `sim.model.core` owns construction, time, controls, substeps, collision-pass
  policy, and reset.
- `sim.model.ball` earns a boundary because deterministic non-overlapping spawn
  and radius-aware multi-impact wall motion are substantial shared rules.
- `sim.model.collision` earns a boundary because mass-aware separation and
  impulse conservation are independently testable physics. It does not choose
  iteration counts or own time.
- `sim.model.config` owns adjustable settings and staged validation, not live
  state.

The separation makes the physics readable without a monolithic indexed loop.
Substeps and collision iterations remain configurable to keep fast bodies
stable, and batched clock updates are sequential composition of the same pure
tick.

The persistent world shape is explicit in `empty-world` and `empty-clock`:

```clojure
{:config {...}
 :seed 2026
 :rng-state 545291967
 :clock {:tick 0
         :elapsed-seconds 0.0
         :paused? false
         :simulation-fps 60}
 :speed-multiplier 1.0
 :theme-index 0
 :balls [{:id 0 :x 12.3 :y 7.4 :vx 5.1 :vy -2.2
          :radius 0.5 :mass 0.25 :band-index 2}]}
```

Each ball remains an ordinary map. Runtime mutation and rendering stay outside
the pure model.

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
(def after (model/advance-world-steps before 120 (/ 1.0 60.0)))
(:clock after)
(take 3 (:balls after))
(assert (= before (model/initial-world 42)))
```

See `learning.md` for the reading order and boundary rationale.
