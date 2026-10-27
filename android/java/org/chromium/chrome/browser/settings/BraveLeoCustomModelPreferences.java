/* Copyright (c) 2024 The Brave Authors. All rights reserved.
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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import org.chromium.base.supplier.MonotonicObservableSupplier;
import org.chromium.base.supplier.ObservableSuppliers;
import org.chromium.base.supplier.SettableMonotonicObservableSupplier;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.brave_leo.BraveLeoMojomHelper;
import org.chromium.chrome.browser.brave_leo.BraveLeoPrefUtils;
import org.chromium.components.browser_ui.settings.ChromeSwitchPreference;
import org.chromium.components.browser_ui.settings.SettingsUtils;
import org.chromium.components.browser_ui.settings.search.BaseSearchIndexProvider;

public class BraveLeoCustomModelPreferences extends BravePreferenceFragment
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private static final String PREF_ENABLE_SWITCH = "custom_provider_enable_switch";
    private static final String PREF_PRESET_GEMINI = "preset_openrouter_gemini";
    private static final String PREF_PRESET_DEEPSEEK = "preset_openrouter_deepseek";
    private static final String PREF_PRESET_OLLAMA = "preset_local_ollama";
    private static final String PREF_ENDPOINT = "custom_endpoint_pref";
    private static final String PREF_API_KEY = "custom_api_key_pref";
    private static final String PREF_MODEL_NAME = "custom_model_name_pref";
    private static final String PREF_CONTEXT_SIZE = "custom_context_size_pref";
    private static final String PREF_SYSTEM_PROMPT = "custom_system_prompt_pref";

    private final SettableMonotonicObservableSupplier<String> mPageTitle =
            ObservableSuppliers.createMonotonic();

    private ChromeSwitchPreference mEnableSwitch;
    private Preference mEndpointPref;
    private Preference mApiKeyPref;
    private Preference mModelNamePref;
    private Preference mContextSizePref;
    private Preference mSystemPromptPref;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
        SettingsUtils.addPreferencesFromResource(this, R.xml.brave_leo_custom_model_preferences);
        mPageTitle.set("Custom AI Model");

        mEnableSwitch = (ChromeSwitchPreference) findPreference(PREF_ENABLE_SWITCH);
        if (mEnableSwitch != null) {
            mEnableSwitch.setChecked(BraveLeoPrefUtils.isCustomProviderEnabled());
            mEnableSwitch.setOnPreferenceChangeListener(this);
        }

        Preference presetGemini = findPreference(PREF_PRESET_GEMINI);
        if (presetGemini != null) {
            presetGemini.setOnPreferenceClickListener(this);
        }
        Preference presetDeepSeek = findPreference(PREF_PRESET_DEEPSEEK);
        if (presetDeepSeek != null) {
            presetDeepSeek.setOnPreferenceClickListener(this);
        }
        Preference presetOllama = findPreference(PREF_PRESET_OLLAMA);
        if (presetOllama != null) {
            presetOllama.setOnPreferenceClickListener(this);
        }

        mEndpointPref = findPreference(PREF_ENDPOINT);
        if (mEndpointPref != null) {
            mEndpointPref.setOnPreferenceClickListener(this);
        }
        mApiKeyPref = findPreference(PREF_API_KEY);
        if (mApiKeyPref != null) {
            mApiKeyPref.setOnPreferenceClickListener(this);
        }
        mModelNamePref = findPreference(PREF_MODEL_NAME);
        if (mModelNamePref != null) {
            mModelNamePref.setOnPreferenceClickListener(this);
        }
        mContextSizePref = findPreference(PREF_CONTEXT_SIZE);
        if (mContextSizePref != null) {
            mContextSizePref.setOnPreferenceClickListener(this);
        }
        mSystemPromptPref = findPreference(PREF_SYSTEM_PROMPT);
        if (mSystemPromptPref != null) {
            mSystemPromptPref.setOnPreferenceClickListener(this);
        }

        updateSummaries();
    }

    private void updateSummaries() {
        if (mEndpointPref != null) {
            mEndpointPref.setSummary(BraveLeoPrefUtils.getCustomProviderEndpoint());
        }
        if (mApiKeyPref != null) {
            String key = BraveLeoPrefUtils.getCustomProviderApiKey();
            if (TextUtils.isEmpty(key)) {
                mApiKeyPref.setSummary("Not set");
            } else if (key.length() > 8) {
                mApiKeyPref.setSummary(key.substring(0, 4) + "..." + key.substring(key.length() - 4));
            } else {
                mApiKeyPref.setSummary("••••••••");
            }
        }
        if (mModelNamePref != null) {
            mModelNamePref.setSummary(BraveLeoPrefUtils.getCustomProviderModelName());
        }
        if (mContextSizePref != null) {
            mContextSizePref.setSummary(String.valueOf(BraveLeoPrefUtils.getCustomProviderContextSize()));
        }
        if (mSystemPromptPref != null) {
            String prompt = BraveLeoPrefUtils.getCustomProviderSystemPrompt();
            mSystemPromptPref.setSummary(TextUtils.isEmpty(prompt) ? "Default system prompt" : prompt);
        }
    }

    @Override
    public MonotonicObservableSupplier<String> getPageTitle() {
        return mPageTitle;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (PREF_ENABLE_SWITCH.equals(preference.getKey())) {
            boolean enabled = (Boolean) newValue;
            BraveLeoPrefUtils.setCustomProviderEnabled(enabled);
            BraveLeoMojomHelper.getInstance(getProfile()).applyCustomProviderSettings();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        if (PREF_PRESET_GEMINI.equals(key)) {
            BraveLeoPrefUtils.setCustomProviderEndpoint("https://openrouter.ai/api/v1/chat/completions");
            BraveLeoPrefUtils.setCustomProviderModelName("google/gemini-2.5-flash");
            BraveLeoPrefUtils.setCustomProviderContextSize(32768);
            updateSummaries();
            BraveLeoMojomHelper.getInstance(getProfile()).applyCustomProviderSettings();
            return true;
        } else if (PREF_PRESET_DEEPSEEK.equals(key)) {
            BraveLeoPrefUtils.setCustomProviderEndpoint("https://openrouter.ai/api/v1/chat/completions");
            BraveLeoPrefUtils.setCustomProviderModelName("deepseek/deepseek-chat");
            BraveLeoPrefUtils.setCustomProviderContextSize(32768);
            updateSummaries();
            BraveLeoMojomHelper.getInstance(getProfile()).applyCustomProviderSettings();
            return true;
        } else if (PREF_PRESET_OLLAMA.equals(key)) {
            BraveLeoPrefUtils.setCustomProviderEndpoint("http://localhost:11434/v1/chat/completions");
            BraveLeoPrefUtils.setCustomProviderModelName("llama3.2");
            BraveLeoPrefUtils.setCustomProviderContextSize(8192);
            updateSummaries();
            BraveLeoMojomHelper.getInstance(getProfile()).applyCustomProviderSettings();
            return true;
        } else if (PREF_ENDPOINT.equals(key)) {
            showEditTextDialog("API Endpoint", BraveLeoPrefUtils.getCustomProviderEndpoint(),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, (text) -> {
                        BraveLeoPrefUtils.setCustomProviderEndpoint(text.trim());
                        updateSummaries();
                        BraveLeoMojomHelper.getInstance(getProfile()).applyCustomProviderSettings();
                    });
            return true;
        } else if (PREF_API_KEY.equals(key)) {
            showEditTextDialog("API Key", BraveLeoPrefUtils.getCustomProviderApiKey(),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, (text) -> {
                        BraveLeoPrefUtils.setCustomProviderApiKey(text.trim());
                        updateSummaries();
                        BraveLeoMojomHelper.getInstance(getProfile()).applyCustomProviderSettings();
                    });
            return true;
        } else if (PREF_MODEL_NAME.equals(key)) {
            showEditTextDialog("Model Identifier", BraveLeoPrefUtils.getCustomProviderModelName(),
                    InputType.TYPE_CLASS_TEXT, (text) -> {
                        BraveLeoPrefUtils.setCustomProviderModelName(text.trim());
                        updateSummaries();
                        BraveLeoMojomHelper.getInstance(getProfile()).applyCustomProviderSettings();
                    });
            return true;
        } else if (PREF_CONTEXT_SIZE.equals(key)) {
            showEditTextDialog("Context Size (Tokens)", String.valueOf(BraveLeoPrefUtils.getCustomProviderContextSize()),
                    InputType.TYPE_CLASS_NUMBER, (text) -> {
                        try {
                            int size = Integer.parseInt(text.trim());
                            BraveLeoPrefUtils.setCustomProviderContextSize(size);
                        } catch (NumberFormatException ignored) {}
                        updateSummaries();
                        BraveLeoMojomHelper.getInstance(getProfile()).applyCustomProviderSettings();
                    });
            return true;
        } else if (PREF_SYSTEM_PROMPT.equals(key)) {
            showEditTextDialog("Custom System Prompt", BraveLeoPrefUtils.getCustomProviderSystemPrompt(),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, (text) -> {
                        BraveLeoPrefUtils.setCustomProviderSystemPrompt(text.trim());
                        updateSummaries();
                        BraveLeoMojomHelper.getInstance(getProfile()).applyCustomProviderSettings();
                    });
            return true;
        }
        return false;
    }

    private interface OnTextEnteredListener {
        void onTextEntered(String text);
    }

    private void showEditTextDialog(String title, String initialText, int inputType, OnTextEnteredListener listener) {
        Context context = getContext();
        if (context == null) return;

        final EditText input = new EditText(context);
        input.setInputType(inputType);
        if (!TextUtils.isEmpty(initialText)) {
            input.setText(initialText);
            input.setSelection(initialText.length());
        }

        FrameLayout container = new FrameLayout(context);
        int margin = (int) (16 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = margin;
        params.rightMargin = margin;
        input.setLayoutParams(params);
        container.addView(input);

        new AlertDialog.Builder(context, R.style.ThemeOverlay_BrowserUI_AlertDialog)
                .setTitle(title)
                .setView(container)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    listener.onTextEntered(input.getText().toString());
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(
                    BraveLeoCustomModelPreferences.class.getName(),
                    BaseSearchIndexProvider.INDEX_OPT_OUT);
}
