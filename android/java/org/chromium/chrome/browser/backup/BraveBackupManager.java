/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.backup;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import org.chromium.base.Callback;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.task.PostTask;
import org.chromium.base.task.TaskTraits;
import org.chromium.chrome.browser.profiles.BraveMultiProfileManager;
import org.chromium.chrome.browser.profiles.BraveProfileInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Manages export and import of encrypted .bravebak archives using Android Storage Access Framework (SAF).
 */
public class BraveBackupManager {
    private static final String TAG = "BraveBackupManager";

    public static class BackupOptions {
        public boolean includeBookmarks = true;
        public boolean includeHistory = true;
        public boolean includeExtensions = true;
        public boolean includeShields = true;
        public boolean includeCookies = true;
        public List<String> profileIds = new ArrayList<>();
    }

    public static class BackupManifest {
        public int version = 1;
        public long timestamp;
        public String appVersion = "";
        public List<String> profileNames = new ArrayList<>();
        public List<String> profileIds = new ArrayList<>();
        public boolean hasBookmarks;
        public boolean hasHistory;
        public boolean hasExtensions;
        public boolean hasShields;
    }

    private static @Nullable BraveBackupManager sInstance;

    private BraveBackupManager() {}

    public static synchronized BraveBackupManager getInstance() {
        if (sInstance == null) {
            sInstance = new BraveBackupManager();
        }
        return sInstance;
    }

    public void exportBackup(
            Context context,
            Uri destinationUri,
            String password,
            BackupOptions options,
            Callback<Boolean> callback) {
        PostTask.postTask(TaskTraits.USER_BLOCKING_MAY_BLOCK, () -> {
            boolean success = false;
            try (OutputStream rawOut = context.getContentResolver().openOutputStream(destinationUri);
                 OutputStream encOut = BraveBackupCrypto.createEncryptedOutputStream(rawOut, password);
                 ZipOutputStream zipOut = new ZipOutputStream(encOut)) {

                // Build manifest
                JSONObject manifestJson = new JSONObject();
                manifestJson.put("version", 1);
                manifestJson.put("timestamp", System.currentTimeMillis());
                manifestJson.put("app_version", "Brave Custom Android");

                JSONArray profilesArr = new JSONArray();
                List<BraveProfileInfo> allProfiles = BraveMultiProfileManager.getInstance().getProfiles();

                File appChromeDir = new File(context.getApplicationInfo().dataDir, "app_chrome");

                for (BraveProfileInfo profile : allProfiles) {
                    if (!options.profileIds.isEmpty() && !options.profileIds.contains(profile.getId())) {
                        continue;
                    }
                    profilesArr.put(profile.toJson());

                    File profileFolder = new File(appChromeDir, profile.getProfileDir());
                    if (!profileFolder.exists()) continue;

                    String prefix = "profiles/" + profile.getId() + "/";

                    // Bookmarks
                    if (options.includeBookmarks) {
                        File bookmarksFile = new File(profileFolder, "Bookmarks");
                        if (bookmarksFile.exists()) {
                            addFileToZip(zipOut, bookmarksFile, prefix + "Bookmarks");
                        }
                    }

                    // Preferences
                    File prefsFile = new File(profileFolder, "Preferences");
                    if (prefsFile.exists()) {
                        addFileToZip(zipOut, prefsFile, prefix + "Preferences");
                    }

                    // History
                    if (options.includeHistory) {
                        File historyFile = new File(profileFolder, "History");
                        if (historyFile.exists()) {
                            addFileToZip(zipOut, historyFile, prefix + "History");
                        }
                        File historyWal = new File(profileFolder, "History-wal");
                        if (historyWal.exists()) {
                            addFileToZip(zipOut, historyWal, prefix + "History-wal");
                        }
                    }

                    // Cookies
                    if (options.includeCookies) {
                        File cookiesFile = new File(profileFolder, "Network/Cookies");
                        if (cookiesFile.exists()) {
                            addFileToZip(zipOut, cookiesFile, prefix + "Network/Cookies");
                        }
                    }

                    // Extensions
                    if (options.includeExtensions) {
                        File extensionsDir = new File(profileFolder, "Extensions");
                        if (extensionsDir.exists()) {
                            addDirectoryToZip(zipOut, extensionsDir, prefix + "Extensions/");
                        }
                        File extSettingsDir = new File(profileFolder, "Local Extension Settings");
                        if (extSettingsDir.exists()) {
                            addDirectoryToZip(zipOut, extSettingsDir, prefix + "Local Extension Settings/");
                        }
                    }
                }

                manifestJson.put("profiles", profilesArr);

                // Global Shields rules & local state
                if (options.includeShields) {
                    File localStateFile = new File(appChromeDir, "Local State");
                    if (localStateFile.exists()) {
                        addFileToZip(zipOut, localStateFile, "global/Local State");
                    }
                }

                // Add manifest.json as the first entry
                ZipEntry manifestEntry = new ZipEntry("manifest.json");
                zipOut.putNextEntry(manifestEntry);
                zipOut.write(manifestJson.toString(2).getBytes(StandardCharsets.UTF_8));
                zipOut.closeEntry();

                zipOut.finish();
                success = true;
                Log.i(TAG, "Backup successfully exported to " + destinationUri);
            } catch (Exception e) {
                Log.e(TAG, "Error exporting backup: ", e);
            }

            final boolean finalSuccess = success;
            PostTask.postTask(TaskTraits.UI_DEFAULT, () -> callback.onResult(finalSuccess));
        });
    }

