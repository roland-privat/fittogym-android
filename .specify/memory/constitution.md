<!--
SYNC IMPACT REPORT
==================
Version change: 1.0.0 → 2.0.0
Rationale: MAJOR — Play Store distribution is now permitted. Principle IV is
relaxed to allow a release variant and Play upload; the Distribution section
is updated to allow Play alongside USB deploy. Multi-user support is still
out of scope.

Modified principles:
- IV. Reproducible Local Build & USB Deploy → broadened to allow an
  optional release variant + signed AAB, while still requiring that the
  `gradlew installDebug` clean-clone path keeps working without secrets.

Modified sections:
- Additional Constraints → Distribution: Play Store path explicitly
  permitted; signing material (keystore) MUST stay out of the repo.

Templates requiring updates:
- ✅ .specify/templates/plan-template.md — Constitution Check still
  references the 5 principles; wording unchanged.
- ⚠ docs/play-store-checklist.md — NEW, lives outside .specify/, manually
  maintained per release.
- ⚠ docs/privacy-policy.md — NEW, must be hosted at a public URL before
  Play upload.

Deferred items: none. The Personal Single-User Scope principle (I) is
unchanged — a Play-published version is still a single-user app per
install; there is no account, no multi-device sync, no shared library.
-->

# Android Run Training App Constitution

## Core Principles

### I. Personal Single-User Scope (NON-NEGOTIABLE)

The app exists to support exactly one user — the developer — on the developer's own
Android device. It MUST NOT add user accounts, authentication flows, multi-profile
data partitioning, role checks, or any infrastructure whose only purpose is to
support additional users. Decisions optimize for "works for me on my phone." Anything
that would only matter for a second user (Play Store distribution, account recovery,
GDPR consent dialogs, server-side identity, A/B testing, crash-reporting opt-in
dialogs) is explicitly out of scope and MUST be rejected during planning unless this
constitution is amended first.

**Rationale**: This is a personal hobby app. Pretending otherwise adds large amounts
of code, complexity, and review surface that delivers no value to the only user.

### II. Local-First & Privacy by Default

All workout data — heart rate samples, imported FIT files, derived metrics, session
state — MUST stay on the device. No telemetry, analytics, crash reporting to third
parties, or background sync. The app MUST function with airplane mode enabled. Any
feature that requires network access MUST be explicitly justified in its spec and
plan (including which host is contacted, what payload, and why it cannot be done
on-device); absent that justification, network access MUST NOT be added. The
`INTERNET` permission MUST NOT be declared in the manifest until a network-using
feature is approved.

**Rationale**: Personal health data should not leave the device. Local-first also
removes whole classes of failure (auth, tokens, server downtime, schema drift) that
would dwarf the actual app.

### III. Simplicity & YAGNI

The project MUST remain a single Android Gradle module (`app/`) unless a concrete,
already-needed second consumer of code is identified. Abstractions (interfaces,
repositories, use-case layers, dependency-injection containers, multi-module splits,
custom Gradle plugins) MUST be introduced only when at least two real call sites
exist or the alternative is demonstrably more code. Third-party libraries MUST be
justified per addition (what concrete code volume / correctness risk they remove).
Prefer the Android platform API and Kotlin standard library over wrappers.

**Rationale**: A personal app with one user has a tiny budget for accidental
complexity. YAGNI is a hard rule, not a suggestion.

### IV. Reproducible Local Build & Distribution

The canonical *development* deployment target is the developer's USB-connected
Android phone (or an emulator) via `adb`. The build MUST succeed from a clean
clone with `./gradlew installDebug` (or the platform-equivalent wrapper
invocation) without any secrets, signing config, private Maven repositories, or
environment variables beyond `ANDROID_HOME`/JDK. The Gradle wrapper,
`gradle/libs.versions.toml` (or equivalent version catalog), and pinned
`minSdk`/`targetSdk`/`compileSdk` MUST be committed.

A signed `release` build variant is **permitted** for Play Store distribution.
Signing material (keystore + passwords) MUST be stored outside the repository
and referenced via a gitignored `keystore.properties` file. When that file is
absent (clean clones, CI without secrets, contributors) the release variant
MUST gracefully fall back to the debug keystore so `assembleRelease` still
completes for verification — the resulting AAB is then **not** uploadable to
Play. Release-only configuration (ProGuard/R8 rules, resource shrinking,
icon assets) is committed alongside the source.

**Rationale**: The first version of this constitution forbade any release
variant because there was no plan to publish. That changed once the app
matured. The amended principle still protects the clean-clone build path
(no secrets ever required to compile) and still keeps signing material
off-repo.

### V. Hardware Integration Honesty

Three integrations are first-class concerns and MUST each ship with a written manual
test recipe (precondition → steps → expected outcome) in the feature's `quickstart.md`
or `tests/manual/` notes, because automated tests for them are impractical:

1. Broadcast heart rate monitor connection (BLE GATT Heart Rate Service `0x180D`,
   and/or ANT+ if explicitly added later).
2. Receiving `.fit` files via Android share / open intents from other apps
   (WhatsApp, Files, Drive, etc.) using `ACTION_SEND` / `ACTION_VIEW` with the
   appropriate MIME and a `FileProvider` for outbound paths.
