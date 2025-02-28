package com.revenuecat.paywallstester

import android.app.Application
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesAreCompletedBy
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.customercenter.CustomerCenterListener

private const val TAG = "MainApplication"

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Purchases.logLevel = LogLevel.VERBOSE

        Purchases.configure(
            PurchasesConfiguration.Builder(this, Constants.GOOGLE_API_KEY)
                .purchasesAreCompletedBy(PurchasesAreCompletedBy.REVENUECAT)
                .appUserID(null)
                .diagnosticsEnabled(true)
                .build(),
        )
        Purchases.sharedInstance.customerCenterListener =
            object : CustomerCenterListener {
                override fun onRestoreStarted() {
                    Log.d(TAG, "CustomerCenterListener: onRestoreStarted called")
                }

                override fun onRestoreCompleted(customerInfo: CustomerInfo) {
                    Log.d(
                        TAG,
                        "CustomerCenterListener: onRestoreCompleted called with customer info: " +
                            customerInfo.originalAppUserId,
                    )
                }

                override fun onRestoreFailed(error: PurchasesError) {
                    Log.d(TAG, "CustomerCenterListener: onRestoreFailed called with error: ${error.message}")
                }

                override fun onManageSubscriptionRequested() {
                    Log.d(TAG, "CustomerCenterListener: onManageSubscriptionRequested called")
                }

                override fun onFeedbackSurveyCompleted(feedbackSurveyOptionId: String) {
                    Log.d(
                        TAG,
                        "CustomerCenterListener: onFeedbackSurveyCompleted called with option ID: " +
                            feedbackSurveyOptionId,
                    )
                }
            }
    }
}