    public void readManifest(
            Context context,
            Uri sourceUri,
            String password,
            Callback<BackupManifest> callback) {
        PostTask.postTask(TaskTraits.USER_BLOCKING_MAY_BLOCK, () -> {
            BackupManifest manifest = null;
            try (InputStream rawIn = context.getContentResolver().openInputStream(sourceUri);
                 InputStream decIn = BraveBackupCrypto.createDecryptedInputStream(rawIn, password);
                 ZipInputStream zipIn = new ZipInputStream(decIn)) {

                ZipEntry entry;
                while ((entry = zipIn.getNextEntry()) != null) {
                    if ("manifest.json".equals(entry.getName())) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buf = new byte[4096];
                        int len;
                        while ((len = zipIn.read(buf)) != -1) {
                            baos.write(buf, 0, len);
                        }
                        String jsonStr = baos.toString(StandardCharsets.UTF_8.name());
                        JSONObject json = new JSONObject(jsonStr);

                        manifest = new BackupManifest();
                        manifest.version = json.optInt("version", 1);
                        manifest.timestamp = json.optLong("timestamp", 0);
                        manifest.appVersion = json.optString("app_version", "Brave Android");

                        JSONArray profilesArr = json.optJSONArray("profiles");
                        if (profilesArr != null) {
                            for (int i = 0; i < profilesArr.length(); i++) {
                                JSONObject p = profilesArr.getJSONObject(i);
                                manifest.profileIds.add(p.optString("id", "profile"));
                                manifest.profileNames.add(p.optString("name", "Profile"));
                            }
                        }
                        break;
                    }
                    zipIn.closeEntry();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading backup manifest: ", e);
            }

            final BackupManifest finalManifest = manifest;
            PostTask.postTask(TaskTraits.UI_DEFAULT, () -> callback.onResult(finalManifest));
        });
    }

