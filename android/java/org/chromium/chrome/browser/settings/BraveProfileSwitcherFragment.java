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
import org.chromium.chrome.browser.BraveRelaunchUtils;
import org.chromium.chrome.browser.profiles.BraveMultiProfileManager;
import org.chromium.chrome.browser.profiles.BraveProfileInfo;
import org.chromium.components.browser_ui.settings.ChromeBasePreference;
import org.chromium.components.browser_ui.settings.SettingsUtils;

import java.util.List;

/**
 * Settings fragment for Multi-Profile management and Android 10 EMUI split-screen window launches.
 */
public class BraveProfileSwitcherFragment extends BravePreferenceFragment
        implements Preference.OnPreferenceClickListener {

    public static final String PREF_ACTIVE_PROFILE_INFO = "pref_active_profile_info";
    public static final String PREF_PROFILES_CATEGORY = "profiles_list_category";
    public static final String PREF_ADD_PROFILE = "pref_add_profile";

    private final SettableMonotonicObservableSupplier<String> mPageTitle =
            ObservableSuppliers.createMonotonic();

    private ChromeBasePreference mActiveProfilePref;
    private PreferenceCategory mProfilesCategory;
    private ChromeBasePreference mAddProfilePref;

    private static final String[] COLOR_NAMES = new String[] {
        "Blue", "Purple", "Emerald Green", "Orange", "Rose Red"
    };

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
        SettingsUtils.addPreferencesFromResource(this, R.xml.brave_profile_switcher_preferences);
        mPageTitle.set("Profiles");

        mActiveProfilePref = (ChromeBasePreference) findPreference(PREF_ACTIVE_PROFILE_INFO);
        mProfilesCategory = (PreferenceCategory) findPreference(PREF_PROFILES_CATEGORY);
        mAddProfilePref = (ChromeBasePreference) findPreference(PREF_ADD_PROFILE);

        if (mAddProfilePref != null) {
            mAddProfilePref.setOnPreferenceClickListener(this);
        }

        refreshProfilesList();
    }

    @Override
    public MonotonicObservableSupplier<String> getPageTitle() {
        return mPageTitle;
    }

    private void refreshProfilesList() {
        BraveMultiProfileManager manager = BraveMultiProfileManager.getInstance();
        BraveProfileInfo active = manager.getActiveProfile();

        if (mActiveProfilePref != null) {
            mActiveProfilePref.setTitle(active.getName() + " [Active]");
            mActiveProfilePref.setSummary("Directory: " + active.getProfileDir() + "\nTask: " + active.getTaskAffinity());
        }

        if (mProfilesCategory != null) {
            // Remove previous dynamically added profile items, keeping mAddProfilePref
            mProfilesCategory.removeAll();
            if (mAddProfilePref != null) {
                mProfilesCategory.addPreference(mAddProfilePref);
            }

            List<BraveProfileInfo> profiles = manager.getProfiles();
            for (BraveProfileInfo profile : profiles) {
                ChromeBasePreference pref = new ChromeBasePreference(getContext());
                pref.setKey("profile_item_" + profile.getId());
                pref.setIcon(R.drawable.ic_profile_switcher);

                String title = profile.getName();
                if (profile.isCurrent()) {
                    title += " (Current Window)";
                }
                pref.setTitle(title);

                String colorName = (profile.getAvatarColorIndex() >= 0 && profile.getAvatarColorIndex() < COLOR_NAMES.length)
                        ? COLOR_NAMES[profile.getAvatarColorIndex()]
                        : "Color " + profile.getAvatarColorIndex();
                pref.setSummary("Storage: " + profile.getProfileDir() + " • Theme: " + colorName);

                pref.setOnPreferenceClickListener(p -> {
                    showProfileOptionsDialog(profile);
                    return true;
                });

                mProfilesCategory.addPreference(pref);
            }
        }
    }

    private void showProfileOptionsDialog(BraveProfileInfo profile) {
        Context context = getContext();
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(profile.getName());

        if (profile.isCurrent()) {
            String[] options = new String[] {"Edit Profile Name & Color"};
            builder.setItems(options, (dialog, which) -> {
                if (which == 0) {
                    showEditProfileDialog(profile);
                }
            });
        } else {
            String[] options = new String[] {
                "Switch to this Profile (Restart App)",
                "Open in Split Screen / New Window (EMUI 10)",
                "Edit Profile Name & Color",
                "Delete Profile"
            };
            builder.setItems(options, (dialog, which) -> {
                if (which == 0) {
                    confirmSwitchProfile(profile);
                } else if (which == 1) {
                    BraveMultiProfileManager.getInstance().launchProfileInAdjacentWindow(context, profile);
                    Toast.makeText(context, "Opening " + profile.getName() + " in adjacent window...", Toast.LENGTH_SHORT).show();
                } else if (which == 2) {
                    showEditProfileDialog(profile);
                } else if (which == 3) {
                    confirmDeleteProfile(profile);
                }
            });
        }

        builder.setNegativeButton("Cancel", null);
        builder.create().show();
    }

    private void confirmSwitchProfile(BraveProfileInfo profile) {
        Context context = getContext();
        if (context == null) return;

        new AlertDialog.Builder(context)
                .setTitle("Switch to " + profile.getName() + "?")
                .setMessage("Brave will restart in this window with the selected profile.")
                .setPositiveButton("Switch & Restart", (dialog, which) -> {
                    BraveMultiProfileManager.getInstance().setActiveProfile(profile.getId());
                    BraveRelaunchUtils.restart();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteProfile(BraveProfileInfo profile) {
        Context context = getContext();
        if (context == null) return;

        new AlertDialog.Builder(context)
                .setTitle("Delete " + profile.getName() + "?")
                .setMessage("All browsing data, history, and cache for profile '" + profile.getName() + "' will be permanently deleted.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    boolean success = BraveMultiProfileManager.getInstance().deleteProfile(profile.getId());
                    if (success) {
                        Toast.makeText(context, "Profile deleted", Toast.LENGTH_SHORT).show();
                        refreshProfilesList();
                    } else {
                        Toast.makeText(context, "Could not delete profile", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditProfileDialog(BraveProfileInfo profile) {
        Context context = getContext();
        if (context == null) return;

        FrameLayout container = new FrameLayout(context);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, padding / 2);

        final EditText input = new EditText(context);
        input.setHint("Profile Name");
        input.setText(profile.getName());
        input.setSelection(profile.getName().length());
        container.addView(input);

        new AlertDialog.Builder(context)
                .setTitle("Edit Profile")
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(newName)) {
                        BraveMultiProfileManager.getInstance().updateProfile(profile.getId(), newName, profile.getAvatarColorIndex());
                        refreshProfilesList();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddProfileDialog() {
        Context context = getContext();
        if (context == null) return;

        FrameLayout container = new FrameLayout(context);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, padding / 2);

        final EditText input = new EditText(context);
        input.setHint("e.g. Work, Trading, Stealth");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        container.addView(input);

        new AlertDialog.Builder(context)
                .setTitle("Create New Profile")
                .setView(container)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        name = "Profile " + (BraveMultiProfileManager.getInstance().getProfiles().size() + 1);
                    }
                    int colorIdx = (BraveMultiProfileManager.getInstance().getProfiles().size()) % COLOR_NAMES.length;
                    BraveProfileInfo created = BraveMultiProfileManager.getInstance().createProfile(name, colorIdx);
                    if (created != null) {
                        Toast.makeText(context, "Profile '" + created.getName() + "' created", Toast.LENGTH_SHORT).show();
                        refreshProfilesList();
                    } else {
                        Toast.makeText(context, "Could not create profile: maximum limit reached", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (PREF_ADD_PROFILE.equals(preference.getKey())) {
            showAddProfileDialog();
            return true;
        }
        return false;
    }
}
