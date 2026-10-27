/* Copyright (c) 2022 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.rate;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * Disabled for debloated build: no Play Store rating prompts or review API dependencies.
 */
public class BraveAskPlayStoreRatingDialog extends BottomSheetDialogFragment {
    public static final String TAG_FRAGMENT = "brave_ask_play_store_rating_dialog_tag";

    public static BraveAskPlayStoreRatingDialog newInstance(boolean isFromSettings) {
        return new BraveAskPlayStoreRatingDialog();
    }

    @Override
    public void show(@NonNull FragmentManager manager, @Nullable String tag) {
        // No-op in custom build
    }
}

