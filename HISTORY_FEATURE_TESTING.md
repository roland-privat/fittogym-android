# Workout History Feature — Testing Guide

## Build Status
✅ **BUILD SUCCESSFUL** — `assembleDebug` completed in 11s with no errors

## Feature Overview
The Workout History feature enables users to:
- **View** all completed and stopped-early workouts in a list
- **Filter** by stopped/completed status
- **Sort** by date (newest first or oldest first)
- **Batch select** multiple results
- **Batch delete** selected results
- **View details** of a single result (metrics, completion status, date/time)
- **Repeat** a workout by navigating back to the workout selection screen
- **Delete** individual results from the detail view

## Quick Test Steps

### 1. Launch app and verify History button
```
✓ App starts to SelectionScreen (list of workouts)
✓ TopAppBar shows two buttons: History (clock icon) and Settings (gear icon)
```

### 2. Start and complete a workout
```
✓ Tap any workout to start a run session
✓ Let the timer run for at least 10 seconds
✓ Tap "Finish" button to complete naturally
✓ Verify completion dialog appears
```

### 3. Navigate to History and verify result saved
```
✓ From SelectionScreen, tap History button
✓ Verify completed workout appears in list
✓ Check that metrics display: planned time, actual time, completion date
✓ Verify "Show stopped" filter is OFF by default (no stopped results shown)
```

### 4. Test batch select and delete
```
✓ In HistoryScreen, tap the checkbox on a result to select it
✓ Additional UI appears: "Select All", "Clear", "Delete X result(s)" button
✓ Tap checkbox again to deselect
✓ Tap "Select All" to select all visible results
✓ Tap "Delete X result(s)" button
✓ Verify result(s) disappear from the list immediately
✓ Verify deleted results do NOT re-appear when you refresh (navigate away and back)
```

### 5. Test sort toggle
```
✓ In HistoryScreen with multiple results, verify default sort is "Newest"
✓ Tap "Newest" button to toggle to "Oldest"
✓ Verify list order reverses
✓ Tap "Oldest" to toggle back to "Newest"
```

### 6. Test filter toggle
```
✓ Start a new workout and manually stop it (tap "Stop Early" button)
✓ Navigate to History
✓ Verify the stopped workout is NOT visible (filter is OFF by default)
✓ Tap "Show stopped" checkbox to enable filter
✓ Verify stopped workout now appears with red "STOPPED" badge
✓ Uncheck "Show stopped"
✓ Verify stopped workout disappears again
```

### 7. Test detail view and repeat
```
✓ In HistoryScreen, tap on any result to open detail view
✓ Verify detail screen shows:
   - Workout name (large, bold)
   - Completion status (✓ Completed or ⚠ Stopped Early)
   - Planned Duration metric card
   - Actual Duration metric card
   - Average Heart Rate (if HRM was connected)
   - TSS (if available)
   - Completion date/time
✓ Tap "Repeat This Workout" button
✓ Verify navigation to SelectionScreen
✓ Tap the same workout again to verify it starts a new session
```

### 8. Test delete from detail view
```
✓ In HistoryDetailScreen, tap "Delete Result" (red button)
✓ Verify immediate navigation back to HistoryScreen
✓ Verify the deleted result no longer appears in the list
```

### 9. Test edge case: Stopped-early workout
```
✓ Start a new workout
✓ Let it run for ~30 seconds
✓ Tap "Stop Early" to stop the session early
✓ Navigate to History
✓ Verify stopped workout appears ONLY when "Show stopped" is checked
✓ Verify detail view shows "⚠ Stopped Early" status
✓ Verify actual duration is shorter than planned
```

## Database Verification

If you want to verify results are actually persisted in the database (Android Studio):

1. Open Android Studio > Device File Explorer
2. Navigate to: `/data/data/com.example.runtraining/databases/`
3. Download `training_app.db`
4. Open in DB Browser for SQLite (or similar)
5. Query: `SELECT * FROM workout_result;`
   - Should show all saved results with correct data

## Known Issues / Notes

### Cosmetic deprecation warnings (safe to ignore):
- `Icons.Filled.ArrowBack` will show yellow warning; can upgrade to `Icons.AutoMirrored.Filled.ArrowBack` later
- `Divider()` will show yellow warning; can upgrade to `HorizontalDivider()` later

### Potential edge cases to test manually:
- **Very long workout names**: Verify text wraps or truncates gracefully in list
- **Many results (100+)**: Verify scroll performance is smooth
- **Delete while list updating**: Rapid deletions should queue properly (handled by coroutine scope)
- **Repeat a workout, then delete that workout from selection**: Should handle gracefully (currently navigates to SELECTION; enhancement: show dialog if workout no longer exists)

## Architecture Summary

```
WorkoutForegroundService (runs workout)
    ↓
    RunSessionEngine.getResultForSaving() (extracts metrics)
    ↓
    WorkoutResultRepository.save() (persists to DB)
    ↓
    WorkoutResultEntity (stored in Room DB)
    ↓
    WorkoutResultDao (CRUD queries)
    ↓
    HistoryScreen (reads from Flow<List>)
    ↓
    HistoryDetailScreen (shows single result)
    ↓
    User actions: delete, repeat, back
```

All flow is reactive (Flow-based), so the UI updates automatically when results are deleted.

## Files Modified

- `app/src/main/kotlin/com/example/runtraining/persistence/db/entities/WorkoutResultEntity.kt` ✨ **NEW**
- `app/src/main/kotlin/com/example/runtraining/persistence/db/WorkoutResultDao.kt` ✨ **NEW**
- `app/src/main/kotlin/com/example/runtraining/persistence/WorkoutResultRepository.kt` ✨ **NEW**
- `app/src/main/kotlin/com/example/runtraining/ui/screens/HistoryScreen.kt` ✨ **NEW**
- `app/src/main/kotlin/com/example/runtraining/ui/screens/HistoryDetailScreen.kt` ✨ **NEW**
- `app/src/main/kotlin/com/example/runtraining/persistence/db/RunTrainingDatabase.kt` (added migration, added DAO)
- `app/src/main/kotlin/com/example/runtraining/RunTrainingApp.kt` (added workoutResultRepository)
- `app/src/main/kotlin/com/example/runtraining/service/WorkoutForegroundService.kt` (save results on completion)
- `app/src/main/kotlin/com/example/runtraining/run/RunSessionEngine.kt` (expose completion metrics)
- `app/src/main/kotlin/com/example/runtraining/nav/Routes.kt` (added HISTORY routes)
- `app/src/main/kotlin/com/example/runtraining/nav/AppNavHost.kt` (added History composables)
- `app/src/main/kotlin/com/example/runtraining/ui/selection/SelectionScreen.kt` (added History button + callback)

## Next Phase (Future Work)

- [ ] Toast/SnackBar feedback on delete success
- [ ] Loading states and shimmer placeholders
- [ ] Empty state messaging
- [ ] Upgrade deprecated icon references
- [ ] Handle deleted workout case in repeat flow (show dialog)
- [ ] Auto-scroll to workout in SelectionScreen when repeating
- [ ] Export history to CSV
- [ ] Advanced filtering (date range, HR range, TSS range)
