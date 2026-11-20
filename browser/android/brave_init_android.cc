// Copyright (c) 2023 The Brave Authors. All rights reserved.
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this file,
// You can obtain one at https://mozilla.org/MPL/2.0/.

#include <string>

#include "base/android/jni_android.h"
#include "base/android/jni_string.h"
#include "base/values.h"
#include "brave/browser/android/safe_browsing/buildflags.h"
#include "brave/browser/brave_browser_process.h"
#include "brave/browser/brave_stats/buildflags.h"
#include "chrome/android/chrome_jni_headers/BraveActivity_jni.h"
#include "chrome/browser/profiles/profile.h"
#include "chrome/browser/profiles/profile_manager.h"
#include "components/prefs/pref_service.h"
#include "components/proxy_config/proxy_config_pref_names.h"

#if BUILDFLAG(ENABLE_BRAVE_STATS_UPDATER)
#include "brave/browser/brave_stats/brave_stats_updater.h"
#endif

namespace chrome {
namespace android {

static void JNI_BraveActivity_RestartStatsUpdater(JNIEnv* env) {
#if BUILDFLAG(ENABLE_BRAVE_STATS_UPDATER)
  g_brave_browser_process->brave_stats_updater()->Stop();
  g_brave_browser_process->brave_stats_updater()->Start();
#endif
}

static base::android::ScopedJavaLocalRef<jstring>
JNI_BraveActivity_GetSafeBrowsingApiKey(JNIEnv* env) {
  return base::android::ConvertUTF8ToJavaString(
      env, BUILDFLAG(SAFEBROWSING_API_KEY));
}

static void JNI_BraveActivity_SetTorProxy(
    JNIEnv* env,
    const base::android::JavaRef<jstring>& j_proxy_uri) {
  Profile* profile = ProfileManager::GetLastUsedProfile();
  if (!profile) {
    return;
  }
  PrefService* prefs = profile->GetPrefs();
  if (!prefs) {
    return;
  }
  std::string proxy_uri =
      base::android::ConvertJavaStringToUTF8(env, j_proxy_uri);
  if (proxy_uri.empty()) {
    prefs->ClearPref(proxy_config::prefs::kProxy);
  } else {
    base::DictValue dict;
    dict.Set("mode", "fixed_servers");
    dict.Set("server", proxy_uri);
    prefs->SetDict(proxy_config::prefs::kProxy, std::move(dict));
  }
}

}  // namespace android
}  // namespace chrome

DEFINE_JNI(BraveActivity)
