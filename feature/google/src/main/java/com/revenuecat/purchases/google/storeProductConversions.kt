package com.revenuecat.purchases.google

import android.util.Log
import com.android.billingclient.api.ProductDetails
import com.revenuecat.purchases.ProductType
import com.revenuecat.purchases.models.GoogleStoreProduct
import com.revenuecat.purchases.models.GoogleSubscriptionOption
import com.revenuecat.purchases.models.Price
import com.revenuecat.purchases.models.PricingPhase
import com.revenuecat.purchases.models.StoreProduct

// In-apps don't have base plan nor offers
fun ProductDetails.toInAppStoreProduct(): StoreProduct = this.toStoreProduct(emptyList(), null)

fun ProductDetails.toStoreProduct(
    offerDetails: List<ProductDetails.SubscriptionOfferDetails>,
    defaultOffer: ProductDetails.SubscriptionOfferDetails?
): GoogleStoreProduct {
    val subscriptionOptions = offerDetails.map { it.toSubscriptionOption(productId, this) }
    val bestOffer = findBestOffer(subscriptionOptions)

    return GoogleStoreProduct(
        productId,
        productType.toRevenueCatProductType(),
        createOneTimeProductPrice(),
        title,
        description,
        offerDetails.firstOrNull { it.isBasePlan }?.subscriptionBillingPeriod,
        offerDetails.map {
            it.toSubscriptionOption(
                productId,
                this
            )
        },
        bestOffer,
        this
    )
}

private fun findBestOffer(subscriptionOptions: List<GoogleSubscriptionOption>): GoogleSubscriptionOption? {
    val basePlan = subscriptionOptions.firstOrNull { it.isBasePlan } ?: return null
    val basePlanPricingPhase = basePlan.pricingPhases.firstOrNull() ?: return null

    val validOffers = subscriptionOptions
        .filter { !it.isBasePlan }
        .filter { !it.tags.contains("rc-ignore-best-offer") }

    // Option 1 - Find longest free pricing phase
    val offerWithLongestFreePricingPhase = validOffers.mapNotNull { offer ->
        // Finds longest free pricing phase for an offer
        offer.pricingPhases.filter { pricingPhase ->
            pricingPhase.priceAmountMicros == 0L
        }.map {
            Pair(offer, parseBillPeriodToDays(it.billingPeriod)) // putting offer in pair here works but feels weird
        }.maxByOrNull { it.second }
    }.maxByOrNull { it.second }?.first

    if (offerWithLongestFreePricingPhase != null) {
        return offerWithLongestFreePricingPhase
    }

    // Option 2 - Find cheapest pricing phase
    //    ex: for $10 per month subscription = $0.35 per day
    //    P1W for $3 for 1 cycle =
    //      $0.42 per day for 7 days is $2.94
    //      $0.35 per day for 49 days is $12.25
    //      TOTAL = $15.19
    //    P1M for $5 for 2 cycle =
    //      $0.17 per day for 56 days is $9.52
    //      TOTAL = 9.52 (BEST OFFER)
    //    P1M for $4 for 1 cycle =
    //      $0.14 per day for 28 days is $3.92
    //      $0.35 per day for 28 days is $9.80
    //      TOTAL = $13.72

    val longestOfferTotalDays = validOffers
        .mapNotNull { subscriptionOption ->
            subscriptionOption.pricingPhases.sumOf { it.totalNumberOfDays() ?: 0 }
        }.maxByOrNull { it } ?: 0

    // Early
    if (longestOfferTotalDays > 0) {
        val periodDays = parseBillPeriodToDays(basePlanPricingPhase.billingPeriod)
        val basePlanCostPerDay = basePlanPricingPhase.priceAmountMicros / periodDays

        Log.d("JOSH", "Offer Details")
        val offersWithCheapestPriceByDay = validOffers.mapNotNull { offer ->
            Log.d("JOSH", "\toffer ${offer.id} ")

            // Get normalized pricing for each phase
            val pricePerDayPerPhase = offer.pricingPhases.mapNotNull { pricingPhase ->
                val periodDays = parseBillPeriodToDays(pricingPhase.billingPeriod)

                if (periodDays > 0) {
                    val pricePerDay = pricingPhase.priceAmountMicros / periodDays
                    val totalDaysOfPhase = pricingPhase.totalNumberOfDays() ?: 0

                    val numberOfDaysOnBasePlan = longestOfferTotalDays - totalDaysOfPhase

                    val costOfPricingPhase = totalDaysOfPhase * pricePerDay
                    val costOfBasePlan = numberOfDaysOnBasePlan * basePlanCostPerDay

                    Log.d("JOSH", "\t\tcostOfPricingPhase=${costOfPricingPhase/1000000.0} for $totalDaysOfPhase days")
                    Log.d("JOSH", "\t\tcostOfBasePlan=${costOfBasePlan/1000000.0} for $numberOfDaysOnBasePlan days")

                    val totes = costOfPricingPhase + costOfBasePlan

                    Pair(pricingPhase, totes)
                } else {
                    null
                }
            }

            // Totals normalized pricing
            var totalPricePerDay = 0L
            pricePerDayPerPhase.forEach { totalPricePerDay += it.second }

            Log.d("JOSH", "\t\tTOTAL=${totalPricePerDay/1000000.0}")

            Pair(offer, totalPricePerDay)
        }.sortedBy { it.second }

        Log.d("JOSH", "Best Normalized Price - $longestOfferTotalDays days")
        offersWithCheapestPriceByDay.forEach {
            Log.d("JOSH", "\t${it.first.id} ${it.second.toFloat() / 1000000}")
        }

        val bestSavingsOffer = offersWithCheapestPriceByDay.firstOrNull()
        if (bestSavingsOffer != null) {
            return bestSavingsOffer.first
        }
    }

    // Option 3 - Return base plan because none
    return basePlan
}

