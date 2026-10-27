/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
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
import org.chromium.chrome.browser.mcp.BraveMcpServer;
import org.chromium.chrome.browser.mcp.BraveMcpServerManager;
import org.chromium.chrome.browser.mcp.BraveMcpUtils;
import org.chromium.chrome.browser.preferences.ChromeSharedPreferences;
import org.chromium.components.browser_ui.settings.ChromeBasePreference;
import org.chromium.components.browser_ui.settings.ChromeSwitchPreference;
import org.chromium.components.browser_ui.settings.SettingsUtils;

/**
 * Settings fragment for Brave Native MCP Server and Stealth CDP Automation.
 */
public class BraveAutomationPreferences extends BravePreferenceFragment
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    public static final String PREF_MCP_SERVER_ENABLED = "pref_mcp_server_enabled";
    public static final String PREF_MCP_SERVER_PORT = "pref_mcp_server_port";
    public static final String PREF_MCP_SERVER_AUTH_TOKEN = "pref_mcp_server_auth_token";
    public static final String PREF_MCP_COPY_CONFIG = "pref_mcp_copy_config";
    public static final String PREF_STEALTH_CDP_ENABLED = "pref_stealth_cdp_enabled";

    private final SettableMonotonicObservableSupplier<String> mPageTitle =
            ObservableSuppliers.createMonotonic();

    private ChromeSwitchPreference mMcpEnabledPref;
    private ChromeBasePreference mMcpPortPref;
    private ChromeBasePreference mMcpTokenPref;
    private ChromeBasePreference mMcpCopyConfigPref;
    private ChromeSwitchPreference mStealthCdpPref;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
        SettingsUtils.addPreferencesFromResource(this, R.xml.brave_automation_preferences);
        mPageTitle.set("AI Automation & Stealth MCP");

        mMcpEnabledPref = (ChromeSwitchPreference) findPreference(PREF_MCP_SERVER_ENABLED);
        mMcpPortPref = (ChromeBasePreference) findPreference(PREF_MCP_SERVER_PORT);
        mMcpTokenPref = (ChromeBasePreference) findPreference(PREF_MCP_SERVER_AUTH_TOKEN);
        mMcpCopyConfigPref = (ChromeBasePreference) findPreference(PREF_MCP_COPY_CONFIG);
        mStealthCdpPref = (ChromeSwitchPreference) findPreference(PREF_STEALTH_CDP_ENABLED);

        boolean mcpEnabled = ChromeSharedPreferences.getInstance()
                .readBoolean(BravePreferenceKeys.BRAVE_MCP_SERVER_ENABLED, false);
        if (mMcpEnabledPref != null) {
            mMcpEnabledPref.setChecked(mcpEnabled);
            mMcpEnabledPref.setOnPreferenceChangeListener(this);
        }

        if (mMcpPortPref != null) {
            updatePortSummary();
            mMcpPortPref.setOnPreferenceClickListener(this);
        }

        if (mMcpTokenPref != null) {
            updateTokenSummary();
            mMcpTokenPref.setOnPreferenceClickListener(this);
        }

        if (mMcpCopyConfigPref != null) {
            mMcpCopyConfigPref.setOnPreferenceClickListener(this);
        }

        boolean stealthEnabled = ChromeSharedPreferences.getInstance()
                .readBoolean(BravePreferenceKeys.BRAVE_STEALTH_CDP_ENABLED, true);
        if (mStealthCdpPref != null) {
            mStealthCdpPref.setChecked(stealthEnabled);
            mStealthCdpPref.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public MonotonicObservableSupplier<String> getPageTitle() {
        return mPageTitle;
    }

    private void updatePortSummary() {
        if (mMcpPortPref == null) return;
        int port = ChromeSharedPreferences.getInstance()
                .readInt(BravePreferenceKeys.BRAVE_MCP_SERVER_PORT, BraveMcpServer.DEFAULT_PORT);
        boolean running = BraveMcpServerManager.getInstance().isServerRunning();
        mMcpPortPref.setSummary("http://127.0.0.1:" + port + (running ? " (Active)" : " (Stopped)"));
    }

    private void updateTokenSummary() {
        if (mMcpTokenPref == null) return;
        String token = ChromeSharedPreferences.getInstance()
                .readString(BravePreferenceKeys.BRAVE_MCP_SERVER_AUTH_TOKEN, "");
        if (token == null || token.isEmpty()) {
            token = BraveMcpUtils.generateSecureToken();
            ChromeSharedPreferences.getInstance()
                    .writeString(BravePreferenceKeys.BRAVE_MCP_SERVER_AUTH_TOKEN, token);
        }
        String masked = token.length() > 10 ? token.substring(0, 6) + "..." + token.substring(token.length() - 4) : token;
        mMcpTokenPref.setSummary("Bearer " + masked + " (Tap to view/manage)");
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (PREF_MCP_SERVER_ENABLED.equals(key)) {
            boolean enabled = (Boolean) newValue;
            ChromeSharedPreferences.getInstance()
                    .writeBoolean(BravePreferenceKeys.BRAVE_MCP_SERVER_ENABLED, enabled);
            if (enabled) {
                BraveMcpServerManager.getInstance().initIfEnabled(getContext());
                Toast.makeText(getContext(), "Brave MCP Server started on 127.0.0.1", Toast.LENGTH_SHORT).show();
            } else {
                BraveMcpServerManager.getInstance().stopServer();
                Toast.makeText(getContext(), "Brave MCP Server stopped", Toast.LENGTH_SHORT).show();
            }
            updatePortSummary();
            return true;
        } else if (PREF_STEALTH_CDP_ENABLED.equals(key)) {
            boolean stealth = (Boolean) newValue;
            ChromeSharedPreferences.getInstance()
                    .writeBoolean(BravePreferenceKeys.BRAVE_STEALTH_CDP_ENABLED, stealth);
            Toast.makeText(getContext(), stealth ? "Stealth automation active" : "Stealth cloaking disabled", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        if (PREF_MCP_SERVER_PORT.equals(key)) {
            showEditPortDialog();
            return true;
        } else if (PREF_MCP_SERVER_AUTH_TOKEN.equals(key)) {
            showTokenManageDialog();
            return true;
        } else if (PREF_MCP_COPY_CONFIG.equals(key)) {
            copyMcpConfigToClipboard();
            return true;
        }
        return false;
    }

    private void showEditPortDialog() {
        Context context = getContext();
        if (context == null) return;

        int currentPort = ChromeSharedPreferences.getInstance()
                .readInt(BravePreferenceKeys.BRAVE_MCP_SERVER_PORT, BraveMcpServer.DEFAULT_PORT);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, padding / 2);

        final EditText portInput = new EditText(context);
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setText(String.valueOf(currentPort));
        layout.addView(portInput);

        new AlertDialog.Builder(context)
                .setTitle("MCP Server Port")
                .setMessage("Enter loopback port (1024-65535)")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    try {
                        int port = Integer.parseInt(portInput.getText().toString().trim());
                        if (port < 1024 || port > 65535) {
                            Toast.makeText(context, "Invalid port range (1024-65535)", Toast.LENGTH_LONG).show();
                            return;
                        }
                        ChromeSharedPreferences.getInstance()
                                .writeInt(BravePreferenceKeys.BRAVE_MCP_SERVER_PORT, port);
                        updatePortSummary();
                        if (BraveMcpServerManager.getInstance().isServerRunning()) {
                            BraveMcpServerManager.getInstance().restartServer();
                        }
                        Toast.makeText(context, "Port updated to " + port, Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException e) {
                        Toast.makeText(context, "Invalid port number", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTokenManageDialog() {
        Context context = getContext();
        if (context == null) return;

        String currentToken = ChromeSharedPreferences.getInstance()
                .readString(BravePreferenceKeys.BRAVE_MCP_SERVER_AUTH_TOKEN, "");
        if (currentToken == null || currentToken.isEmpty()) {
            currentToken = BraveMcpUtils.generateSecureToken();
            ChromeSharedPreferences.getInstance()
                    .writeString(BravePreferenceKeys.BRAVE_MCP_SERVER_AUTH_TOKEN, currentToken);
        }

        final String tokenToShow = currentToken;

        new AlertDialog.Builder(context)
                .setTitle("Bearer Authentication Token")
                .setMessage("Token:\n\n" + tokenToShow)
                .setPositiveButton("Copy Token", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        ClipData clip = ClipData.newPlainText("Brave MCP Token", tokenToShow);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(context, "Token copied to clipboard", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Regenerate", (dialog, which) -> {
                    String newToken = BraveMcpUtils.generateSecureToken();
                    ChromeSharedPreferences.getInstance()
                            .writeString(BravePreferenceKeys.BRAVE_MCP_SERVER_AUTH_TOKEN, newToken);
                    updateTokenSummary();
                    if (BraveMcpServerManager.getInstance().isServerRunning()) {
                        BraveMcpServerManager.getInstance().restartServer();
                    }
                    Toast.makeText(context, "New authentication token generated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void copyMcpConfigToClipboard() {
        Context context = getContext();
        if (context == null) return;

        int port = ChromeSharedPreferences.getInstance()
                .readInt(BravePreferenceKeys.BRAVE_MCP_SERVER_PORT, BraveMcpServer.DEFAULT_PORT);
        String token = ChromeSharedPreferences.getInstance()
                .readString(BravePreferenceKeys.BRAVE_MCP_SERVER_AUTH_TOKEN, "");

        String config = BraveMcpUtils.buildClaudeConfigJson(port, token);
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("Brave MCP Config", config);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "Claude / Cursor MCP configuration copied", Toast.LENGTH_LONG).show();
        }
    }
}
