package com.revenuecat.apitester.java;

import android.content.Intent;

import androidx.annotation.OptIn;

import com.revenuecat.purchases.ExperimentalPreviewRevenueCatPurchasesAPI;
import com.revenuecat.purchases.deeplinks.DeepLinkHandler;

@OptIn(markerClass = ExperimentalPreviewRevenueCatPurchasesAPI.class)
@SuppressWarnings({"unused"})
final class DeepLinkHandlerAPI {
    static void check(Intent intent, Boolean shouldCache) {
        DeepLinkHandler.HandleResult result = DeepLinkHandler.Companion.handleDeepLink(intent, shouldCache);
    }

    static boolean checkResult(DeepLinkHandler.HandleResult handleResult) {
        switch (handleResult) {
            case HANDLED:
            case IGNORED:
            case DEFERRED_TO_SDK_CONFIGURATION:
                return true;
            default:
                return false;
        }
    }
}
