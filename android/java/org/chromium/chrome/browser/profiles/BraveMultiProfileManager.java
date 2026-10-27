/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.profiles;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.task.PostTask;
import org.chromium.base.task.TaskTraits;
import org.chromium.chrome.browser.ChromeTabbedActivity;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages isolated multi-profile data and EMUI 10 multi-window tasks on Android.
 */
public class BraveMultiProfileManager {
    private static final String TAG = "BraveMultiProfileMgr";

    public static final String PREF_BRAVE_PROFILES_JSON = "brave_profiles_json";
    public static final String PREF_ACTIVE_PROFILE_ID = "brave_active_profile_id";
    public static final String PREF_ACTIVE_PROFILE_DIR = "brave_active_profile_directory";

    public static final int MAX_PROFILES = 5; // Default + 4 Multi-Instance activities

    private static @Nullable BraveMultiProfileManager sInstance;

    private BraveMultiProfileManager() {}

    public static synchronized BraveMultiProfileManager getInstance() {
        if (sInstance == null) {
            sInstance = new BraveMultiProfileManager();
        }
        return sInstance;
    }

    private SharedPreferences getPrefs() {
        return ContextUtils.getAppSharedPreferences();
    }

    public synchronized List<BraveProfileInfo> getProfiles() {
        List<BraveProfileInfo> list = new ArrayList<>();
        String jsonStr = getPrefs().getString(PREF_BRAVE_PROFILES_JSON, null);
        String activeId = getActiveProfileId();

        if (TextUtils.isEmpty(jsonStr)) {
            BraveProfileInfo defaultProfile = BraveProfileInfo.createDefault();
            defaultProfile.setCurrent(true);
            list.add(defaultProfile);
            saveProfiles(list);
            return list;
        }

        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                BraveProfileInfo info = BraveProfileInfo.fromJson(arr.getJSONObject(i));
                if (info != null) {
                    info.setCurrent(info.getId().equals(activeId));
                    list.add(info);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse profiles JSON: ", e);
        }

        if (list.isEmpty()) {
            BraveProfileInfo defaultProfile = BraveProfileInfo.createDefault();
            defaultProfile.setCurrent(true);
            list.add(defaultProfile);
            saveProfiles(list);
        }

        return list;
    }

    public synchronized BraveProfileInfo getActiveProfile() {
        String activeId = getActiveProfileId();
        List<BraveProfileInfo> profiles = getProfiles();
        for (BraveProfileInfo info : profiles) {
            if (info.getId().equals(activeId)) {
                return info;
            }
        }
        return profiles.get(0);
    }

    public String getActiveProfileId() {
        return getPrefs().getString(PREF_ACTIVE_PROFILE_ID, BraveProfileInfo.DEFAULT_PROFILE_ID);
    }

    public String getActiveProfileDir() {
        return getPrefs().getString(PREF_ACTIVE_PROFILE_DIR, BraveProfileInfo.DEFAULT_PROFILE_DIR);
    }

    public synchronized boolean setActiveProfile(String profileId) {
        List<BraveProfileInfo> profiles = getProfiles();
        BraveProfileInfo target = null;
        for (BraveProfileInfo info : profiles) {
            if (info.getId().equals(profileId)) {
                target = info;
                break;
            }
        }
        if (target == null) return false;

        getPrefs().edit()
                .putString(PREF_ACTIVE_PROFILE_ID, target.getId())
                .putString(PREF_ACTIVE_PROFILE_DIR, target.getProfileDir())
                .apply();

        for (BraveProfileInfo info : profiles) {
            info.setCurrent(info.getId().equals(profileId));
        }
        saveProfiles(profiles);
        return true;
    }

