/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.profiles;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.Nullable;

/**
 * Encapsulates metadata for an isolated profile on Android.
 */
public class BraveProfileInfo {
    private final String mId;
    private String mName;
    private int mAvatarColorIndex;
    private final String mProfileDir;
    private final String mTaskAffinity;
    private boolean mIsCurrent;
    private final long mCreatedAt;

    public static final String DEFAULT_PROFILE_ID = "default";
    public static final String DEFAULT_PROFILE_DIR = "Default";
    public static final String DEFAULT_TASK_AFFINITY = "org.chromium.chrome.browser.BraveActivity";

    // Predefined vibrant profile avatar colors (Blue, Purple, Emerald, Orange, Rose)
    public static final int[] AVATAR_COLORS = new int[] {
        0xFF3B82F6, // Blue
        0xFF8B5CF6, // Purple
        0xFF10B981, // Emerald Green
        0xFFF97316, // Orange
        0xFFF43F5E  // Rose Red
    };

    public BraveProfileInfo(
            String id,
            String name,
            int avatarColorIndex,
            String profileDir,
            String taskAffinity,
            boolean isCurrent,
            long createdAt) {
        mId = id;
        mName = name;
        mAvatarColorIndex = avatarColorIndex;
        mProfileDir = profileDir;
        mTaskAffinity = taskAffinity;
        mIsCurrent = isCurrent;
        mCreatedAt = createdAt;
    }

    public static BraveProfileInfo createDefault() {
        return new BraveProfileInfo(
                DEFAULT_PROFILE_ID,
                "Personal",
                0,
                DEFAULT_PROFILE_DIR,
                DEFAULT_TASK_AFFINITY,
                true,
                System.currentTimeMillis());
    }

    public String getId() {
        return mId;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    public int getAvatarColorIndex() {
        return mAvatarColorIndex;
    }

    public void setAvatarColorIndex(int colorIndex) {
        mAvatarColorIndex = colorIndex;
    }

    public int getAvatarColor() {
        if (mAvatarColorIndex >= 0 && mAvatarColorIndex < AVATAR_COLORS.length) {
            return AVATAR_COLORS[mAvatarColorIndex];
        }
        return AVATAR_COLORS[0];
    }

    public String getProfileDir() {
        return mProfileDir;
    }

    public String getTaskAffinity() {
        return mTaskAffinity;
    }

    public boolean isCurrent() {
        return mIsCurrent;
    }

    public void setCurrent(boolean current) {
        mIsCurrent = current;
    }

    public long getCreatedAt() {
        return mCreatedAt;
    }

    public boolean isDefault() {
        return DEFAULT_PROFILE_ID.equals(mId);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", mId);
        json.put("name", mName);
        json.put("avatar_color_index", mAvatarColorIndex);
        json.put("profile_dir", mProfileDir);
        json.put("task_affinity", mTaskAffinity);
        json.put("is_current", mIsCurrent);
        json.put("created_at", mCreatedAt);
        return json;
    }

    public static @Nullable BraveProfileInfo fromJson(JSONObject json) {
        try {
            String id = json.getString("id");
            String name = json.optString("name", "Profile");
            int colorIndex = json.optInt("avatar_color_index", 0);
            String dir = json.optString("profile_dir", DEFAULT_PROFILE_DIR);
            String affinity = json.optString("task_affinity", DEFAULT_TASK_AFFINITY);
            boolean isCurrent = json.optBoolean("is_current", false);
            long createdAt = json.optLong("created_at", System.currentTimeMillis());
            return new BraveProfileInfo(id, name, colorIndex, dir, affinity, isCurrent, createdAt);
        } catch (JSONException e) {
            return null;
        }
    }
}
