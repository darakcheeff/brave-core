/* Copyright (c) 2021 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.billing;

import android.app.Activity;
import android.content.Context;
import android.text.SpannableString;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;

import org.chromium.build.annotations.NullMarked;
import org.chromium.build.annotations.Nullable;
import org.chromium.chrome.browser.profiles.Profile;

/** No-op stub replacing Google Play In-App Billing wrapper in debloated builds. */
@NullMarked
public class InAppPurchaseWrapper {
    public static final String MANAGE_SUBSCRIPTION_PAGE =
            "https://play.google.com/store/account/subscriptions";
    public static final String VPN_RELEASE_MONTHLY_SUBSCRIPTION = "brave.vpn.monthly";
    public static final String VPN_RELEASE_YEARLY_SUBSCRIPTION = "brave.vpn.yearly";

    private static volatile @Nullable InAppPurchaseWrapper sInAppPurchaseWrapper;
    private static final Object sMutex = new Object();

    public enum SubscriptionType {
        MONTHLY,
        YEARLY
    }

    public enum SubscriptionProduct {
        VPN,
        LEO,
        ORIGIN
    }

    private final MutableLiveData<ProductDetails> mMutableMonthlyProductDetails =
            new MutableLiveData<>();
    private final MutableLiveData<ProductDetails> mMutableYearlyProductDetails =
            new MutableLiveData<>();
    private final MutableLiveData<ProductDetails> mMutableOriginProductDetails =
            new MutableLiveData<>();

    private InAppPurchaseWrapper() {}

    public static InAppPurchaseWrapper getInstance() {
        InAppPurchaseWrapper result = sInAppPurchaseWrapper;
        if (result == null) {
            synchronized (sMutex) {
                result = sInAppPurchaseWrapper;
                if (result == null) sInAppPurchaseWrapper = result = new InAppPurchaseWrapper();
            }
        }
        return result;
    }

    public LiveData<ProductDetails> getOriginProductDetails() {
        return mMutableOriginProductDetails;
    }

    public LiveData<ProductDetails> getMonthlyProductDetails(SubscriptionProduct product) {
        return mMutableMonthlyProductDetails;
    }

    public LiveData<ProductDetails> getYearlyProductDetails(SubscriptionProduct product) {
        return mMutableYearlyProductDetails;
    }

    public boolean isMonthlySubscription(String productId) {
        return false;
    }

    public String getProductId(SubscriptionProduct product, SubscriptionType subscriptionType) {
        return "";
    }

    public String getOriginProductId() {
        return "";
    }

    public void queryOriginProductDetailsAsync() {}

    public void consumeExistingOriginPurchase() {}

    public void queryProductDetailsAsync(SubscriptionProduct product) {}

    public void queryPurchases(
            MutableLiveData<PurchaseModel> mutableActivePurchases, SubscriptionProduct type) {
        mutableActivePurchases.postValue(null);
    }

    public void initiatePurchase(Activity activity, @Nullable ProductDetails productDetails) {}

    public void initiatePurchase(
            Activity activity,
            @Nullable ProductDetails productDetails,
            @Nullable Profile profile) {}

    public void initiatePurchaseInApp(
            Activity activity, @Nullable ProductDetails productDetails) {}

    public void initiatePurchaseInApp(
            Activity activity,
            @Nullable ProductDetails productDetails,
            @Nullable Profile profile) {}

    public void processPurchases(Context context, @Nullable Purchase activePurchase) {}

    public void maybeShowSubscriptionInAppMessages(
            Activity activity, @Nullable Profile profile) {}

    public @Nullable SpannableString getFormattedProductPrice(
            Context context, @Nullable ProductDetails productDetails, int stringRes) {
        return null;
    }

    public @Nullable SpannableString getFormattedFullProductPrice(
            Context context, @Nullable ProductDetails productDetails) {
        return null;
    }

    public int getYearlyDiscountPercentage(
            @Nullable ProductDetails monthlyProductDetails,
            @Nullable ProductDetails yearlyProductDetails) {
        return 0;
    }

    public @Nullable String getFormattedOneTimePurchasePrice(
            @Nullable ProductDetails productDetails) {
        return null;
    }

    public @Nullable String getOneTimePurchaseCurrencyCode(
            @Nullable ProductDetails productDetails) {
        return null;
    }

    public void setSuppressToasts(boolean suppress) {}
}
