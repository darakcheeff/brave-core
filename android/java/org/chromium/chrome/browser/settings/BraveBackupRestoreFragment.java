/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import org.chromium.base.supplier.MonotonicObservableSupplier;
import org.chromium.base.supplier.ObservableSuppliers;
import org.chromium.base.supplier.SettableMonotonicObservableSupplier;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.BraveRelaunchUtils;
import org.chromium.chrome.browser.backup.BraveBackupManager;
import org.chromium.components.browser_ui.settings.ChromeBasePreference;
import org.chromium.components.browser_ui.settings.SettingsUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Settings fragment for encrypted portable backup (.bravebak) export and restore via Android SAF.
 */
public class BraveBackupRestoreFragment extends BravePreferenceFragment
        implements Preference.OnPreferenceClickListener {

    public static final String PREF_CREATE_BACKUP = "pref_create_backup";
    public static final String PREF_RESTORE_BACKUP = "pref_restore_backup";

    private static final int REQUEST_CODE_CREATE_BACKUP = 41920;
    private static final int REQUEST_CODE_RESTORE_BACKUP = 41921;

    private final SettableMonotonicObservableSupplier<String> mPageTitle =
            ObservableSuppliers.createMonotonic();

    private ChromeBasePreference mCreateBackupPref;
    private ChromeBasePreference mRestoreBackupPref;

    private @Nullable String mPendingExportPassword;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
        SettingsUtils.addPreferencesFromResource(this, R.xml.brave_backup_restore_preferences);
        mPageTitle.set("Backup & Restore");

        mCreateBackupPref = (ChromeBasePreference) findPreference(PREF_CREATE_BACKUP);
        mRestoreBackupPref = (ChromeBasePreference) findPreference(PREF_RESTORE_BACKUP);

        if (mCreateBackupPref != null) {
            mCreateBackupPref.setOnPreferenceClickListener(this);
        }
        if (mRestoreBackupPref != null) {
            mRestoreBackupPref.setOnPreferenceClickListener(this);
        }
    }

    @Override
    public MonotonicObservableSupplier<String> getPageTitle() {
        return mPageTitle;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (PREF_CREATE_BACKUP.equals(preference.getKey())) {
            showExportPasswordDialog();
            return true;
        } else if (PREF_RESTORE_BACKUP.equals(preference.getKey())) {
            launchFilePickerForImport();
            return true;
        }
        return false;
    }

    private void showExportPasswordDialog() {
        Context context = getContext();
        if (context == null) return;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, padding / 2);

        final EditText passInput = new EditText(context);
        passInput.setHint("Encryption Password (Optional)");
        passInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(passInput);

        new AlertDialog.Builder(context)
                .setTitle("Create Backup (.bravebak)")
                .setMessage("Enter an optional master password to encrypt your profiles, bookmarks, and extensions.")
                .setView(layout)
                .setPositiveButton("Choose Location", (dialog, which) -> {
                    mPendingExportPassword = passInput.getText().toString();
                    launchFilePickerForExport();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void launchFilePickerForExport() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        String defaultFilename = "brave_backup_" + sdf.format(new Date()) + ".bravebak";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, defaultFilename);
        startActivityForResult(intent, REQUEST_CODE_CREATE_BACKUP);
    }

    private void launchFilePickerForImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_CODE_RESTORE_BACKUP);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        Context context = getContext();
        if (context == null) return;

        if (requestCode == REQUEST_CODE_CREATE_BACKUP) {
            final String password = mPendingExportPassword != null ? mPendingExportPassword : "";
            mPendingExportPassword = null;

            Toast.makeText(context, "Exporting backup to file...", Toast.LENGTH_SHORT).show();
            BraveBackupManager.BackupOptions options = new BraveBackupManager.BackupOptions();
            BraveBackupManager.getInstance().exportBackup(context, uri, password, options, success -> {
                if (success) {
                    new AlertDialog.Builder(context)
                            .setTitle("Backup Created")
                            .setMessage("Your encrypted .bravebak archive was successfully saved.")
                            .setPositiveButton("OK", null)
                            .show();
                } else {
                    Toast.makeText(context, "Failed to create backup file", Toast.LENGTH_LONG).show();
                }
            });
        } else if (requestCode == REQUEST_CODE_RESTORE_BACKUP) {
            showImportPasswordDialog(uri);
        }
    }

    private void showImportPasswordDialog(Uri uri) {
        Context context = getContext();
        if (context == null) return;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, padding / 2);

        final EditText passInput = new EditText(context);
        passInput.setHint("Password (leave blank if none)");
        passInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(passInput);

        new AlertDialog.Builder(context)
                .setTitle("Unlock Backup")
                .setMessage("Enter the password used when creating this backup.")
                .setView(layout)
                .setPositiveButton("Unlock", (dialog, which) -> {
                    String password = passInput.getText().toString();
                    verifyAndConfirmRestore(uri, password);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void verifyAndConfirmRestore(Uri uri, String password) {
        Context context = getContext();
        if (context == null) return;

        Toast.makeText(context, "Reading backup archive...", Toast.LENGTH_SHORT).show();
        BraveBackupManager.getInstance().readManifest(context, uri, password, manifest -> {
            if (manifest == null) {
                new AlertDialog.Builder(context)
                        .setTitle("Decryption Failed")
                        .setMessage("Could not read backup. Please check your password or ensure the file is a valid .bravebak container.")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }

            StringBuilder details = new StringBuilder();
            details.append("Profiles in backup: ").append(manifest.profileNames.size()).append("\n");
            for (String name : manifest.profileNames) {
                details.append(" • ").append(name).append("\n");
            }
            details.append("\nRestoring will merge these profiles and restart Brave.");

            new AlertDialog.Builder(context)
                    .setTitle("Restore Profiles & Data?")
                    .setMessage(details.toString())
                    .setPositiveButton("Restore & Restart", (d, w) -> {
                        Toast.makeText(context, "Restoring data...", Toast.LENGTH_SHORT).show();
                        BraveBackupManager.getInstance().importBackup(context, uri, password, success -> {
                            if (success) {
                                BraveRelaunchUtils.restart();
                            } else {
                                Toast.makeText(context, "Restore failed", Toast.LENGTH_LONG).show();
                            }
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }
}
