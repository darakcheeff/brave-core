/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.tor;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.chromium.base.BravePreferenceKeys;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.chrome.browser.preferences.ChromeSharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the native embedded Tor daemon on Android, handles torrc generation,
 * control socket communication, bootstrap progress, and circuit reset (SIGNAL NEWNYM).
 */
public class BraveTorManager {
    private static final String TAG = "BraveTorManager";

    public static final int DEFAULT_SOCKS_PORT = 9050;
    public static final int DEFAULT_CONTROL_PORT = 9051;

    public interface TorBootstrapListener {
        void onBootstrapProgress(int progress, String summary);
        void onTorReady();
        void onTorStopped();
        void onError(String message);
    }

    public interface TorCommandCallback {
        void onResult(boolean success, String message);
    }

    private static volatile BraveTorManager sInstance;

    private final AtomicBoolean mIsRunning = new AtomicBoolean(false);
    private final AtomicBoolean mIsBootstrapped = new AtomicBoolean(false);
    private int mBootstrapProgress;
    private String mBootstrapSummary = "Not connected";

    private Process mTorProcess;
    private Socket mControlSocket;
    private OutputStream mControlOutputStream;
    private Thread mControlMonitorThread;
    private final ExecutorService mExecutor = Executors.newCachedThreadPool();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final List<TorBootstrapListener> mListeners = new CopyOnWriteArrayList<>();

    private BraveTorManager() {}

    public static BraveTorManager getInstance() {
        if (sInstance == null) {
            synchronized (BraveTorManager.class) {
                if (sInstance == null) {
                    sInstance = new BraveTorManager();
                }
            }
        }
        return sInstance;
    }

