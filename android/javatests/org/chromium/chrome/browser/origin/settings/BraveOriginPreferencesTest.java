/* Copyright (c) 2025 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.origin.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import android.os.Looper;
import android.view.View;

import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.chromium.base.test.util.CriteriaHelper;
import org.chromium.base.test.util.DoNotBatch;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.settings.BraveOriginPreferences;
import org.chromium.chrome.browser.settings.SettingsActivity;
import org.chromium.chrome.browser.settings.SettingsActivityTestRule;
import org.chromium.chrome.test.ChromeJUnit4ClassRunner;
import org.chromium.components.browser_ui.settings.ChromeSwitchPreference;

/** Test for {@link BraveOriginPreferences}. Main purpose is to check preference defaults. */
@RunWith(ChromeJUnit4ClassRunner.class)
@DoNotBatch(reason = "Tests cannot run batched because they launch a Settings activity.")
public class BraveOriginPreferencesTest {
    private static final String PREF_PRIVACY_PRESERVING_ANALYTICS_SWITCH =
            "privacy_preserving_analytics_switch";
    private static final String PREF_LEO_AI_SWITCH = "leo_ai_switch";
    private static final String PREF_STATISTICS_REPORTING_SWITCH = "statistics_reporting_switch";
    private static final String PREF_WEB_DISCOVERY_PROJECT_SWITCH = "web_discovery_project_switch";

    @Rule
    public final SettingsActivityTestRule<BraveOriginPreferences> mSettingsActivityTestRule =
            new SettingsActivityTestRule<>(BraveOriginPreferences.class);

    private BraveOriginPreferences mBraveOriginPreferences;

    @Before
    public void setup() {
        Looper.prepare();
    }

    // Test that all switch preferences are off by default.
    @Test
    @SmallTest
    public void testAllSwitchPreferencesDefaultValues() {
        startSettings();

        // Test privacy preserving analytics switch
        ChromeSwitchPreference privacyPreservingAnalyticsSwitch =
                (ChromeSwitchPreference)
                        mBraveOriginPreferences.findPreference(
                                PREF_PRIVACY_PRESERVING_ANALYTICS_SWITCH);
        assertNotNull(
                "PREF_PRIVACY_PRESERVING_ANALYTICS_SWITCH should exist",
                privacyPreservingAnalyticsSwitch);
        assertFalse(
                "PREF_PRIVACY_PRESERVING_ANALYTICS_SWITCH should be off by default",
                privacyPreservingAnalyticsSwitch.isChecked());

        // Test Leo AI switch
        ChromeSwitchPreference leoAiSwitch =
                (ChromeSwitchPreference) mBraveOriginPreferences.findPreference(PREF_LEO_AI_SWITCH);
        assertNotNull("PREF_LEO_AI_SWITCH should exist", leoAiSwitch);
        assertFalse("PREF_LEO_AI_SWITCH should be off by default", leoAiSwitch.isChecked());

        // Test statistics reporting switch
        ChromeSwitchPreference statisticsReportingSwitch =
                (ChromeSwitchPreference)
                        mBraveOriginPreferences.findPreference(PREF_STATISTICS_REPORTING_SWITCH);
        assertNotNull("PREF_STATISTICS_REPORTING_SWITCH should exist", statisticsReportingSwitch);
        assertFalse(
                "PREF_STATISTICS_REPORTING_SWITCH should be off by default",
                statisticsReportingSwitch.isChecked());

        // Test web discovery project switch
        ChromeSwitchPreference webDiscoveryProjectSwitch =
                (ChromeSwitchPreference)
                        mBraveOriginPreferences.findPreference(PREF_WEB_DISCOVERY_PROJECT_SWITCH);
        assertNotNull("PREF_WEB_DISCOVERY_PROJECT_SWITCH should exist", webDiscoveryProjectSwitch);
        assertFalse(
                "PREF_WEB_DISCOVERY_PROJECT_SWITCH should be off by default",
                webDiscoveryProjectSwitch.isChecked());
    }

    // Test that launching with EXTRA_SHOW_RESTART_PROMPT shows the restart snackbar (the
    // credential-refresh path, where credentials are already present so there is no spinner).
    @Test
    @SmallTest
    public void testRestartPromptArgShowsRestartSnackbar() {
        Bundle args = new Bundle();
        args.putBoolean(BraveOriginPreferences.EXTRA_SHOW_RESTART_PROMPT, true);
        SettingsActivity activity = mSettingsActivityTestRule.startSettingsActivity(args);
        mBraveOriginPreferences = mSettingsActivityTestRule.getFragment();
        Assert.assertNotNull("SettingsActivity failed to launch.", mBraveOriginPreferences);

        // The snackbar is shown asynchronously from onViewCreated; poll for its
        // "Restart now" action button to appear.
        CriteriaHelper.pollUiThread(
                () -> {
                    View action =
                            activity.getWindow().getDecorView().findViewById(R.id.snackbar_action);
                    return action != null && action.getVisibility() == View.VISIBLE;
                },
                "Restart snackbar should be shown when EXTRA_SHOW_RESTART_PROMPT is set",
                5000L,
                100L);

        // The "fetching credentials" spinner state must not be shown on this path.
        View fetching =
                activity.getWindow().getDecorView().findViewById(R.id.snackbar_fetching_container);
        assertTrue(
                "Fetching spinner should not be shown",
                fetching == null || fetching.getVisibility() != View.VISIBLE);
    }

    private void startSettings() {
        mSettingsActivityTestRule.startSettingsActivity();
        mBraveOriginPreferences = mSettingsActivityTestRule.getFragment();
        Assert.assertNotNull("SettingsActivity failed to launch.", mBraveOriginPreferences);
    }
}
