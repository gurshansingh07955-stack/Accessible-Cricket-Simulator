# GESTURE REDESIGN PLAN — Accessible Cricket Simulator (Android)

> **STATUS (see `HANDOFF.md` section 7 for the full current picture):**
> Section 2 (bowling) is **implemented**, with four deviations from the
> plan as originally written below, all made after real on-device
> TalkBack testing (and one later bug-fix pass):
> 1. The gesture detector tracks a **single active pointer**, not two
>    simultaneous ones — TalkBack's touch-exploration passthrough never
>    delivers two simultaneously-pressed pointers to the app; see
>    `HANDOFF.md` section 7 for why, and `PitchingScreen.kt`'s own doc
>    comment for the full technical explanation.
> 2. **Speed is a real Compose `Slider`**, not a three-tier list pick —
>    this was always what "the web version" meant by a speed control, and
>    `Slider` carries first-class TalkBack support with no custom gesture
>    code needed. It also now lives on the **same screen** as the rest of
>    the aim controls (angle/line/variation/length) rather than a
>    separate step, and its `steps` parameter makes it snap to whole
>    km/h exactly like the web version.
> 3. **Section 2.1's timing mechanic has been dropped entirely** (the
>    "drop it" side of the fork it deliberately left open) — bowling
>    quality is now computed directly from the length drag's own
>    precision and smoothness, via the already-ported
>    `BowlingSystem.computeBowlingQuality`, exactly matching how the web
>    app itself computes it. There is no release/timing step at all
>    anymore.
> 4. **Swing is implemented via the length drag's own curve** (see
>    `BowlingSystem.classifySwing`), matching how the web app derives it
>    from the horizontal curve of its drag path — curving the swipe left
>    reads as IN_SWING, curving it right reads as OUT_SWING. It is no
>    longer a "known v1 simplification"; there is no separate swing
>    gesture.
>
> A gesture-detection bug was also fixed on this pass: the discrete
> left/right/up swipes (angle/line/variation) used to change their value
> exactly once and then silently stop responding to further swipes, because
> the gesture-detection coroutine is launched once for the life of the
> screen and its callbacks had captured `angle`/`line`/`variation` by
> value from the first composition rather than reading them live. See
> `PitchingScreen.kt`'s "WHY DISCRETE SWIPES USED TO STOP WORKING AFTER
> ONE FIRING" doc comment for the full explanation. The length-drag's
> full sweep range was also switched from a fixed, unmeasured pixel guess
> to one that scales to the gesture area's actual measured size, and all
> swipe thresholds now use density-independent (dp) units instead of raw
> pixels.
>
> **Section 3 (batting) is now implemented** for footwork, shot
> selection and intent — see `BattingScreen.kt`'s own doc comment
> ("GESTURE IMPLEMENTATION") for the exact mechanics, and the updated
> section 3 below for what changed from the plan as originally written.
> Batting's timing-window widening (section 5.1) is **deliberately still
> not done** — it needs a real on-device latency measurement first, per
> that section's own caution, not a guessed number.
>
> Section 4 (fielding) is **not started**. Section 5's remaining bundled
> fixes (5.2 crowd-at-toss, 5.3 vibration investigation) are **not
> started**. The rest of this document is the plan largely as originally
> discussed, with section 3 updated to match what actually shipped —
> read `HANDOFF.md` first for what's actually true today.

**Paste this into your working chat to continue implementation.** This
document replaces the previous TalkBack list-pick-based gesture system
for pitching and batting with a new two-finger gesture vocabulary
designed to feel closer to the web app's continuous drag, while staying
compatible with TalkBack. Fielding keeps its existing plan (see section
4).

Read this alongside `HANDOFF.md`, `PORTING_NOTES.md`, and `UI_NOTES.md` —
this plan only concerns gesture *input design* plus a few related
audio/hardware fixes bundled in at the end. It doesn't change the logic
layer, save system, or anything else already documented there.

---

## 1. Core principle behind every gesture below

