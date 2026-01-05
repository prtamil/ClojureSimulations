# Simulation Practice (Clojure)

This repository is a REPL-first simulation playground focused on improving systems thinking, state modeling, and time-based reasoning using Clojure for my Hobby Simulation Projects.

The goal is not animation-first or problem-solving puzzles, but building correct models, validating them headlessly, and only then visualizing them.

## High-level workflow

The intended development flow is:

model → ascii → repl → driver → quil

1. Model the system as pure data + update rules
2. Validate correctness using ASCII / headless rendering
3. Explore behavior interactively in the REPL
4. Stabilize controls in a driver namespace
5. Visualize the already-correct system using Quil

Each folder exists to support one cognitive mode.

## Design principles

* Model before rendering
* ASCII before animation
* REPL before abstraction
* Promotion, not duplication (move proven ideas into sim or driver)
* One responsibility per namespace

## Why this structure exists

This structure mirrors real-world systems work:

* Simulation engines
* Distributed systems modeling
* State machines
* Event-driven architectures

It is intentionally optimized for:

* Systems thinking
* Time-based reasoning
* State management
* Cognitive clarity

## Summary

* sim = truth
* ascii = correctness
* repl = thinking
* driver = control
* quil = visualization
* build = distribute