private fun PricingPhase.totalPricePerDayIsh(): Long? {
    val periodDays = parseBillPeriodToDays(billingPeriod)

    return if (periodDays > 0) {
        priceAmountMicros / periodDays
    } else {
        0L
    }
}

private fun PricingPhase.totalNumberOfDays(): Int? {
    val periodDays = parseBillPeriodToDays(billingPeriod)
    val count = billingCycleCount ?: 0

    return if (periodDays > 0 && count > 0) {
        periodDays * count
    } else {
        0
    }
}

// Would use Duration.parse but only available API 26 and up
private fun parseBillPeriodToDays(period: String): Int {
    val regex = "^P(?!\$)(\\d+(?:\\.\\d+)?Y)?(\\d+(?:\\.\\d+)?M)?(\\d+(?:\\.\\d+)?W)?(\\d+(?:\\.\\d+)?D)?(T(?=\\d)(\\d+(?:\\.\\d+)?H)?(\\d+(?:\\.\\d+)?M)?(\\d+(?:\\.\\d+)?S)?)?\$"
        .toRegex()
        .matchEntire(period)

    regex?.let { periodResult ->
        val toInt = fun(part: String): Int {
            return part.dropLast(1).toIntOrNull() ?: 0
        }

        val (year, month, week, day) = periodResult.destructured
        return (toInt(year) * 365) + (toInt(month) * 28) + (toInt(week) * 7) + toInt(day)
    }

    return 0
}

private fun ProductDetails.createOneTimeProductPrice(): Price? {
    return if (productType.toRevenueCatProductType() == ProductType.INAPP) {
        oneTimePurchaseOfferDetails?.let {
            Price(
                it.formattedPrice,
                it.priceAmountMicros,
                it.priceCurrencyCode
            )
        }
    } else null
}

fun List<ProductDetails>.toStoreProducts(): List<StoreProduct> {
    val storeProducts = mutableListOf<StoreProduct>()
    forEach { productDetails ->
        val basePlans = productDetails.subscriptionOfferDetails?.filter { it.isBasePlan } ?: emptyList()

        val offerDetailsBySubPeriod = productDetails.subscriptionOfferDetails?.groupBy {
            it.subscriptionBillingPeriod
        } ?: emptyMap()

        // Maps basePlans to StoreProducts, if any
        // Otherwise, maps productDetail to StoreProduct
        basePlans.takeUnless { it.isEmpty() }?.forEach { basePlan ->
            val basePlanBillingPeriod = basePlan.subscriptionBillingPeriod
            val offerDetailsForBasePlan = offerDetailsBySubPeriod[basePlanBillingPeriod] ?: emptyList()
            val defaultOfferDetails = basePlan // TODOBC5: Change logic here for default offer.

            productDetails.toStoreProduct(offerDetailsForBasePlan, defaultOfferDetails).let {
                storeProducts.add(it)
            }
        } ?: productDetails.toInAppStoreProduct().let { storeProducts.add(it) }
    }
    return storeProducts
}
