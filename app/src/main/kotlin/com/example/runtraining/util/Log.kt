package com.example.runtraining.util

import android.util.Log as AndroidLog

/**
 * Single shared log tag for the app. Use via:
 *   Log.d("imported workout id=$id")
 */
object Log {
    const val TAG = "RunTraining"

    fun v(msg: String) { AndroidLog.v(TAG, msg) }
    fun d(msg: String) { AndroidLog.d(TAG, msg) }
    fun i(msg: String) { AndroidLog.i(TAG, msg) }
    fun w(msg: String, t: Throwable? = null) {
        if (t == null) AndroidLog.w(TAG, msg) else AndroidLog.w(TAG, msg, t)
    }
    fun e(msg: String, t: Throwable? = null) {
        if (t == null) AndroidLog.e(TAG, msg) else AndroidLog.e(TAG, msg, t)
    }
}
