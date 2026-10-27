/* Copyright (c) 2023 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.brave_leo;

import org.jni_zero.JNINamespace;
import org.jni_zero.NativeMethods;

import org.chromium.ai_chat.mojom.AiChatSettingsHelper;
import org.chromium.ai_chat.mojom.CustomModelOptions;
import org.chromium.ai_chat.mojom.Model;
import org.chromium.ai_chat.mojom.ModelOptions;
import org.chromium.ai_chat.mojom.ModelWithSubtitle;
import org.chromium.ai_chat.mojom.OperationResult;
import org.chromium.ai_chat.mojom.PremiumStatus;
import org.chromium.chrome.browser.browsing_data.TimePeriod;
import org.chromium.content_public.browser.BrowserContextHandle;
import org.chromium.mojo.bindings.ConnectionErrorHandler;
import org.chromium.mojo.bindings.Interface;
import org.chromium.mojo.bindings.Interface.Proxy.Handler;
import org.chromium.mojo.system.MessagePipeHandle;
import org.chromium.mojo.system.MojoException;
import org.chromium.mojo.system.impl.CoreImpl;
import org.chromium.url.mojom.Url;

/**
 * Helper to interact with native AIChatSettingsHelper. Check ai_chat_settings_helper.{h|cc}, for
 * native parts
 */
@JNINamespace("ai_chat")
public class BraveLeoMojomHelper implements ConnectionErrorHandler {
    private long mNativeAIChatCMHelperAndroid;
    AiChatSettingsHelper mAIChatAndroidHelper;

    private static final Object sLock = new Object();
    private static BraveLeoMojomHelper sInstance;

    public static BraveLeoMojomHelper getInstance(BrowserContextHandle browserContextHandle) {
        synchronized (sLock) {
            if (sInstance == null) {
                sInstance = new BraveLeoMojomHelper(browserContextHandle);
            }
        }
        return sInstance;
    }

    private BraveLeoMojomHelper(BrowserContextHandle browserContextHandle) {
        mNativeAIChatCMHelperAndroid = BraveLeoMojomHelperJni.get().init(browserContextHandle);
        long nativeHandle =
                BraveLeoMojomHelperJni.get()
                        .getInterfaceToAndroidHelper(mNativeAIChatCMHelperAndroid);
        MessagePipeHandle handle =
                CoreImpl.getInstance().acquireNativeHandle(nativeHandle).toMessagePipeHandle();
        mAIChatAndroidHelper = AiChatSettingsHelper.MANAGER.attachProxy(handle, 0);
        Handler handler = ((Interface.Proxy) mAIChatAndroidHelper).getProxyHandler();
        handler.setErrorHandler(this);
    }

    private void destroy() {
        synchronized (sLock) {
            sInstance = null;
            if (mNativeAIChatCMHelperAndroid == 0 && mAIChatAndroidHelper == null) {
                return;
            }
            mAIChatAndroidHelper.close();
            mAIChatAndroidHelper = null;
            BraveLeoMojomHelperJni.get().destroy(mNativeAIChatCMHelperAndroid);
            mNativeAIChatCMHelperAndroid = 0;
        }
    }

    public void getPremiumStatus(AiChatSettingsHelper.GetPremiumStatus_Response callback) {
        if (mAIChatAndroidHelper == null) {
            callback.call(PremiumStatus.INACTIVE, null);
            return;
        }
        mAIChatAndroidHelper.getPremiumStatus(callback);
    }

    public void createOrderId(AiChatSettingsHelper.CreateOrderId_Response callback) {
        if (mAIChatAndroidHelper == null) {
            callback.call("");
            return;
        }
        mAIChatAndroidHelper.createOrderId(callback);
    }

    public void fetchOrderCredentials(
            String orderId, AiChatSettingsHelper.FetchOrderCredentials_Response callback) {
        if (mAIChatAndroidHelper == null) {
            callback.call("{}");
            return;
        }
        mAIChatAndroidHelper.fetchOrderCredentials(orderId, callback);
    }

    public void refreshOrder(String orderId, AiChatSettingsHelper.RefreshOrder_Response callback) {
        if (mAIChatAndroidHelper == null) {
            callback.call("{}");
            return;
        }
        mAIChatAndroidHelper.refreshOrder(orderId, callback);
    }

    public void getModels(AiChatSettingsHelper.GetModelsWithSubtitles_Response callback) {
        if (mAIChatAndroidHelper == null) {
            callback.call(new ModelWithSubtitle[0]);
            return;
        }
        mAIChatAndroidHelper.getModelsWithSubtitles(callback);
    }

