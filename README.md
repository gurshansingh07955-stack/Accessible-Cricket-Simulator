# Accessible Cricket Simulator — Android (Kotlin)

Native Kotlin port of the accessibility-first cricket simulator.

## Status: logic-layer port in progress

This is a **from-scratch native rewrite**, not a mechanical transpile of the
original Floot/React web app. The web app remains the source of truth for
gameplay design and balance. See `PORTING_NOTES.md` for exactly what has
been ported so far, what's deliberately left for later, and why a straight
line-by-line translation isn't possible for the UI/audio/accessibility
layers (only the pure game-logic layer ports cleanly).

## Repo layout (so far)

- `app/src/main/java/com/cricketsim/logic/` — pure Kotlin data classes and
  functions ported from the web app's `helpers/*.tsx` files. No Android
  dependencies — this package is plain Kotlin and unit-testable on its own.
- `PORTING_NOTES.md` — running log of what's been ported, file by file,
  with the original web source file it corresponds to.