TalkBack intercepts **single-finger** touch for its own explore/double-tap
model. **Two-finger gestures largely pass through untouched**, giving raw
continuous motion data with no TalkBack interception. This is the
mechanism every gesture below relies on. **(See `HANDOFF.md` section 7 —
"pass through untouched" turned out to mean something more specific than
this plan originally assumed: the app receives a translated
single-pointer stream, not two simultaneous pointers. The user-facing
gesture vocabulary below is unaffected; only the implementation
technique changed.)**

**Gesture vocabulary, consistent across the whole app:**
- **Single-finger tap** → commit / confirm / execute (consistent with existing TalkBack tap-to-activate behavior).
- **Two-finger swipe (directional or continuous drag)** → set / cycle a value.

**Feedback rules for continuous two-finger drags:**
- A **continuous audio tone** (pitch and/or stereo pan) tracks the value live as the finger moves — never interrupted, always live, no speech involved during the drag itself.
- A **spoken announcement fires only when crossing into a new discrete zone** (e.g. entering "good length" from "full"), debounced so jitter right at a zone boundary doesn't cause repeated firing.
- **Never** fire a spoken announcement continuously during a drag — TalkBack's speech queue cannot keep up with rapid updates and produces stuttering, cut-off speech. Continuous feedback must be tonal/haptic, not spoken; discrete zone-crossing feedback is where speech belongs.

---

## 2. Bowling (Pitching Screen)

| Axis | Gesture |
|---|---|
| Angle | Two-finger swipe **left** |
| Line | Two-finger swipe **right** |
| Variation | Two-finger swipe **up** |
| Length | Two-finger swipe **down**, continuous drag — tone feedback live, spoken announcement only at zone crossings (yorker → full → good → short → bouncer) |
| Swing | The length drag's own left/right curve — see status banner above and `BowlingSystem.classifySwing` |
| Speed | Existing slider control (already TalkBack-safe as a single-finger adjustable control — no change needed), now on the same screen as the rest of the aim controls |

### 2.1 — Release / timing mechanic: repurpose, don't discard

**Open question raised and resolved in discussion, documented here for the record:**

Since length (and every other aim axis) is now set directly by swipe, the *original* role of the timing tap — shifting your chosen length away from target based on early/late release — no longer makes sense. Length is now a deliberate pick, not something to "aim for" via timing.

**However, removing timing entirely turns bowling into a fully deterministic settings pick** (choose angle, line, variation, length, speed, done) with no skill layer left at the moment of delivery — which reintroduces the "it's becoming a choice-based game" problem this whole redesign exists to solve.

