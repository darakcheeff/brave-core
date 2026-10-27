/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.mcp;

import android.app.Activity;
import android.content.Context;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.BravePreferenceKeys;
import org.chromium.base.Log;
import org.chromium.chrome.browser.app.BraveActivity;
import org.chromium.chrome.browser.preferences.ChromeSharedPreferences;

import java.lang.ref.WeakReference;

/**
 * Singleton manager for Brave's native in-browser MCP Server.
 * Coordinates server lifecycle and links active window/tabs for stealth automation.
 */
public class BraveMcpServerManager {
    private static final String TAG = "BraveMcpServerMgr";

    private static volatile BraveMcpServerManager sInstance;

    private BraveMcpServer mServer;
    private WeakReference<BraveActivity> mCurrentActivityRef = new WeakReference<>(null);

    private BraveMcpServerManager() {}

    public static BraveMcpServerManager getInstance() {
        if (sInstance == null) {
            synchronized (BraveMcpServerManager.class) {
                if (sInstance == null) {
                    sInstance = new BraveMcpServerManager();
                }
            }
        }
        return sInstance;
    }

    public synchronized void setCurrentActivity(BraveActivity activity) {
        mCurrentActivityRef = new WeakReference<>(activity);
    }

    public synchronized void onActivityDestroyed(BraveActivity activity) {
        if (mCurrentActivityRef.get() == activity) {
            mCurrentActivityRef.clear();
        }
    }

    public synchronized BraveActivity getCurrentActivity() {
        BraveActivity act = mCurrentActivityRef.get();
        if (act != null && !act.isFinishing() && !act.isDestroyed()) {
            return act;
        }

        Activity lastFocused = ApplicationStatus.getLastTrackedFocusedActivity();
        if (lastFocused instanceof BraveActivity && !lastFocused.isFinishing() && !lastFocused.isDestroyed()) {
            mCurrentActivityRef = new WeakReference<>((BraveActivity) lastFocused);
            return (BraveActivity) lastFocused;
        }

        for (Activity running : ApplicationStatus.getRunningActivities()) {
            if (running instanceof BraveActivity && !running.isFinishing() && !running.isDestroyed()) {
                mCurrentActivityRef = new WeakReference<>((BraveActivity) running);
                return (BraveActivity) running;
            }
        }

        return null;
    }

    /**
     * Initializes and starts the MCP server if enabled in preferences.
     */
    public synchronized void initIfEnabled(Context context) {
        boolean enabled = ChromeSharedPreferences.getInstance()
                .readBoolean(BravePreferenceKeys.BRAVE_MCP_SERVER_ENABLED, false);

        if (!enabled) {
            stopServer();
            return;
        }

        int port = ChromeSharedPreferences.getInstance()
                .readInt(BravePreferenceKeys.BRAVE_MCP_SERVER_PORT, BraveMcpServer.DEFAULT_PORT);
        String token = ChromeSharedPreferences.getInstance()
                .readString(BravePreferenceKeys.BRAVE_MCP_SERVER_AUTH_TOKEN, "");

        if (token == null || token.isEmpty()) {
            token = BraveMcpUtils.generateSecureToken();
            ChromeSharedPreferences.getInstance()
                    .writeString(BravePreferenceKeys.BRAVE_MCP_SERVER_AUTH_TOKEN, token);
        }

        startServer(port, token);
    }

    public synchronized void startServer(int port, String token) {
        if (mServer != null && mServer.isRunning()) {
            if (mServer.getPort() == port) {
                Log.i(TAG, "MCP server already running on port " + port);
                return;
            }
            mServer.stop();
        }

        Log.i(TAG, "Starting Brave MCP Server on port " + port);
        mServer = new BraveMcpServer(port, token);
        mServer.start();
    }

    public synchronized void stopServer() {
        if (mServer != null) {
            Log.i(TAG, "Stopping Brave MCP Server");
            mServer.stop();
            mServer = null;
        }
    }

    public synchronized void restartServer() {
        stopServer();
        int port = ChromeSharedPreferences.getInstance()
                .readInt(BravePreferenceKeys.BRAVE_MCP_SERVER_PORT, BraveMcpServer.DEFAULT_PORT);
        String token = ChromeSharedPreferences.getInstance()
                .readString(BravePreferenceKeys.BRAVE_MCP_SERVER_AUTH_TOKEN, "");
        if (token == null || token.isEmpty()) {
            token = BraveMcpUtils.generateSecureToken();
            ChromeSharedPreferences.getInstance()
                    .writeString(BravePreferenceKeys.BRAVE_MCP_SERVER_AUTH_TOKEN, token);
        }
        startServer(port, token);
    }

    public synchronized boolean isServerRunning() {
        return mServer != null && mServer.isRunning();
    }

    public synchronized int getPort() {
        return mServer != null ? mServer.getPort() : ChromeSharedPreferences.getInstance()
                .readInt(BravePreferenceKeys.BRAVE_MCP_SERVER_PORT, BraveMcpServer.DEFAULT_PORT);
    }
}
