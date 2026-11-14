/* Copyright (c) 2024 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.vpn;

import org.jni_zero.CalledByNative;
import org.jni_zero.JNINamespace;

import org.chromium.brave_vpn.mojom.ServiceHandler;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.mojo.bindings.ConnectionErrorHandler;
import org.chromium.mojo.bindings.Interface;
import org.chromium.mojo.bindings.Interface.Proxy.Handler;
import org.chromium.mojo.system.MessagePipeHandle;
import org.chromium.mojo.system.impl.CoreImpl;

@JNINamespace("chrome::android")
public class BraveVpnServiceFactoryAndroid {
    private static final Object sLock = new Object();
    private static BraveVpnServiceFactoryAndroid sInstance;

    public static BraveVpnServiceFactoryAndroid getInstance() {
        synchronized (sLock) {
            if (sInstance == null) {
                sInstance = new BraveVpnServiceFactoryAndroid();
            }
        }
        return sInstance;
    }

    private BraveVpnServiceFactoryAndroid() {}

    public ServiceHandler getVpnService(
            Profile profile, ConnectionErrorHandler connectionErrorHandler) {
        if (profile == null) {
            return null;
        }
        long nativeHandle =
                BraveVpnServiceFactoryAndroidJni.get().getInterfaceToVpnService(profile);
        if (nativeHandle == -1) {
            return null;
        }
        MessagePipeHandle handle = wrapNativeHandle(nativeHandle);
        ServiceHandler serviceHandler = ServiceHandler.MANAGER.attachProxy(handle, 0);
        Handler handler = ((Interface.Proxy) serviceHandler).getProxyHandler();
        handler.setErrorHandler(connectionErrorHandler);

        return serviceHandler;
    }

    private MessagePipeHandle wrapNativeHandle(long nativeHandle) {
        return CoreImpl.getInstance().acquireNativeHandle(nativeHandle).toMessagePipeHandle();
    }

    @CalledByNative
    private static void jniStub() {}

    interface Natives {
        long getInterfaceToVpnService(Profile profile);
    }

    static final class BraveVpnServiceFactoryAndroidJni {
        private static final Natives INSTANCE = profile -> -1L;

        static Natives get() {
            return INSTANCE;
        }
    }
}
