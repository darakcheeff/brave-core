/* Copyright (c) 2023 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.rewards.adaptive_captcha;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.browser.BraveRewardsNativeWorker;

/**
 * Device-attestation captcha flow stubbed out for debloated build without Google Play Integrity.
 */
public class AdaptiveCaptchaHelper {
    public static final String TAG = "adaptive_captcha";

    public static void startAttestation(String captchaId, String paymentId) {
        // Disabled in debloated build
    }

    public static void fetchPlayIntegrityToken(String captchaId, String paymentId, String nonce) {
        attestPaymentId(captchaId, paymentId, "", nonce);
    }

    private static void attestPaymentId(
            String captchaId, String paymentId, String integrityToken, String nonce) {
        BraveRewardsNativeWorker worker = BraveRewardsNativeWorker.getInstance();
        if (worker != null) {
            worker.attestPaymentId(
                    captchaId,
                    paymentId,
                    integrityToken,
                    nonce,
                    ContextUtils.getApplicationContext().getPackageName());
        }
    }
}
