/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.tor;

import android.net.Uri;

import org.chromium.base.BravePreferenceKeys;
import org.chromium.chrome.browser.preferences.ChromeSharedPreferences;

/**
 * Controller for managing Tor proxy routing, .onion domain interception,
 * and Onion-Location header redirects on Android.
 */
public final class BraveTorProxyController {
    public static final String ONION_TLD = ".onion";
    public static final String HEADER_ONION_LOCATION = "onion-location";

    private BraveTorProxyController() {}

    /**
     * Checks if a URL points to a Tor hidden service (.onion domain).
     */
    public static boolean isOnionUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            return host != null && host.toLowerCase().endsWith(ONION_TLD);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Checks if automatic routing for .onion domains is enabled in preferences.
     */
    public static boolean isAutoOnionEnabled() {
        return ChromeSharedPreferences.getInstance()
                .readBoolean(BravePreferenceKeys.BRAVE_TOR_AUTO_ONION, true);
    }

    /**
     * Checks if Tor integration master switch is enabled in preferences.
     */
    public static boolean isTorFeatureEnabled() {
        return ChromeSharedPreferences.getInstance()
                .readBoolean(BravePreferenceKeys.BRAVE_TOR_ENABLED, false);
    }

    /**
     * Returns the formatted SOCKS5 proxy URI for Tor routing (e.g. socks5://127.0.0.1:9050).
     */
    public static String getTorProxyUri() {
        int port = BraveTorManager.getInstance().getSocksPort();
        return "socks5://127.0.0.1:" + port;
    }
}
