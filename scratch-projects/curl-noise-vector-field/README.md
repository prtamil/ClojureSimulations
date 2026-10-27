# Curl Noise Vector Field

A Clojure/Quil port of `curl_noise_vector_field.c`, organized around the
repository's simulation framework:

```text
model -> ascii -> repl -> driver -> quil
```

The domain is divided by responsibility: `sim.random` supplies a deterministic
random stream, `sim.noise` implements Perlin/fBm/curl sampling, and `sim.model`
owns world lifecycle, particle advection, trail state, and pattern evolution.
Simulation parameters live in `sim.config`, while RGB palettes live in
`sim.theme`. The world is plain immutable Clojure data, so it can be explored
without opening a window. Quil and ASCII are adapters over that state.

## Configuration

Every world carries the configuration used to create it. Start with
`sim.config/default-config` and use `assoc-in` to make an experimental variant:

```clojure
(require '[sim.config :as config]
         '[sim.model :as model])

(def small-config
  (-> config/default-config
      (assoc-in [:grid :width] 48)
      (assoc-in [:grid :height] 28)
      (assoc-in [:particles :count] 64)))

(def small-world (model/initial-world small-config 42))
```

The two-argument form accepts configuration plus seed. Existing calls such as
`(model/initial-world 42)` continue to use the default configuration.

## Runtime architecture

`sim.runtime/world` is the one process-level atom shared by the driver and the
Quil window. The value stored in it is still immutable. This means REPL calls
such as `(driver/next-pattern!)`, `(driver/step! 10)`, and
`(driver/reset-world!)` immediately affect an open GUI.

Keyboard input is translated into data commands by the pure `sim.controller`
namespace. Quil only handles the graphical side effect of closing the window.
The model and its tests therefore do not need to load Quil.

Quil renders at a steady frame rate while `sim.clock` accumulates elapsed wall
time and emits one fixed-step batch for the model. The clock is a small pure
function over integer nanoseconds; the expensive work belongs to the model,
where it can be batched deliberately. Long stalls are capped at the configured
maximum delta, preventing a large catch-up burst. Closing the window releases
the runtime GUI flag, allowing `(driver/start-gui!)` to be called again in the
same REPL session.

Every pattern consumes the same `{tick count, fixed dt}` batch. Particle views
still integrate every intermediate position, but decay their full trail grid
only once for the batch. Static views (`VECTOR`, `POTENTIAL`, and `CURL MAG`)
advance by the same elapsed simulation time and rebuild their final grid once.
This preserves each pattern's required mathematics while avoiding redundant
whole-grid work and catch-up spirals.

## Run

From this directory:

```powershell
# Small headless preview
clojure -M:run

# Interactive Quil window
clojure -M -e "(require '[driver :as d]) (d/start-gui!)"

# REPL helpers
clojure -M:dev

# Tests
clojure -M:test
```

## Controls

| Key | Action |
| --- | --- |
| `n` / `p` | Next / previous visualization |
| `t` / `T` | Next / previous color theme |
| `r` | Reseed and reset the field |
| `Space` | Pause / resume |
| `+` / `-` | Double / halve particle speed and field drift |
| `]` / `[` | Raise / lower the simulation rate |
| `q` or `Esc` | Close the Quil window |

The five views are `PARTICLES`, `VECTOR`, `POTENTIAL`, `CURL MAG`, and
`WARPED`. The first and last advect particles; the others repaint a sampled
view of the same scalar field and its curl.