    public void addListener(TorBootstrapListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
            if (mIsBootstrapped.get()) {
                listener.onBootstrapProgress(100, "Connected");
                listener.onTorReady();
            } else if (mIsRunning.get()) {
                listener.onBootstrapProgress(mBootstrapProgress, mBootstrapSummary);
            }
        }
    }

    public void removeListener(TorBootstrapListener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    public boolean isTorRunning() {
        return mIsRunning.get();
    }

    public boolean isBootstrapped() {
        return mIsBootstrapped.get();
    }

    public int getBootstrapProgress() {
        return mBootstrapProgress;
    }

    public String getBootstrapSummary() {
        return mBootstrapSummary;
    }

    public int getSocksPort() {
        return ChromeSharedPreferences.getInstance()
                .readInt(BravePreferenceKeys.BRAVE_TOR_SOCKS_PORT, DEFAULT_SOCKS_PORT);
    }

    public int getControlPort() {
        return ChromeSharedPreferences.getInstance()
                .readInt(BravePreferenceKeys.BRAVE_TOR_CONTROL_PORT, DEFAULT_CONTROL_PORT);
    }

    /**
     * Starts the embedded Tor daemon and initiates control connection.
     */
    public synchronized void startTor(Context context, @Nullable TorBootstrapListener initialListener) {
        if (initialListener != null) {
            addListener(initialListener);
        }

        if (mIsRunning.get()) {
            Log.i(TAG, "Tor is already running");
            return;
        }

        mIsRunning.set(true);
        mIsBootstrapped.set(false);
        mBootstrapProgress = 0;
        mBootstrapSummary = "Starting daemon...";
        notifyProgress(0, mBootstrapSummary);

        mExecutor.execute(() -> {
            try {
                File torDir = new File(context.getFilesDir(), "tor");
                File torDataDir = new File(torDir, "data");
                if (!torDataDir.exists()) torDataDir.mkdirs();

                int socksPort = getSocksPort();
                int controlPort = getControlPort();
                File torrc = writeTorrcConfig(torDir, torDataDir, socksPort, controlPort);

                // Attempt to launch libtor.so or binary from nativeLibraryDir
                String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
                File torLib = new File(nativeLibDir, "libtor.so");
                if (torLib.exists()) {
                    Log.i(TAG, "Launching embedded Tor from " + torLib.getAbsolutePath());
                    ProcessBuilder pb = new ProcessBuilder(
                            torLib.getAbsolutePath(),
                            "-f", torrc.getAbsolutePath()
                    );
                    pb.directory(torDir);
                    mTorProcess = pb.start();
                } else {
                    Log.w(TAG, "libtor.so not found in " + nativeLibDir + ", assuming external Tor daemon on " + socksPort);
                }

                // Connect to Tor Control Port
                connectControlSocket(torDir, controlPort);

            } catch (Exception e) {
                Log.e(TAG, "Failed to start Tor daemon", e);
                mIsRunning.set(false);
                notifyError("Startup failed: " + e.getMessage());
            }
        });
    }

    /**
     * Stops the Tor daemon and control connection.
     */
    public synchronized void stopTor() {
        if (!mIsRunning.get()) return;

        mIsRunning.set(false);
        mIsBootstrapped.set(false);
        mBootstrapProgress = 0;
        mBootstrapSummary = "Stopped";

        try {
            if (mControlOutputStream != null) {
                mControlOutputStream.write("QUIT\r\n".getBytes(StandardCharsets.UTF_8));
                mControlOutputStream.flush();
            }
        } catch (Exception ignored) {}

        try {
            if (mControlSocket != null && !mControlSocket.isClosed()) {
                mControlSocket.close();
            }
        } catch (Exception ignored) {}

        if (mTorProcess != null) {
            mTorProcess.destroy();
            mTorProcess = null;
        }

        notifyStopped();
        Log.i(TAG, "Tor daemon stopped");
    }

    /**
     * Issues SIGNAL NEWNYM to Tor to discard old circuits and request a new IP identity.
     */
    public void newTorIdentity(TorCommandCallback callback) {
        mExecutor.execute(() -> {
            try {
                if (mControlOutputStream == null || mControlSocket == null || mControlSocket.isClosed()) {
                    if (callback != null) {
                        mMainHandler.post(() -> callback.onResult(false, "Control socket not connected"));
                    }
                    return;
                }

                synchronized (mControlOutputStream) {
                    mControlOutputStream.write("SIGNAL NEWNYM\r\n".getBytes(StandardCharsets.UTF_8));
                    mControlOutputStream.flush();
                }

                Log.i(TAG, "SIGNAL NEWNYM sent successfully");
                if (callback != null) {
                    mMainHandler.post(() -> callback.onResult(true, "New Tor circuit requested"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to send SIGNAL NEWNYM", e);
                if (callback != null) {
                    mMainHandler.post(() -> callback.onResult(false, e.getMessage()));
                }
            }
        });
    }

    private File writeTorrcConfig(File torDir, File torDataDir, int socksPort, int controlPort) throws Exception {
        File torrc = new File(torDir, "torrc");
        File authCookie = new File(torDir, "control.authcookie");
        File logFile = new File(torDir, "tor.log");

        StringBuilder sb = new StringBuilder();
        sb.append("DataDirectory ").append(torDataDir.getAbsolutePath()).append("\n");
        sb.append("SocksPort 127.0.0.1:").append(socksPort).append("\n");
        sb.append("ControlPort 127.0.0.1:").append(controlPort).append("\n");
        sb.append("CookieAuthentication 1\n");
        sb.append("CookieAuthFile ").append(authCookie.getAbsolutePath()).append("\n");
        sb.append("Log notice file ").append(logFile.getAbsolutePath()).append("\n");
        sb.append("AvoidDiskWrites 1\n");
        sb.append("CircuitBuildTimeout 30\n");
        sb.append("KeepalivePeriod 60\n");
        sb.append("SafeSocks 1\n");
        sb.append("TestSocks 1\n");

        int bridgeMode = ChromeSharedPreferences.getInstance()
                .readInt(BravePreferenceKeys.BRAVE_TOR_BRIDGES_MODE, 0);
        if (bridgeMode > 0) {
            sb.append("UseBridges 1\n");
            if (bridgeMode == 1) {
                // Built-in Snowflake bridge
                sb.append("Bridge snowflake 192.0.2.3:1 2B280B23E1107BB62ABFC40DDCC816BC1D3D6767\n");
            } else if (bridgeMode == 3) {
                // Custom user bridges
                String custom = ChromeSharedPreferences.getInstance()
                        .readString(BravePreferenceKeys.BRAVE_TOR_CUSTOM_BRIDGES, "");
                for (String line : custom.split("\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        sb.append(trimmed.startsWith("Bridge ") ? trimmed : "Bridge " + trimmed).append("\n");
                    }
                }
            }
        }

        try (FileOutputStream fos = new FileOutputStream(torrc)) {
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }

        return torrc;
    }

    private void connectControlSocket(File torDir, int controlPort) {
        int attempts = 0;
        while (mIsRunning.get() && attempts < 15) {
            try {
                Thread.sleep(500);
                mControlSocket = new Socket(InetAddress.getByName("127.0.0.1"), controlPort);
                mControlOutputStream = mControlSocket.getOutputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(mControlSocket.getInputStream(), StandardCharsets.UTF_8));

                // Authenticate using cookie if available
                File cookieFile = new File(torDir, "control.authcookie");
                if (cookieFile.exists() && cookieFile.length() > 0) {
                    byte[] cookieBytes = new byte[(int) cookieFile.length()];
                    try (FileInputStream fis = new FileInputStream(cookieFile)) {
                        int read = fis.read(cookieBytes);
                        if (read > 0) {
                            String hexCookie = bytesToHex(cookieBytes);
                            mControlOutputStream.write(("AUTHENTICATE " + hexCookie + "\r\n").getBytes(StandardCharsets.UTF_8));
                        } else {
                            mControlOutputStream.write("AUTHENTICATE\r\n".getBytes(StandardCharsets.UTF_8));
                        }
                    }
                } else {
                    mControlOutputStream.write("AUTHENTICATE\r\n".getBytes(StandardCharsets.UTF_8));
                }
                mControlOutputStream.flush();

                String authResp = reader.readLine();
                Log.i(TAG, "Tor Auth Response: " + authResp);

                // Subscribe to bootstrap status events
                mControlOutputStream.write("SETEVENTS STATUS_CLIENT NOTICE\r\n".getBytes(StandardCharsets.UTF_8));
                mControlOutputStream.flush();

                // Start monitor thread
                mControlMonitorThread = new Thread(() -> readControlEvents(reader), "BraveTorMonitorThread");
                mControlMonitorThread.start();
                return;

            } catch (Exception e) {
                attempts++;
                Log.d(TAG, "Waiting for Tor control port, attempt " + attempts + ": " + e.getMessage());
            }
        }

        if (mIsRunning.get()) {
            notifyError("Failed to connect to Tor control port 127.0.0.1:" + controlPort);
        }
    }

    private void readControlEvents(BufferedReader reader) {
        try {
            String line;
            while (mIsRunning.get() && (line = reader.readLine()) != null) {
                if (line.contains("BOOTSTRAP") && line.contains("PROGRESS=")) {
                    int progIdx = line.indexOf("PROGRESS=");
                    if (progIdx != -1) {
                        int endIdx = line.indexOf(' ', progIdx);
                        if (endIdx == -1) endIdx = line.length();
                        String progStr = line.substring(progIdx + 9, endIdx).trim();
                        try {
                            int percent = Integer.parseInt(progStr);
                            String summary = "Connecting...";
                            int sumIdx = line.indexOf("SUMMARY=\"");
                            if (sumIdx != -1) {
                                int sumEnd = line.indexOf('\"', sumIdx + 9);
                                if (sumEnd != -1) {
                                    summary = line.substring(sumIdx + 9, sumEnd);
                                }
                            }

                            mBootstrapProgress = percent;
                            mBootstrapSummary = summary;
                            notifyProgress(percent, summary);

                            if (percent >= 100) {
                                mIsBootstrapped.set(true);
                                notifyReady();
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            if (mIsRunning.get()) {
                Log.e(TAG, "Error in Tor control monitor thread", e);
            }
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void notifyProgress(int percent, String summary) {
        mMainHandler.post(() -> {
            for (TorBootstrapListener l : mListeners) {
                l.onBootstrapProgress(percent, summary);
            }
        });
    }

    private void notifyReady() {
        mMainHandler.post(() -> {
            for (TorBootstrapListener l : mListeners) {
                l.onTorReady();
            }
        });
    }

    private void notifyStopped() {
        mMainHandler.post(() -> {
            for (TorBootstrapListener l : mListeners) {
                l.onTorStopped();
            }
        });
    }

    private void notifyError(String error) {
        mMainHandler.post(() -> {
            for (TorBootstrapListener l : mListeners) {
                l.onError(error);
            }
        });
    }
}
