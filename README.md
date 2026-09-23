# Accessible Cricket Simulator — Android (Kotlin)

Native Kotlin port of the accessibility-first cricket simulator.

## Start here

**Read `HANDOFF.md` first.** It has the project links, exact current
status, how to compile/build (including the one real GitHub-permissions
gotcha), the audio system's encryption key, and next steps. This file is
intentionally short and only kept for anyone landing on the repo without
context.

`PORTING_NOTES.md` and `UI_NOTES.md` are the detailed, file-by-file
records behind everything summarised in `HANDOFF.md`: the pure game-logic
port and everything native to Android (UI, audio, persistence,
security/compatibility hardening), respectively.

## Repo layout

- `app/src/main/java/com/cricketsim/logic/` — the pure Kotlin game-logic
  layer, ported from the web app's `helpers/*.tsx` files. No Android
  dependencies.
- `app/src/main/java/com/cricketsim/ui/` — the Compose UI: setup flow,
  match screen, gesture surfaces, settings, home/about.
- `app/src/main/java/com/cricketsim/audio/` — the sound engine, the
  bundled/encrypted audio pack, and game settings.
- `app/src/main/java/com/cricketsim/persistence/` — save/resume.
- `tools/` — the audio-fetch script and its report.
