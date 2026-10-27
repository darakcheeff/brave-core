/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.tor;

import android.app.Activity;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.chromium.chrome.browser.app.BraveActivity;
import org.chromium.chrome.browser.tabmodel.TabLaunchType;
import org.chromium.content_public.browser.LoadUrlParams;

/**
 * High-level coordinator between UI activities, tabs, and Tor daemon.
 */
public final class BraveTorController {

    private BraveTorController() {}

    /**
     * Opens a private tab dedicated to Tor browsing.
     * Automatically launches the embedded Tor daemon if not currently running.
     */
    public static void openTorTab(Activity activity, String targetUrl) {
        if (!(activity instanceof BraveActivity)) return;
        BraveActivity braveActivity = (BraveActivity) activity;

        final String url = (targetUrl != null && !targetUrl.isEmpty()) ? targetUrl : "chrome://tor-internals";

        BraveTorManager torManager = BraveTorManager.getInstance();
        if (!torManager.isTorRunning()) {
            Toast.makeText(activity, "Connecting to Tor network...", Toast.LENGTH_SHORT).show();
            torManager.startTor(activity, new BraveTorManager.TorBootstrapListener() {
                @Override
                public void onBootstrapProgress(int progress, String summary) {}

                @Override
                public void onTorReady() {
                    Toast.makeText(activity, "Tor connected (100%)", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onTorStopped() {}

                @Override
                public void onError(String message) {
                    Toast.makeText(activity, "Tor error: " + message, Toast.LENGTH_LONG).show();
                }
            });
        }

        // Launch in incognito / private tab mode
        if (braveActivity.getTabCreator(true) != null) {
            braveActivity.getTabCreator(true).launchUrl(url, TabLaunchType.FROM_CHROME_UI);
        } else if (braveActivity.getActivityTab() != null) {
            braveActivity.getActivityTab().loadUrl(new LoadUrlParams(url));
        }
    }

    /**
     * Checks if a URL is an .onion address and ensures Tor is active.
     */
    public static boolean maybeRouteOnionUrl(Activity activity, String url) {
        if (BraveTorProxyController.isOnionUrl(url) && BraveTorProxyController.isAutoOnionEnabled()) {
            openTorTab(activity, url);
            return true;
        }
        return false;
    }

    /**
     * Shows a prompt when an Onion-Location response header is encountered.
     */
    public static void promptOnionLocation(Activity activity, String onionUrl) {
        if (activity == null || activity.isFinishing()) return;

        new AlertDialog.Builder(activity)
                .setTitle("Onion Site Available")
                .setMessage("This site offers an encrypted .onion hidden service version via the Tor network:\n\n" + onionUrl)
                .setPositiveButton("Open via Tor", (dialog, which) -> openTorTab(activity, onionUrl))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
