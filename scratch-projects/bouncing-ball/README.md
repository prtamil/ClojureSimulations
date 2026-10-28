# Bouncing Ball

A Clojure-first elastic-wall simulation. One circular ball moves in continuous
space and reflects from radius-aware boundaries.

## Design

```text
direction + speed ---> ball motion ---> immutable world
                                          |
                              ASCII / terminal / Quil
```

- `sim.model.ball` creates and advances the ball, including any number of wall
  impacts in a long elapsed-time step.
- `sim.model.core` composes lifecycle, time, pause/reset, and controls.
- `project-world` provides interpolation without changing simulation truth.

The model contains no unrelated RNG or noise state. Physics is expressed in
seconds, so renderer frame rate does not change the trajectory.

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
| `r` | Reset |
| `+` / `-` | Increase / decrease speed |
| `t` / `T` | Next / previous theme |
| `]` / `[` | Raise / lower simulation frequency |
| `q`, `Esc`, or `Ctrl-C` | Quit |

## REPL

```clojure
(require '[sim.model.core :as model])

(def before (model/initial-world 42))
(def after (model/update-world before 0.5))
(:ball after)

;; Projection changes only the displayed ball value.
(def projected (model/project-world after 0.01))
(assert (= (dissoc after :ball) (dissoc projected :ball)))
```
