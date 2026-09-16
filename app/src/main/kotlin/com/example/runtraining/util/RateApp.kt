package com.example.runtraining.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Launches the Play in-app review flow, falling back to the Play Store listing on
 * any failure. Both paths are brokered by the Play Store app, so this needs no
 * INTERNET permission (FR-040 / FR-034).
 */
fun launchRateFlow(activity: Activity) {
    val manager = ReviewManagerFactory.create(activity)
    manager.requestReviewFlow().addOnCompleteListener { task ->
        if (task.isSuccessful) {
            manager.launchReviewFlow(activity, task.result)
                .addOnFailureListener { openPlayStoreListing(activity) }
        } else {
            openPlayStoreListing(activity)
        }
    }
}

private fun openPlayStoreListing(activity: Activity) {
    val pkg = activity.packageName
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
    try {
        activity.startActivity(market)
    } catch (e: ActivityNotFoundException) {
        activity.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")),
        )
    }
}
