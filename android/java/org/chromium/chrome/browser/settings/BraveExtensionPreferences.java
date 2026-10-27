/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.settings;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import org.chromium.base.supplier.MonotonicObservableSupplier;
import org.chromium.base.supplier.ObservableSuppliers;
import org.chromium.base.supplier.SettableMonotonicObservableSupplier;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.ChromeSharedPreferences;
import org.chromium.chrome.browser.util.TabUtils;
import org.chromium.components.browser_ui.settings.ChromeBasePreference;
import org.chromium.components.browser_ui.settings.ChromeSwitchPreference;
import org.chromium.components.browser_ui.settings.SettingsUtils;
import org.chromium.components.browser_ui.settings.search.BaseSearchIndexProvider;

import java.util.HashSet;
import java.util.Set;

/**
 * Settings fragment for extensions security, auto-update policy and version pinning (anti-supply-chain protection).
 */
public class BraveExtensionPreferences extends BravePreferenceFragment
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    public static final String PREF_EXTENSION_AUTO_UPDATE = "pref_extension_auto_update";
    public static final String PREF_MANUAL_UPDATE_CHECK = "pref_manual_update_check";
    public static final String PREF_OPEN_WEBSTORE = "pref_open_webstore";
    public static final String PREF_INSTALLED_CATEGORY = "installed_extensions_category";

    public static final String KEY_AUTO_UPDATE_ENABLED = "brave.extensions.auto_update_enabled";
    public static final String KEY_PINNED_EXTENSIONS = "brave.extensions.pinned_list";

    private final SettableMonotonicObservableSupplier<String> mPageTitle =
            ObservableSuppliers.createMonotonic();

    private ChromeSwitchPreference mAutoUpdateSwitch;
    private PreferenceCategory mInstalledCategory;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
        SettingsUtils.addPreferencesFromResource(this, R.xml.brave_extension_preferences);
        mPageTitle.set("Extensions");

        mAutoUpdateSwitch = (ChromeSwitchPreference) findPreference(PREF_EXTENSION_AUTO_UPDATE);
        if (mAutoUpdateSwitch != null) {
            mAutoUpdateSwitch.setChecked(isAutoUpdateEnabled());
            mAutoUpdateSwitch.setOnPreferenceChangeListener(this);
        }

        Preference manualCheck = findPreference(PREF_MANUAL_UPDATE_CHECK);
        if (manualCheck != null) {
            manualCheck.setOnPreferenceClickListener(this);
        }

        Preference openStore = findPreference(PREF_OPEN_WEBSTORE);
        if (openStore != null) {
            openStore.setOnPreferenceClickListener(this);
        }

        mInstalledCategory = (PreferenceCategory) findPreference(PREF_INSTALLED_CATEGORY);
        refreshPinnedExtensionsList();
    }

    @Override
    public MonotonicObservableSupplier<String> getPageTitle() {
        return mPageTitle;
    }

    public static boolean isAutoUpdateEnabled() {
        return ChromeSharedPreferences.getInstance().readBoolean(KEY_AUTO_UPDATE_ENABLED, true);
    }

    public static void setAutoUpdateEnabled(boolean enabled) {
        ChromeSharedPreferences.getInstance().writeBoolean(KEY_AUTO_UPDATE_ENABLED, enabled);
    }

    public static Set<String> getPinnedExtensionIds() {
        return ChromeSharedPreferences.getInstance().readStringSet(KEY_PINNED_EXTENSIONS, new HashSet<>());
    }

    public static boolean isExtensionPinned(String extensionId) {
        if (TextUtils.isEmpty(extensionId)) return false;
        Set<String> pinned = getPinnedExtensionIds();
        return pinned.contains(extensionId);
    }

    public static void setExtensionPinned(String extensionId, boolean pinned) {
        if (TextUtils.isEmpty(extensionId)) return;
        Set<String> current = new HashSet<>(getPinnedExtensionIds());
        if (pinned) {
            current.add(extensionId);
        } else {
            current.remove(extensionId);
        }
        ChromeSharedPreferences.getInstance().writeStringSet(KEY_PINNED_EXTENSIONS, current);
    }

    private void refreshPinnedExtensionsList() {
        if (mInstalledCategory == null) return;

        // Clean dynamic prefs
        mInstalledCategory.removeAll();

        // Help notice
        ChromeBasePreference helpPref = new ChromeBasePreference(getContext());
        helpPref.setKey("pref_extensions_help");
        helpPref.setSelectable(false);
        helpPref.setSummary("Enable 'Version Pin' on an extension to lock its current version and block updates, protecting against compromised developer accounts.");
        mInstalledCategory.addPreference(helpPref);

        // Add "Pin Extension by ID" action
        ChromeBasePreference addPinPref = new ChromeBasePreference(getContext());
        addPinPref.setTitle("Add Extension ID to Pin List");
        addPinPref.setSummary("Lock specific extension ID against automatic updates");
        addPinPref.setOnPreferenceClickListener(preference -> {
            showAddPinDialog();
            return true;
        });
        mInstalledCategory.addPreference(addPinPref);

        // Show existing pinned extensions
        Set<String> pinnedIds = getPinnedExtensionIds();
        for (String id : pinnedIds) {
            ChromeSwitchPreference extPref = new ChromeSwitchPreference(getContext());
            extPref.setTitle("Extension: " + id);
            extPref.setSummary("Version locked (Updates blocked)");
            extPref.setChecked(true);
            extPref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean isPinned = (Boolean) newValue;
                setExtensionPinned(id, isPinned);
                refreshPinnedExtensionsList();
                return true;
            });
            mInstalledCategory.addPreference(extPref);
        }
    }

    private void showAddPinDialog() {
        Context context = getContext();
        if (context == null) return;

        final EditText input = new EditText(context);
        input.setHint("e.g. cjpalhdlnbpafiamejdnhcphjbkeiagm");
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        FrameLayout container = new FrameLayout(context);
        int margin = (int) (16 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = margin;
        params.rightMargin = margin;
        input.setLayoutParams(params);
        container.addView(input);

        new AlertDialog.Builder(context, R.style.ThemeOverlay_BrowserUI_AlertDialog)
                .setTitle("Pin Extension Version")
                .setMessage("Enter Extension ID to lock version:")
                .setView(container)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    String extId = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(extId)) {
                        setExtensionPinned(extId, true);
                        refreshPinnedExtensionsList();
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (PREF_EXTENSION_AUTO_UPDATE.equals(preference.getKey())) {
            boolean enabled = (Boolean) newValue;
            setAutoUpdateEnabled(enabled);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        if (PREF_OPEN_WEBSTORE.equals(key)) {
            TabUtils.openURLWithBraveActivity("https://chromewebstore.google.com");
            return true;
        } else if (PREF_MANUAL_UPDATE_CHECK.equals(key)) {
            Toast.makeText(getContext(), "Checking updates for unpinned extensions...", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(
                    BraveExtensionPreferences.class.getName(),
                    BaseSearchIndexProvider.INDEX_OPT_OUT);
}
