# Matrix Rain

A deterministic falling-code simulation built on the repository's reference
architecture. Streams enter above the field, move at independent speeds,
mutate their glyphs, leave a variable-length trail, and respawn after the tail
has cleared the bottom.

```text
config/random/noise -> model -> ascii -> repl -> driver -> quil
                     theme -> model/runtime ----------^
                              clock -> runtime --------^
                         controller ------------------^
```

## Responsibilities

| Namespace | Responsibility |
| --- | --- |
| `sim.config` | Validated grid, rain, clock, noise, and render settings |
| `sim.random` | Explicit deterministic SplitMix64 stream |
| `sim.noise` | Seeded Perlin/fBm utility for derived effects |
| `sim.clock` | Wall time accumulated into fixed simulation ticks |
| `sim.model` | Stream spawning, movement, glyph mutation, and respawning |
| `sim.ascii` | Pure headless projection of model-owned glyphs |
| `sim.theme` | Matrix and alternate color palettes |
| `sim.controller` | Pure keyboard commands |
| `sim.runtime` | Shared world atom and GUI lifecycle |
| `driver` | Stable REPL, terminal-animation, and GUI helpers |
| `sim.quil` | Graphical text renderer over the shared world |

## Deterministic design

The original sketch generated drops with global `rand-*` calls and generated
new ASCII characters during rendering. That made identical worlds display
differently. In this version, every drop stores its glyph vector and all random
choices consume the world's explicit RNG state. Therefore:

- equal config and seed produce equal worlds;
- rendering the same world always produces the same output;
- batched updates preserve every intermediate mutation and respawn;
- failures can be replayed exactly from their seed.

Each drop contains its identity, column, continuous head position, speed,
length, and glyphs. The glyph at depth zero is the bright head; deeper glyphs
are mapped through progressively darker theme bands by Quil.

## REPL-first workflow

The comment guides in `sim.model`, `sim.ascii`, and `repl` start with copyable
`require` forms:

```clojure
(require '[sim.model :as model]
         '[sim.ascii :as ascii]
         :reload)

(def world (model/initial-world 42))
(def later (model/update-world-steps world 60 (/ 1.0 60.0)))
(ascii/render later)
```

Run focused tests from the development REPL:

```clojure
(require '[clojure.test :as test]
         '[sim.model-test]
         :reload)

(test/run-tests 'sim.model-test)
```

## Configuration

```clojure
(require '[sim.config :as config]
         '[sim.model :as model])

(def compact-config
  (-> config/default-config
      (assoc-in [:grid :width] 24)
      (assoc-in [:grid :height] 12)
      (assoc-in [:rain :drop-count] 8)
      (assoc-in [:rain :length :max] 10)))

(def compact-world (model/initial-world compact-config 42))
```

The charset, stream count, trail length, speed, spawn depth, mutation
probability, and runtime rates are all configurable and validated.

## Run

```powershell
# Headless ASCII preview
clojure -M:run

# Quil window
clojure -M -e "(require '[driver :as d]) (d/start-gui!)"

# Development nREPL
clojure -M:dev

# Tests
clojure -M:test

# JAR
clojure -T:build jar

# Self-contained executable JAR
clojure -T:build uber
java -jar target/matrix-rain-0.1.0-standalone.jar
```

## Controls

| Key | Action |
| --- | --- |
| `r` | Reset using the current config and seed |
| `Space` | Pause or resume |
| `t` / `T` | Next or previous theme |
| `+` / `-` | Double or halve rain speed |
| `]` / `[` | Raise or lower simulation tick rate |
| `q` or `Esc` | Close the Quil window |