**Resolution used in this plan: repurpose the timing tap as an execution-quality layer, independent of aim.**
- Your swipes set *intent* (what ball you're trying to bowl).
- The timing tap now determines *execution* — how cleanly you deliver the ball you intended. Suggested effects of timing quality:
  - Extra pace variation (a well-timed release keeps the chosen speed true; a mistimed one adds unwanted variance).
  - Degree of swing/seam movement (better timing = more control over shape).
  - A small chance of the ball drifting off your intended line/length on a poorly timed release (mirrors a real bowler's "the ball just didn't come out right" moment), rather than a guaranteed automatic delivery of exactly what you swiped.
- This mirrors the real skill split in cricket: deciding what to bowl vs. executing it well are two different skills, and the web app's drag-based approach effectively blended both into one motion. Splitting them (swipe = intent, tap = execution) is a legitimate translation, not a downgrade.

**If you'd rather drop timing from bowling entirely once swipe-aiming is in, that's a valid alternative** — it simplifies the mechanic at the cost of losing a skill layer at the point of delivery. Flagged here as the explicit fork in the road; default assumption below is "keep it, repurposed."

> **RESOLVED (see status banner at the top of this document): the "drop
> it" alternative was chosen.** Bowling quality is computed directly from
> the length drag's own precision and smoothness instead — see
> `HANDOFF.md` section 7 for the reasoning.

**Regardless of which resolution is chosen, if timing is kept:**
- **Widen the "Perfect" timing tolerance** (currently ~30–60ms either side of the beat) — exact new value TBD, needs real-device testing.
- Account for touch-input latency under TalkBack (double-tap-to-click adds delay the current fixed schedule doesn't compensate for) — fix this alongside the window widening, not separately, since both affect measured timing accuracy.
- Keep the existing buzz + tick rhythm (4 pulses, accented final pulse) as the base structure — the redesign is about the tolerance window and the *meaning* of the outcome, not the rhythm itself.

---

## 3. Batting

> **STATUS: footwork, shot selection and intent are IMPLEMENTED** in
> `BattingScreen.kt` — see that file's own "GESTURE IMPLEMENTATION" doc
> comment for the exact mechanics and for why each one deviates slightly
> from the plan as originally written below:
> - **Footwork** uses Compose's built-in `combinedClickable` (tap vs
>   long-press on a single "Next ball" control) rather than a hand-rolled
>   two-finger gesture — TalkBack already exposes click and long-click as
>   distinct, natively-supported actions, so no custom timing detection
>   was needed here the way the pitching/shot/intent surfaces need it.
> - **Shot selection** is a continuous two-finger vertical swipe over the
>   already-ported `BattingSystem.classifyShot` (15 equal-height bands,
>   same mechanic as bowling's length classification), with the same
>   tone-tracks-live / speech-only-at-zone-crossings feedback pitching's
>   length drag uses, committed by lifting and then confirmed with an
>   explicit "Play Shot" tap (mirrors pitching's Bowl button gating).
> - **Intent** is a single four-way discrete swipe, structurally
>   identical to pitching's discrete angle/line/variation swipes
>   (one decision per touch, resolved at release if needed), driven by
>   the already-ported `BattingSystem.classifyIntentDirection`. Unlike
>   shot selection it fires and advances immediately — it's a single
>   mutually-exclusive pick, not a browsable range.
>
> **Timing is NOT changed by this pass** — the existing tap-on-the-beat
> mechanic (section 3's original "Timing/release" row below) is
> unchanged, and the widened-window fix (section 5.1) remains **not
> done**, deliberately: it needs a real on-device latency measurement
> first, per that section's own caution.

**Footwork (blind commit, mirrors web app's system):**
- Single tap "Next ball" → front foot
- Double-tap-and-hold "Next ball" → back foot

**Delivery reveal:** unchanged — length, line, variation, angle, speed, and any field-change announcement are read out as they already are.

**Shot selection:**
- Two-finger continuous swipe up/down — same mechanism as bowling's length gesture: live tone feedback as the finger moves, spoken announcement only when crossing into a new shot zone.

**Intent (four-way swipe, follows shot selection as a separate step):**

| Gesture | Intent |
|---|---|
| Two-finger swipe up | Aggressive |
| Two-finger swipe down | Defensive |
| Two-finger swipe left | Grounded aggressive |
| Two-finger swipe right | Step out |

Note: shot-selection's up/down and intent's up/down are the **same physical gesture reused at two different sequential steps** with different meaning — same principle as a gear-dependent pedal. Worth confirming on-device this doesn't confuse players expecting a swipe direction to mean one fixed thing throughout a whole delivery.

**Timing/release:** existing tap-on-the-beat mechanic. Apply the **same widened Perfect-window fix** planned for bowling (section 2.1) here as well — same underlying latency/tolerance issue applies to both surfaces.

> **NOT STARTED.** Bowling's release/timing mechanic was removed entirely
> rather than fixed (see section 2.1's resolution above) — whether that
> same reasoning should extend to batting's timing tap (it has no
> separate "aim" gesture to fall back on the way bowling's length drag
> does) was left as an open question for whoever picked this section up.
> **Resolved when the batting gestures above were implemented: NO** —
> batting's timing tap is kept as-is (it has no separate aim gesture to
> fall back on, unlike bowling's length drag), so it still needs the
> widened-window fix from section 5.1, which remains not done.

---

## 4. Fielding (unchanged from earlier plan)

The existing two-phase pick (fielder, then position) maps well onto Android's **official Accessibility Drag-and-Drop framework** (`ACTION_DRAG_START` / `ACTION_DRAG_DROP` / `ACTION_DRAG_CANCEL`) — the same mechanism the Android home screen uses for TalkBack-accessible icon rearranging. This is separate implementation work from the two-finger swipe system above, since fielding is a discrete "place item in slot" action, not a continuous aim/value gesture. No changes to this part of the plan.

> **NOT STARTED.**

---

## 5. Other fixes bundled into this pass

### 5.1 — Widen the Perfect timing window (bowling execution + batting timing)
- Current tolerance too tight, contributes to the mechanic feeling frustrating rather than skill-based.
- Get a real device timing-latency measurement pass before picking a final number — don't guess a value and ship it blind, given how sensitive the earlier release-timing bug already was to exactly this kind of estimation error.
- Applies to both the bowling execution tap (section 2.1) and the existing batting timing tap.

> **MOOT for bowling** (no timing tap left to widen — see section 2.1).
> **Still applies to batting — NOT done.** Batting's footwork/shot/intent
> gestures were implemented without touching the timing step at all (see
> section 3's status note above); this still needs a real-device
> latency measurement pass before a new tolerance value is picked.

### 5.2 — Crowd ambience should start at the toss, not later
- Currently the crowd bed presumably starts once the match screen itself is active (or later); change the trigger point so ambient crowd sound begins as soon as the **Toss screen** appears, so the experience feels alive from the very first screen of a live match, not just once play starts.

> **NOT STARTED.**

### 5.3 — Vibration not firing / lagging on some devices (reported: Motorola Edge 70 Fusion)
This needs investigation, not just a code tweak, since it's device-specific:
- Confirm whether the app uses the modern `VibrationEffect` API (Android 8.0+) rather than the deprecated raw-duration `vibrate()` call — some OEM skins handle the old API inconsistently.
- Check whether the device has a system-level "vibration intensity" or battery-saver setting suppressing app-requested vibration independent of the app's own in-game Vibration toggle.
- Confirm the app checks `Vibrator.hasVibrator()` and, ideally, `hasAmplitudeControl()`, and gracefully degrades (falling back to the audible tick, which already exists) rather than silently doing nothing if the device can't vibrate as expected.
- This is a device-specific quirk, consistent with other open real-device risks already flagged in the project notes — test on more than one physical device, not just the reporting friend's phone, before considering it fixed.

> **NOT STARTED.**

---

## 6. Suggested implementation order

1. Bowling's four two-finger swipe gestures (angle/line/variation/length) + speed slider — simplest surface, proves the gesture pattern. **DONE.**
2. Decide and implement the bowling timing resolution (section 2.1: repurpose as execution quality, or drop entirely) + widen the Perfect window using real device timing measurements. **DONE — dropped entirely; no widening needed since nothing was kept.**
3. Batting: footwork tap/hold → shot selection swipe → intent four-way swipe, reusing the same tone-feedback pattern built for bowling. **DONE — see `BattingScreen.kt`'s "GESTURE IMPLEMENTATION" doc comment. Untested on a real device with TalkBack, like every other gesture surface in this project.**
4. Apply the same widened timing window fix to batting's existing timing tap. **NOT STARTED — needs a real-device latency measurement pass first (section 5.1).**
5. Crowd-ambience-at-toss trigger change (small, independent of the gesture work — can be done anytime). **NOT STARTED.**
6. Vibration investigation on multiple physical devices (independent workstream, can run in parallel with the above). **NOT STARTED.**
7. Fielding's drag-and-drop framework work (separate, larger effort — sequence last, per the earlier plan). **NOT STARTED.**
8. Swing-via-curve for bowling (section 2's swing row) + speed merged onto the aim screen + fix for the discrete-swipe "changes once then stops" bug. **DONE.**

---

**This document is the plan as originally discussed, updated in place as
each section ships. See the status banners above section 2 and section 3,
and `HANDOFF.md`, for what has actually shipped and what changed along
the way.**
