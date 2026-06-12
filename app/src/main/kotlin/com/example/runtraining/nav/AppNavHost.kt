package com.example.runtraining.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.runtraining.ui.complete.CompleteScreen
import com.example.runtraining.ui.details.DetailsScreen
import com.example.runtraining.ui.options.OptionsScreen
import com.example.runtraining.ui.run.RunScreen
import com.example.runtraining.ui.selection.SelectionScreen

/**
 * Compose NavHost. Real screens are wired for US1 (Selection / Details /
 * Options). Run + Complete are still placeholders until US2.
 *
 * `startDestination` is decided by MainActivity:
 *  - SELECTION on cold launch (FR-009)
 *  - details/{id} when launched from a FIT intent (FR-007 / FR-008)
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    /** When non-null, DetailsScreen for this workoutId shows the "fresh import" affordance. */
    freshImportId: Long? = null,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Routes.SELECTION) {
            SelectionScreen(
                onTapWorkout = { id -> navController.navigate(Routes.run(id)) },
                onOpenDetails = { id -> navController.navigate(Routes.details(id)) },
                onOpenOptions = { navController.navigate(Routes.OPTIONS) },
            )
        }
        composable(
            route = Routes.DETAILS,
            arguments = listOf(navArgument(Routes.ARG_WORKOUT_ID) { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong(Routes.ARG_WORKOUT_ID) ?: 0L
            val isFresh = freshImportId != null && id == freshImportId
            DetailsScreen(
                isFreshImport = isFresh,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.SELECTION) {
                            popUpTo(Routes.SELECTION) { inclusive = true }
                        }
                    }
                },
                onRun = { rid -> navController.navigate(Routes.run(rid)) },
            )
        }
        composable(
            route = Routes.RUN,
            arguments = listOf(navArgument(Routes.ARG_WORKOUT_ID) { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong(Routes.ARG_WORKOUT_ID) ?: 0L
            RunScreen(
                onBack = { navController.popBackStack() },
                onWorkoutComplete = { wid, stoppedEarly ->
                    navController.navigate(Routes.complete(wid, stoppedEarly)) {
                        popUpTo(Routes.SELECTION)
                    }
                },
            )
        }
        composable(Routes.OPTIONS) {
            OptionsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.COMPLETE,
            arguments = listOf(
                navArgument(Routes.ARG_WORKOUT_ID) { type = NavType.LongType },
                navArgument(Routes.ARG_STOPPED_EARLY) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) {
            CompleteScreen(onDone = {
                navController.navigate(Routes.SELECTION) {
                    popUpTo(Routes.SELECTION) { inclusive = true }
                }
            })
        }
    }
}