    public void importBackup(
            Context context,
            Uri sourceUri,
            String password,
            Callback<Boolean> callback) {
        PostTask.postTask(TaskTraits.USER_BLOCKING_MAY_BLOCK, () -> {
            boolean success = false;
            try (InputStream rawIn = context.getContentResolver().openInputStream(sourceUri);
                 InputStream decIn = BraveBackupCrypto.createDecryptedInputStream(rawIn, password);
                 ZipInputStream zipIn = new ZipInputStream(decIn)) {

                File appChromeDir = new File(context.getApplicationInfo().dataDir, "app_chrome");
                if (!appChromeDir.exists()) {
                    appChromeDir.mkdirs();
                }

                ZipEntry entry;
                byte[] buffer = new byte[8192];

                while ((entry = zipIn.getNextEntry()) != null) {
                    String name = entry.getName();

                    if ("manifest.json".equals(name)) {
                        // Process manifest to restore profile info in BraveMultiProfileManager
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        int len;
                        while ((len = zipIn.read(buffer)) != -1) {
                            baos.write(buffer, 0, len);
                        }
                        try {
                            JSONObject json = new JSONObject(baos.toString(StandardCharsets.UTF_8.name()));
                            JSONArray profilesArr = json.optJSONArray("profiles");
                            if (profilesArr != null) {
                                restoreProfilesMetadata(profilesArr);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing manifest on restore: ", e);
                        }
                        zipIn.closeEntry();
                        continue;
                    }

                    File targetFile = null;
                    if (name.startsWith("global/")) {
                        String subPath = name.substring("global/".length());
                        targetFile = new File(appChromeDir, subPath);
                    } else if (name.startsWith("profiles/")) {
                        // Example: profiles/default/Bookmarks -> app_chrome/Default/Bookmarks
                        // profiles/profile_1/Bookmarks -> app_chrome/Profiles/profile_1/Bookmarks
                        String rel = name.substring("profiles/".length());
                        int slashIdx = rel.indexOf('/');
                        if (slashIdx != -1) {
                            String profileId = rel.substring(0, slashIdx);
                            String subPath = rel.substring(slashIdx + 1);
                            String profileFolder = BraveProfileInfo.DEFAULT_PROFILE_ID.equals(profileId)
                                    ? BraveProfileInfo.DEFAULT_PROFILE_DIR
                                    : "Profiles/" + profileId;
                            targetFile = new File(new File(appChromeDir, profileFolder), subPath);
                        }
                    }

                    if (targetFile != null) {
                        if (entry.isDirectory()) {
                            targetFile.mkdirs();
                        } else {
                            File parent = targetFile.getParentFile();
                            if (parent != null && !parent.exists()) {
                                parent.mkdirs();
                            }
                            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                                int len;
                                while ((len = zipIn.read(buffer)) != -1) {
                                    fos.write(buffer, 0, len);
                                }
                            }
                        }
                    }

                    zipIn.closeEntry();
                }

                success = true;
                Log.i(TAG, "Backup successfully imported from " + sourceUri);
            } catch (Exception e) {
                Log.e(TAG, "Error importing backup: ", e);
            }

            final boolean finalSuccess = success;
            PostTask.postTask(TaskTraits.UI_DEFAULT, () -> callback.onResult(finalSuccess));
        });
    }

    private void restoreProfilesMetadata(JSONArray profilesArr) {
        BraveMultiProfileManager manager = BraveMultiProfileManager.getInstance();
        List<BraveProfileInfo> existing = manager.getProfiles();

        for (int i = 0; i < profilesArr.length(); i++) {
            JSONObject obj = profilesArr.optJSONObject(i);
            if (obj == null) continue;
            BraveProfileInfo imported = BraveProfileInfo.fromJson(obj);
            if (imported == null) continue;

            boolean found = false;
            for (BraveProfileInfo ex : existing) {
                if (ex.getId().equals(imported.getId())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                manager.createProfile(imported.getName(), imported.getAvatarColorIndex());
            }
        }
    }

    private void addFileToZip(ZipOutputStream zipOut, File file, String zipPath) throws Exception {
        if (!file.exists()) return;
        ZipEntry entry = new ZipEntry(zipPath);
        zipOut.putNextEntry(entry);
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) != -1) {
                zipOut.write(buf, 0, len);
            }
        }
        zipOut.closeEntry();
    }

    private void addDirectoryToZip(ZipOutputStream zipOut, File dir, String zipPrefix) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                addDirectoryToZip(zipOut, f, zipPrefix + f.getName() + "/");
            } else {
                addFileToZip(zipOut, f, zipPrefix + f.getName());
            }
        }
    }
}
