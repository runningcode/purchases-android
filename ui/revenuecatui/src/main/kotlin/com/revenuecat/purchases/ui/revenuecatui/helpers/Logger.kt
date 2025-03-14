package com.revenuecat.purchases.ui.revenuecatui.helpers

import android.util.Log

internal object Logger {
    private const val TAG = "[Purchases]"

    // TODO-PAYWALLS, allow hooking up a custom log handler
    fun e(message: String) {
        Log.e(TAG, message)
    }

    fun e(message: String, throwable: Throwable) {
        Log.e(TAG, message, throwable)
    }

    fun w(message: String) {
        Log.w(TAG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun d(message: String) {
        Log.d(TAG, message)
    }
}