3. Immersive fullscreen behavior (status/navigation bars hidden, screen kept on
   during an active workout).

Each integration MUST degrade gracefully on the predictable failure modes —
permission denied, Bluetooth off, no HRM in range, malformed/oversized FIT file,
unsupported FIT message, screen-off interruption — by surfacing a visible
non-crashing state. Silent failure is forbidden; crashing on these paths is a
release blocker.

**Rationale**: These three flows are the entire reason the app exists. They cannot
be hidden behind unit tests, so the discipline shifts to documented manual recipes
and explicit failure handling.

## Additional Constraints

**Platform & language**

- Language: Kotlin (latest stable supported by the chosen AGP).
- UI: Jetpack Compose preferred; Views/XML allowed only where Compose lacks a
  reasonable equivalent (e.g., specific system integrations).
- `minSdk` MUST be chosen as the lowest level that still gives stable BLE GATT and
  the new Bluetooth runtime permissions model; the chosen value MUST be recorded in
  the plan and not lowered later without amendment.
- `targetSdk` MUST be the latest stable Android API level at the time of each
  feature's plan.

**Permissions**

- Only request permissions actually used by an implemented feature in the current
  branch. Speculative permissions MUST NOT be declared.
- For HRM: use the Android 12+ runtime permission model (`BLUETOOTH_SCAN`,
  `BLUETOOTH_CONNECT`, with `neverForLocation` where appropriate). Legacy
  `ACCESS_FINE_LOCATION` MUST NOT be requested unless a documented device target
  requires it.
- For FIT share/open: use `FileProvider` and scoped storage; do not request
  legacy storage permissions.

**Fullscreen & screen state**

- The active-workout screen MUST request sustained-screen-on
  (`WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` or the Compose equivalent) and
  immersive sticky behavior. Non-workout screens MUST NOT.

**FIT file handling**

- Parsing MUST tolerate truncated and unknown FIT messages without crashing.
- Imported files MUST be copied into app-private storage on import; the app MUST
  NOT depend on the source `Uri` remaining valid.

**Distribution**

- Both **USB sideload** (debug or signed-release APK via `adb install`,
  `gradlew installDebug`, `gradlew installRelease`) and **Google Play Store**
  (signed AAB via `gradlew bundleRelease`) are permitted distribution
  channels.
- The Play Store path additionally requires:
  - A privacy policy hosted at a public URL (see `docs/privacy-policy.md`).
  - Permission justifications for `SYSTEM_ALERT_WINDOW`,
    `FOREGROUND_SERVICE_SPECIAL_USE`, and the BLE permissions (see
    `docs/play-store-checklist.md`).
  - A signed release variant produced from a keystore that is NEVER
    committed.
- The Play-published variant remains a **single-user-per-install** app. No
  accounts, no off-device sync, no shared libraries — Principle I and II
  are unchanged.

## Development Workflow

- Single long-lived branch is acceptable for solo work; feature branches per
  Spec Kit (`/speckit.specify` etc.) are used when a feature warrants tracked
  spec/plan/tasks artifacts.
- Every feature plan MUST include a `Constitution Check` section that lists each
  of the five principles above and states either "pass" or a justified entry under
  `Complexity Tracking`.
- Manual device testing is the canonical verification gate. Unit tests are OPTIONAL
  and added only where they cover non-trivial pure logic (e.g., FIT parsing,
  workout-target math, BLE frame decoding).
- Commit messages SHOULD describe intent. No external code review process is
  required for solo work; self-review against the principles is.
- Before merging or amending, the developer MUST manually verify the three
  integrations from Principle V still work on the USB-connected device, if the
  change could plausibly affect them.

## Governance

This constitution supersedes ad-hoc decisions, comments in code, and informal chat
history. Where a plan or task conflicts with the constitution, the constitution
wins and the plan MUST be revised or the constitution MUST be amended first.

**Amendment procedure**

1. Edit `.specify/memory/constitution.md` with the proposed change.
2. Update the Sync Impact Report comment at the top.
3. Bump `Version` per semantic-versioning rules below.
4. Set `Last Amended` to today's date (ISO `YYYY-MM-DD`); leave `Ratified`
   unchanged.
5. Propagate any changes into `.specify/templates/*` so downstream commands stay
   consistent.

**Versioning policy**

- MAJOR: remove or redefine a principle, or change governance in a backward-
  incompatible way (e.g., allowing Play Store distribution, allowing cloud sync,
  introducing multi-user support).
- MINOR: add a new principle, add a new mandatory section, or materially expand
  guidance under an existing principle.
- PATCH: wording, clarifications, typo fixes, non-semantic refinements that do
  not change what is permitted or forbidden.

**Compliance review**

Each `/speckit.plan` invocation MUST run the Constitution Check gate and MUST
NOT proceed to Phase 1 design if any principle is violated without an entry in
`Complexity Tracking` that names the simpler alternative considered and why it
was rejected. The Hardware Integration Honesty manual-test recipes (Principle V)
MUST be present before a feature touching HRM, FIT intents, or fullscreen is
considered done.

**Version**: 2.0.0 | **Ratified**: 2026-05-25 | **Last Amended**: 2026-05-27
