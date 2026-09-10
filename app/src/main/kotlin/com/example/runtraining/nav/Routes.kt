package com.example.runtraining.nav

/**
 * Compose Navigation routes. Keep these as constants here so the NavHost and
 * call sites stay in sync.
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val SELECTION = "selection"
    const val DETAILS = "details/{workoutId}"
    const val RUN = "run/{workoutId}"
    const val OPTIONS = "options"
    const val COMPLETE = "complete/{workoutId}?stoppedEarly={stoppedEarly}"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/detail/{resultId}"

    const val ARG_WORKOUT_ID = "workoutId"
    const val ARG_STOPPED_EARLY = "stoppedEarly"
    const val ARG_RESULT_ID = "resultId"

    fun details(workoutId: Long) = "details/$workoutId"
    fun run(workoutId: Long) = "run/$workoutId"
    fun complete(workoutId: Long, stoppedEarly: Boolean = false) =
        "complete/$workoutId?stoppedEarly=$stoppedEarly"
    fun historyDetail(resultId: Long) = "history/detail/$resultId"
}