    public synchronized @Nullable BraveProfileInfo createProfile(String name, int avatarColorIndex) {
        List<BraveProfileInfo> profiles = getProfiles();
        if (profiles.size() >= MAX_PROFILES) {
            Log.w(TAG, "Maximum number of profiles reached (" + MAX_PROFILES + ")");
            return null;
        }

        // Find the lowest unused slot (profile_1 .. profile_4)
        String newId = null;
        int slotNumber = 1;
        for (int i = 1; i <= 4; i++) {
            String candidateId = "profile_" + i;
            boolean inUse = false;
            for (BraveProfileInfo existing : profiles) {
                if (existing.getId().equals(candidateId)) {
                    inUse = true;
                    break;
                }
            }
            if (!inUse) {
                newId = candidateId;
                slotNumber = i;
                break;
            }
        }

        if (newId == null) {
            return null;
        }

        String profileDir = "Profiles/" + newId;
        String taskAffinity = "org.chromium.chrome.browser.BraveActivity.Profile_" + slotNumber;
        BraveProfileInfo newProfile = new BraveProfileInfo(
                newId,
                name,
                avatarColorIndex,
                profileDir,
                taskAffinity,
                false,
                System.currentTimeMillis());

        profiles.add(newProfile);
        saveProfiles(profiles);
        return newProfile;
    }

    public synchronized boolean updateProfile(String id, String newName, int newAvatarColorIndex) {
        List<BraveProfileInfo> profiles = getProfiles();
        boolean found = false;
        for (BraveProfileInfo info : profiles) {
            if (info.getId().equals(id)) {
                info.setName(newName);
                info.setAvatarColorIndex(newAvatarColorIndex);
                found = true;
                break;
            }
        }
        if (found) {
            saveProfiles(profiles);
        }
        return found;
    }

    public synchronized boolean deleteProfile(String id) {
        if (BraveProfileInfo.DEFAULT_PROFILE_ID.equals(id)) {
            Log.w(TAG, "Cannot delete default profile");
            return false;
        }

        if (id.equals(getActiveProfileId())) {
            Log.w(TAG, "Cannot delete currently active profile");
            return false;
        }

        List<BraveProfileInfo> profiles = getProfiles();
        BraveProfileInfo toRemove = null;
        for (BraveProfileInfo info : profiles) {
            if (info.getId().equals(id)) {
                toRemove = info;
                break;
            }
        }

        if (toRemove == null) return false;

        profiles.remove(toRemove);
        saveProfiles(profiles);

        // Delete profile data directory asynchronously
        final String dirToDelete = toRemove.getProfileDir();
        PostTask.postTask(TaskTraits.BEST_EFFORT_MAY_BLOCK, () -> {
            try {
                Context context = ContextUtils.getApplicationContext();
                File appChromeDir = new File(context.getApplicationInfo().dataDir, "app_chrome");
                File profileFolder = new File(appChromeDir, dirToDelete);
                if (profileFolder.exists()) {
                    deleteRecursively(profileFolder);
                    Log.i(TAG, "Deleted profile storage at: " + profileFolder.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to clean profile directory: ", e);
            }
        });

        return true;
    }

    private void deleteRecursively(File fileOrDir) {
        if (fileOrDir.isDirectory()) {
            File[] children = fileOrDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        fileOrDir.delete();
    }

    private void saveProfiles(List<BraveProfileInfo> profiles) {
        JSONArray arr = new JSONArray();
        for (BraveProfileInfo info : profiles) {
            try {
                arr.put(info.toJson());
            } catch (JSONException e) {
                Log.e(TAG, "Error serializing profile: ", e);
            }
        }
        getPrefs().edit().putString(PREF_BRAVE_PROFILES_JSON, arr.toString()).apply();
    }

    public static Class<? extends Activity> getActivityClassForProfile(String profileId) {
        if ("profile_1".equals(profileId)) {
            return BraveMultiInstanceActivity1.class;
        } else if ("profile_2".equals(profileId)) {
            return BraveMultiInstanceActivity2.class;
        } else if ("profile_3".equals(profileId)) {
            return BraveMultiInstanceActivity3.class;
        } else if ("profile_4".equals(profileId)) {
            return BraveMultiInstanceActivity4.class;
        }
        return ChromeTabbedActivity.class;
    }

    /**
     * Launches a secondary profile into an independent Android 10 EMUI split-screen task window.
     */
    public void launchProfileInAdjacentWindow(Context context, BraveProfileInfo profile) {
        Class<? extends Activity> activityClass = getActivityClassForProfile(profile.getId());
        Intent intent = new Intent(context, activityClass);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        // FLAG_ACTIVITY_LAUNCH_ADJACENT instructs Android 10 EMUI to position the activity
        // side-by-side in split-screen / multi-window mode.
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        context.startActivity(intent);
    }
}