    public void getDefaultModelKey(AiChatSettingsHelper.GetDefaultModelKey_Response callback) {
        if (mAIChatAndroidHelper == null) {
            callback.call("");
            return;
        }
        mAIChatAndroidHelper.getDefaultModelKey(callback);
    }

    public void setDefaultModelKey(String modelKey) {
        if (mAIChatAndroidHelper == null) {
            return;
        }

        mAIChatAndroidHelper.setDefaultModelKey(modelKey);
    }

    public void getCustomModels(AiChatSettingsHelper.GetCustomModels_Response callback) {
        if (mAIChatAndroidHelper == null) {
            callback.call(new Model[0]);
            return;
        }
        mAIChatAndroidHelper.getCustomModels(callback);
    }

    public void addCustomModel(Model model, AiChatSettingsHelper.AddCustomModel_Response callback) {
        if (mAIChatAndroidHelper == null) {
            callback.call(OperationResult.SUCCESS);
            return;
        }
        mAIChatAndroidHelper.addCustomModel(model, callback);
    }

    public void saveCustomModel(
            int index, Model model, AiChatSettingsHelper.SaveCustomModel_Response callback) {
        if (mAIChatAndroidHelper == null) {
            callback.call(OperationResult.SUCCESS);
            return;
        }
        mAIChatAndroidHelper.saveCustomModel(index, model, callback);
    }

    public void deleteCustomModel(int index) {
        if (mAIChatAndroidHelper == null) {
            return;
        }
        mAIChatAndroidHelper.deleteCustomModel(index);
    }

    public void applyCustomProviderSettings() {
        if (!BraveLeoPrefUtils.isCustomProviderEnabled()) {
            // Revert default to chat-automatic if custom provider disabled
            getDefaultModelKey(
                    currentKey -> {
                        if (currentKey != null && currentKey.startsWith("custom:")) {
                            setDefaultModelKey("chat-automatic");
                        }
                    });
            return;
        }

        String endpointUrl = BraveLeoPrefUtils.getCustomProviderEndpoint();
        String apiKey = BraveLeoPrefUtils.getCustomProviderApiKey();
        String modelName = BraveLeoPrefUtils.getCustomProviderModelName();
        int contextSize = BraveLeoPrefUtils.getCustomProviderContextSize();
        String systemPrompt = BraveLeoPrefUtils.getCustomProviderSystemPrompt();

        Model customModel = new Model();
        customModel.key = "";
        customModel.displayName = modelName;
        customModel.options = new ModelOptions();
        CustomModelOptions customOpts = new CustomModelOptions();
        customOpts.modelRequestName = modelName;
        customOpts.endpoint = new Url();
        customOpts.endpoint.url = endpointUrl;
        customOpts.apiKey = apiKey;
        customOpts.contextSize = contextSize;
        customOpts.maxAssociatedContentLength = contextSize * 4;
        customOpts.longConversationWarningCharacterLimit = contextSize * 3;
        customOpts.modelSystemPrompt = systemPrompt;
        customModel.options.setCustomModelOptions(customOpts);
        customModel.visionSupport = true;
        customModel.supportsTools = true;
        customModel.supportedCapabilities = new int[0];

        getCustomModels(
                existingModels -> {
                    if (existingModels != null && existingModels.length > 0) {
                        customModel.key = existingModels[0].key;
                        saveCustomModel(
                                0,
                                customModel,
                                result -> {
                                    setDefaultModelKey(customModel.key);
                                });
                    } else {
                        addCustomModel(
                                customModel,
                                result -> {
                                    getCustomModels(
                                            newModels -> {
                                                if (newModels != null && newModels.length > 0) {
                                                    setDefaultModelKey(newModels[0].key);
                                                }
                                            });
                                });
                    }
                });
    }

    public void deleteConversations(@TimePeriod int timePeriod) {
        if (mAIChatAndroidHelper == null) {
            return;
        }

        mAIChatAndroidHelper.deleteConversations(timePeriod);
    }

    @Override
    public void onConnectionError(MojoException e) {
        destroy();
    }

    @NativeMethods
    public interface Natives {
        long init(BrowserContextHandle browserContextHandle);

        void destroy(long nativeAIChatSettingsHelper);

        long getInterfaceToAndroidHelper(long nativeAIChatSettingsHelper);
    }
}
