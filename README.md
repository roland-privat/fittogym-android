# Run Training (Android)

Personal Android app for running guided treadmill / pace workouts imported from
`.fit` files. Single user, single device, USB deploy only — see the
[constitution](.specify/memory/constitution.md) for the binding principles.

## Quick start

Full setup, build, and deploy instructions (including how to deploy from VS
Code without Android Studio) live here:

- [Quickstart guide](specs/001-core-workout-flow/quickstart.md)

Once the host machine has JDK 17 + Android SDK 35 set up per the quickstart:

```powershell
.\gradlew.bat installDebug                                       # build + install on USB-connected phone
adb shell am start -n com.example.runtraining/.MainActivity      # launch
adb logcat -v color RunTraining:V *:S                            # tail logs
```

## Design docs

- [Feature spec](specs/001-core-workout-flow/spec.md)
- [Implementation plan](specs/001-core-workout-flow/plan.md)
- [Phase 0 research](specs/001-core-workout-flow/research.md)
- [Phase 1 data model](specs/001-core-workout-flow/data-model.md)
- [Phase 1 contracts](specs/001-core-workout-flow/contracts/)
- [Tasks](specs/001-core-workout-flow/tasks.md)
