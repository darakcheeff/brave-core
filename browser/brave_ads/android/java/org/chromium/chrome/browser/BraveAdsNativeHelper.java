/**
 * Copyright (c) 2019 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package org.chromium.chrome.browser;

import org.jni_zero.CalledByNative;
import org.jni_zero.JNINamespace;

import org.chromium.chrome.browser.profiles.Profile;

@JNINamespace("brave_ads")
public class BraveAdsNativeHelper {
    private BraveAdsNativeHelper() {}

    public static boolean nativeIsSupportedRegion(Profile profile) {
        return BraveAdsNativeHelperJni.get().isSupportedRegion(profile);
    }

    public static void nativeClearData(Profile profile) {
        BraveAdsNativeHelperJni.get().clearData(profile);
    }

    public static void nativeOnNotificationAdShown(Profile profile, String jNotificationId) {
        BraveAdsNativeHelperJni.get().onNotificationAdShown(profile, jNotificationId);
    }

    public static void nativeOnNotificationAdClosed(
            Profile profile, String jNotificationId, boolean jByUser) {
        BraveAdsNativeHelperJni.get().onNotificationAdClosed(profile, jNotificationId, jByUser);
    }

    public static void nativeOnNotificationAdClicked(Profile profile, String jNotificationId) {
        BraveAdsNativeHelperJni.get().onNotificationAdClicked(profile, jNotificationId);
    }

    @CalledByNative
    private static void jniStub() {}

    interface Natives {
        boolean isSupportedRegion(Profile profile);

        void clearData(Profile profile);

        void onNotificationAdShown(Profile profile, String jNotificationId);

        void onNotificationAdClosed(Profile profile, String jNotificationId, boolean jByUser);

        void onNotificationAdClicked(Profile profile, String jNotificationId);
    }

    static final class BraveAdsNativeHelperJni {
        private static final Natives INSTANCE =
                new Natives() {
                    @Override
                    public boolean isSupportedRegion(Profile profile) {
                        return false;
                    }

                    @Override
                    public void clearData(Profile profile) {}

                    @Override
                    public void onNotificationAdShown(Profile profile, String jNotificationId) {}

                    @Override
                    public void onNotificationAdClosed(
                            Profile profile, String jNotificationId, boolean jByUser) {}

                    @Override
                    public void onNotificationAdClicked(
                            Profile profile, String jNotificationId) {}
                };

        static Natives get() {
            return INSTANCE;
        }
    }
}
