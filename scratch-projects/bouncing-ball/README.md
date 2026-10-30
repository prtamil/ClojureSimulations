# Bouncing Ball

A Clojure-first elastic-wall simulation. One circular ball moves in continuous
space and reflects from radius-aware boundaries.

## Model story

```text
initial-world -> validate config -> start clock -> create ball
advance-world -> advance clock -> apply elastic ball motion
project-world -> project only the ball -> terminal / Quil
```

- `sim.model.core` owns the world lifecycle, time, reset, and controls.
- `sim.model.ball` earns a separate boundary because consuming overshoot across
  any number of elastic wall impacts is substantial, independently testable
  mathematics. It does not own time, controls, or presentation.
- `sim.model.config` owns adjustable settings and staged validation, not live
  simulation state.
- `project-world` lets a renderer interpolate motion without changing the
  simulation clock or stored truth.

The model contains no unrelated RNG or noise state. Physics is expressed in
seconds, so renderer frame rate does not change the trajectory.

The persistent world shape is visible as `empty-world` and `empty-clock`:

```clojure
{:config {...}
 :seed 2026
 :clock {:tick 0
         :elapsed-seconds 0.0
         :paused? false
         :simulation-fps 60}
 :speed-multiplier 1.0
 :theme-index 0
 :ball {:x 10.0 :y 5.0 :vx 8.49 :vy 8.49 :radius 0.45}}
```

The ball stays an ordinary map. A record or protocol would add machinery
without protecting another invariant.

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
(def after (model/advance-world before 0.5))
(:clock after)
(:ball after)

;; Projection changes only the displayed ball value.
(def projected (model/project-world after 0.01))
(assert (= (dissoc after :ball) (dissoc projected :ball)))
```

See `learning.md` for a guided reading order and a longer executable example.
