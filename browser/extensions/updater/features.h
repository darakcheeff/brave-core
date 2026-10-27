// Copyright (c) 2026 The Brave Authors. All rights reserved.
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this file,
// You can obtain one at https://mozilla.org/MPL/2.0/.

#ifndef BRAVE_BROWSER_EXTENSIONS_UPDATER_FEATURES_H_
#define BRAVE_BROWSER_EXTENSIONS_UPDATER_FEATURES_H_

#include <string>

#include "base/feature_list.h"

class PrefService;
namespace user_prefs {
class PrefRegistrySyncable;
}  // namespace user_prefs

namespace extensions::features {

BASE_DECLARE_FEATURE(kBraveAutoUpdateExtensions);

inline constexpr char kBraveExtensionAutoUpdateEnabled[] =
    "brave.extensions.auto_update_enabled";
inline constexpr char kBravePinnedExtensionsList[] =
    "brave.extensions.pinned_list";

bool IsAutoUpdateEnabled(const PrefService* prefs);
bool IsExtensionPinned(const PrefService* prefs, const std::string& extension_id);
void SetExtensionPinned(PrefService* prefs,
                        const std::string& extension_id,
                        bool pinned);
void RegisterProfilePrefs(user_prefs::PrefRegistrySyncable* registry);

}  // namespace extensions::features

#endif  // BRAVE_BROWSER_EXTENSIONS_UPDATER_FEATURES_H_
