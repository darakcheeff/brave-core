/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

#ifndef BRAVE_COMPONENTS_BRAVE_SHIELDS_CONTENT_BROWSER_SHIELDS_SYNC_BRIDGE_H_
#define BRAVE_COMPONENTS_BRAVE_SHIELDS_CONTENT_BROWSER_SHIELDS_SYNC_BRIDGE_H_

#include <string>
#include <string_view>

#include "base/memory/raw_ptr.h"
#include "base/memory/weak_ptr.h"
#include "components/prefs/pref_change_registrar.h"

class PrefService;

namespace user_prefs {
class PrefRegistrySyncable;
}

namespace brave_shields {

// Sync bridge that synchronizes custom Brave Shields rules across devices
// over the Brave Sync profile sync channel, while keeping them unified in
// local_state across local profiles on the device.
class ShieldsSyncBridge {
 public:
  ShieldsSyncBridge(PrefService* profile_prefs, PrefService* local_state);
  ~ShieldsSyncBridge();

  ShieldsSyncBridge(const ShieldsSyncBridge&) = delete;
  ShieldsSyncBridge& operator=(const ShieldsSyncBridge&) = delete;

  static void RegisterProfilePrefs(user_prefs::PrefRegistrySyncable* registry);

  // Deterministically merges remote rules into local rules using an ordered union.
  static std::string MergeRules(std::string_view local_rules,
                                std::string_view remote_rules);

  void OnRemotePayloadChanged();
  void OnLocalFiltersChanged();

 private:
  const raw_ptr<PrefService> profile_prefs_;
  const raw_ptr<PrefService> local_state_;

  PrefChangeRegistrar profile_pref_change_registrar_;
  PrefChangeRegistrar local_pref_change_registrar_;

  bool is_syncing_ = false;

  base::WeakPtrFactory<ShieldsSyncBridge> weak_factory_{this};
};

}  // namespace brave_shields

#endif  // BRAVE_COMPONENTS_BRAVE_SHIELDS_CONTENT_BROWSER_SHIELDS_SYNC_BRIDGE_H_
