/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.settings;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import org.chromium.base.BravePreferenceKeys;
import org.chromium.base.supplier.MonotonicObservableSupplier;
import org.chromium.base.supplier.ObservableSuppliers;
import org.chromium.base.supplier.SettableMonotonicObservableSupplier;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.ChromeSharedPreferences;
import org.chromium.chrome.browser.tor.BraveTorManager;
import org.chromium.components.browser_ui.settings.ChromeBasePreference;
import org.chromium.components.browser_ui.settings.ChromeSwitchPreference;
import org.chromium.components.browser_ui.settings.SettingsUtils;

/**
 * Settings fragment for Brave Tor (.onion) daemon configuration, bridge circumvention,
 * and circuit identity management on Android.
 */
public class BraveTorPreferences extends BravePreferenceFragment
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener,
                   BraveTorManager.TorBootstrapListener {

    public static final String PREF_TOR_ENABLED = "pref_tor_enabled";
    public static final String PREF_TOR_STATUS = "pref_tor_status";
    public static final String PREF_TOR_NEW_IDENTITY = "pref_tor_new_identity";
    public static final String PREF_TOR_AUTO_ONION = "pref_tor_auto_onion";
    public static final String PREF_TOR_BRIDGES = "pref_tor_bridges";
    public static final String PREF_TOR_SOCKS_PORT = "pref_tor_socks_port";
    public static final String PREF_TOR_CONTROL_PORT = "pref_tor_control_port";

    private final SettableMonotonicObservableSupplier<String> mPageTitle =
            ObservableSuppliers.createMonotonic();

    private ChromeSwitchPreference mTorEnabledPref;
    private ChromeBasePreference mTorStatusPref;
    private ChromeBasePreference mTorNewIdentityPref;
    private ChromeSwitchPreference mTorAutoOnionPref;
    private ChromeBasePreference mTorBridgesPref;
    private ChromeBasePreference mTorSocksPortPref;
    private ChromeBasePreference mTorControlPortPref;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
        SettingsUtils.addPreferencesFromResource(this, R.xml.brave_tor_preferences);
        mPageTitle.set("Tor (.onion) Integration");

        mTorEnabledPref = (ChromeSwitchPreference) findPreference(PREF_TOR_ENABLED);
        mTorStatusPref = (ChromeBasePreference) findPreference(PREF_TOR_STATUS);
        mTorNewIdentityPref = (ChromeBasePreference) findPreference(PREF_TOR_NEW_IDENTITY);
        mTorAutoOnionPref = (ChromeSwitchPreference) findPreference(PREF_TOR_AUTO_ONION);
        mTorBridgesPref = (ChromeBasePreference) findPreference(PREF_TOR_BRIDGES);
        mTorSocksPortPref = (ChromeBasePreference) findPreference(PREF_TOR_SOCKS_PORT);
        mTorControlPortPref = (ChromeBasePreference) findPreference(PREF_TOR_CONTROL_PORT);

        boolean torEnabled = ChromeSharedPreferences.getInstance()
                .readBoolean(BravePreferenceKeys.BRAVE_TOR_ENABLED, false);
        if (mTorEnabledPref != null) {
            mTorEnabledPref.setChecked(torEnabled);
            mTorEnabledPref.setOnPreferenceChangeListener(this);
        }

        if (mTorNewIdentityPref != null) {
            mTorNewIdentityPref.setOnPreferenceClickListener(this);
        }

        boolean autoOnion = ChromeSharedPreferences.getInstance()
                .readBoolean(BravePreferenceKeys.BRAVE_TOR_AUTO_ONION, true);
        if (mTorAutoOnionPref != null) {
            mTorAutoOnionPref.setChecked(autoOnion);
            mTorAutoOnionPref.setOnPreferenceChangeListener(this);
        }

        if (mTorBridgesPref != null) {
            updateBridgesSummary();
            mTorBridgesPref.setOnPreferenceClickListener(this);
        }

        if (mTorSocksPortPref != null) {
            updatePortsSummary();
            mTorSocksPortPref.setOnPreferenceClickListener(this);
        }

        if (mTorControlPortPref != null) {
            mTorControlPortPref.setOnPreferenceClickListener(this);
        }

        updateStatusDisplay();
    }

    @Override
    public void onResume() {
        super.onResume();
        BraveTorManager.getInstance().addListener(this);
        updateStatusDisplay();
    }

    @Override
    public void onPause() {
        super.onPause();
        BraveTorManager.getInstance().removeListener(this);
    }

    @Override
    public MonotonicObservableSupplier<String> getPageTitle() {
        return mPageTitle;
    }

    private void updateStatusDisplay() {
        if (mTorStatusPref == null) return;
        BraveTorManager tm = BraveTorManager.getInstance();
        if (tm.isBootstrapped()) {
            mTorStatusPref.setSummary("Connected (100%) - Circuits Established");
        } else if (tm.isTorRunning()) {
            mTorStatusPref.setSummary("Connecting (" + tm.getBootstrapProgress() + "%): " + tm.getBootstrapSummary());
        } else {
            mTorStatusPref.setSummary("Disconnected");
        }
    }

    private void updateBridgesSummary() {
        if (mTorBridgesPref == null) return;
        int mode = ChromeSharedPreferences.getInstance()
                .readInt(BravePreferenceKeys.BRAVE_TOR_BRIDGES_MODE, 0);
        switch (mode) {
            case 1:
                mTorBridgesPref.setSummary("Snowflake (Censorship bypass active)");
                break;
            case 3:
                mTorBridgesPref.setSummary("Custom Bridge");
                break;
            default:
                mTorBridgesPref.setSummary("None (Direct connection)");
                break;
        }
    }

    private void updatePortsSummary() {
        if (mTorSocksPortPref != null) {
            int socks = BraveTorManager.getInstance().getSocksPort();
            mTorSocksPortPref.setSummary("127.0.0.1:" + socks);
        }
        if (mTorControlPortPref != null) {
            int ctrl = BraveTorManager.getInstance().getControlPort();
            mTorControlPortPref.setSummary("127.0.0.1:" + ctrl);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (PREF_TOR_ENABLED.equals(key)) {
            boolean enabled = (Boolean) newValue;
            ChromeSharedPreferences.getInstance()
                    .writeBoolean(BravePreferenceKeys.BRAVE_TOR_ENABLED, enabled);
            if (enabled) {
                BraveTorManager.getInstance().startTor(getContext(), this);
                Toast.makeText(getContext(), "Starting Tor daemon...", Toast.LENGTH_SHORT).show();
            } else {
                BraveTorManager.getInstance().stopTor();
                Toast.makeText(getContext(), "Tor daemon stopped", Toast.LENGTH_SHORT).show();
            }
            updateStatusDisplay();
            return true;
        } else if (PREF_TOR_AUTO_ONION.equals(key)) {
            boolean auto = (Boolean) newValue;
            ChromeSharedPreferences.getInstance()
                    .writeBoolean(BravePreferenceKeys.BRAVE_TOR_AUTO_ONION, auto);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        if (PREF_TOR_NEW_IDENTITY.equals(key)) {
            BraveTorManager.getInstance().newTorIdentity((success, msg) -> {
                Context ctx = getContext();
                if (ctx != null) {
                    Toast.makeText(ctx, success ? "New Tor Identity established (circuits refreshed)" : "Failed: " + msg, Toast.LENGTH_SHORT).show();
                }
            });
            return true;
        } else if (PREF_TOR_BRIDGES.equals(key)) {
            showBridgesDialog();
            return true;
        } else if (PREF_TOR_SOCKS_PORT.equals(key)) {
            showEditPortDialog(true);
            return true;
        } else if (PREF_TOR_CONTROL_PORT.equals(key)) {
            showEditPortDialog(false);
            return true;
        }
        return false;
    }

    private void showBridgesDialog() {
        Context context = getContext();
        if (context == null) return;

        String[] options = new String[]{"None (Direct)", "Snowflake (Recommended for censorship)", "Custom Bridges"};
        int current = ChromeSharedPreferences.getInstance()
                .readInt(BravePreferenceKeys.BRAVE_TOR_BRIDGES_MODE, 0);
        int selection = (current == 1) ? 1 : ((current == 3) ? 2 : 0);

        new AlertDialog.Builder(context)
                .setTitle("Select Tor Bridge Mode")
                .setSingleChoiceItems(options, selection, (dialog, which) -> {
                    int mode = (which == 1) ? 1 : ((which == 2) ? 3 : 0);
                    ChromeSharedPreferences.getInstance()
                            .writeInt(BravePreferenceKeys.BRAVE_TOR_BRIDGES_MODE, mode);
                    updateBridgesSummary();
                    dialog.dismiss();

                    if (mode == 3) {
                        showCustomBridgeDialog();
                    } else if (BraveTorManager.getInstance().isTorRunning()) {
                        BraveTorManager.getInstance().stopTor();
                        BraveTorManager.getInstance().startTor(context, BraveTorPreferences.this);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCustomBridgeDialog() {
        Context context = getContext();
        if (context == null) return;

        String existing = ChromeSharedPreferences.getInstance()
                .readString(BravePreferenceKeys.BRAVE_TOR_CUSTOM_BRIDGES, "");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, padding / 2);

        final EditText input = new EditText(context);
        input.setHint("e.g. obfs4 192.0.2.1:443 ...");
        input.setText(existing);
        layout.addView(input);

        new AlertDialog.Builder(context)
                .setTitle("Custom Tor Bridges")
                .setMessage("Paste bridge lines provided by bridges.torproject.org:")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    ChromeSharedPreferences.getInstance()
                            .writeString(BravePreferenceKeys.BRAVE_TOR_CUSTOM_BRIDGES, input.getText().toString().trim());
                    if (BraveTorManager.getInstance().isTorRunning()) {
                        BraveTorManager.getInstance().stopTor();
                        BraveTorManager.getInstance().startTor(context, BraveTorPreferences.this);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditPortDialog(boolean isSocks) {
        Context context = getContext();
        if (context == null) return;

        int current = isSocks ? BraveTorManager.getInstance().getSocksPort() : BraveTorManager.getInstance().getControlPort();

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, padding / 2);

        final EditText portInput = new EditText(context);
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setText(String.valueOf(current));
        layout.addView(portInput);

        new AlertDialog.Builder(context)
                .setTitle(isSocks ? "SOCKS5 Proxy Port" : "Tor Control Port")
                .setMessage("Default: " + (isSocks ? "9050" : "9051"))
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    try {
                        int p = Integer.parseInt(portInput.getText().toString().trim());
                        if (isSocks) {
                            ChromeSharedPreferences.getInstance().writeInt(BravePreferenceKeys.BRAVE_TOR_SOCKS_PORT, p);
                        } else {
                            ChromeSharedPreferences.getInstance().writeInt(BravePreferenceKeys.BRAVE_TOR_CONTROL_PORT, p);
                        }
                        updatePortsSummary();
                    } catch (NumberFormatException ignored) {}
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // TorBootstrapListener implementation:
    @Override
    public void onBootstrapProgress(int progress, String summary) {
        if (mTorStatusPref != null) {
            mTorStatusPref.setSummary("Connecting (" + progress + "%): " + summary);
        }
    }

    @Override
    public void onTorReady() {
        if (mTorStatusPref != null) {
            mTorStatusPref.setSummary("Connected (100%) - Circuits Established");
        }
    }

    @Override
    public void onTorStopped() {
        if (mTorStatusPref != null) {
            mTorStatusPref.setSummary("Disconnected");
        }
    }

    @Override
    public void onError(String message) {
        if (mTorStatusPref != null) {
            mTorStatusPref.setSummary("Error: " + message);
        }
    }
}
