/* Copyright (c) 2021 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.crypto_wallet.util;

import org.jni_zero.JNINamespace;

import org.chromium.chrome.browser.profiles.Profile;

@JNINamespace("chrome::android")
public class WalletNativeUtils {
    public static void resetWallet(Profile profile) {
        WalletNativeUtilsJni.get().resetWallet(profile);
    }

    public static boolean isUnstoppableDomainsTld(String domain) {
        return WalletNativeUtilsJni.get().isUnstoppableDomainsTld(domain);
    }

    public static boolean isEnsTld(String domain) {
        return WalletNativeUtilsJni.get().isEnsTld(domain);
    }

    public static boolean isSnsTld(String domain) {
        return WalletNativeUtilsJni.get().isSnsTld(domain);
    }

    interface Natives {
        void resetWallet(Profile profile);

        boolean isUnstoppableDomainsTld(String domain);

        boolean isEnsTld(String domain);

        boolean isSnsTld(String domain);
    }

    static final class WalletNativeUtilsJni {
        private static final Natives INSTANCE =
                new Natives() {
                    @Override
                    public void resetWallet(Profile profile) {}

                    @Override
                    public boolean isUnstoppableDomainsTld(String domain) {
                        return false;
                    }

                    @Override
                    public boolean isEnsTld(String domain) {
                        return false;
                    }

                    @Override
                    public boolean isSnsTld(String domain) {
                        return false;
                    }
                };

        static Natives get() {
            return INSTANCE;
        }
    }
}
