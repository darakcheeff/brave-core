/* Copyright (c) 2023 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.brave_leo;

import org.chromium.base.supplier.OneshotSupplier;
import org.chromium.chrome.browser.init.ActivityProfileProvider;
import org.chromium.chrome.browser.init.AsyncInitializationActivity;
import org.chromium.chrome.browser.profiles.ProfileProvider;

/** Stubbed Brave Leo Plans Activity - paywall completely removed. */
public class BraveLeoPlansActivity extends AsyncInitializationActivity {
    @Override
    public boolean shouldStartGpuProcess() {
        return false;
    }

    @Override
    protected void triggerLayoutInflation() {
        // Feature unlocked, immediately close if launched.
        finish();
    }

    @Override
    protected OneshotSupplier<ProfileProvider> createProfileProvider() {
        return new ActivityProfileProvider(getLifecycleDispatcher());
    }
}
