// Copyright (c) 2026 The Brave Authors. All rights reserved.
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this file,
// You can obtain one at https://mozilla.org/MPL/2.0/.

#include "brave/browser/extensions/updater/features.h"

#include "base/values.h"
#include "components/pref_registry/pref_registry_syncable.h"
#include "components/prefs/pref_service.h"
#include "components/prefs/scoped_user_pref_update.h"

namespace extensions::features {

// When enabled (default), user installed extensions are automatically updated
// via component updater on a regular frequency.
//
// Can be disabled to prevent automatic extension updates. Folks can still
// update them manually from the brave://extensions page.
BASE_FEATURE(kBraveAutoUpdateExtensions, base::FEATURE_ENABLED_BY_DEFAULT);

bool IsAutoUpdateEnabled(const PrefService* prefs) {
  if (!base::FeatureList::IsEnabled(kBraveAutoUpdateExtensions)) {
    return false;
  }
  if (prefs && !prefs->GetBoolean(kBraveExtensionAutoUpdateEnabled)) {
    return false;
  }
  return true;
}

bool IsExtensionPinned(const PrefService* prefs,
                       const std::string& extension_id) {
  if (!prefs) {
    return false;
  }
  const base::Value::List& list = prefs->GetList(kBravePinnedExtensionsList);
  for (const auto& item : list) {
    if (item.is_string() && item.GetString() == extension_id) {
      return true;
    }
  }
  return false;
}

void SetExtensionPinned(PrefService* prefs,
                        const std::string& extension_id,
                        bool pinned) {
  if (!prefs) {
    return;
  }
  ScopedListPrefUpdate update(prefs, kBravePinnedExtensionsList);
  base::Value::List& list = update.Get();
  if (pinned) {
    for (const auto& item : list) {
      if (item.is_string() && item.GetString() == extension_id) {
        return;
      }
    }
    list.Append(extension_id);
  } else {
    list.EraseIf([&extension_id](const base::Value& item) {
      return item.is_string() && item.GetString() == extension_id;
    });
  }
}

void RegisterProfilePrefs(user_prefs::PrefRegistrySyncable* registry) {
  registry->RegisterBooleanPref(kBraveExtensionAutoUpdateEnabled, true);
  registry->RegisterListPref(kBravePinnedExtensionsList);
}

}  // namespace extensions::features
