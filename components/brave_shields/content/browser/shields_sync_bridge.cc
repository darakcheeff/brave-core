/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

#include "brave/components/brave_shields/content/browser/shields_sync_bridge.h"

#include <set>
#include <sstream>
#include <utility>
#include <vector>

#include "base/check.h"
#include "base/functional/bind.h"
#include "base/json/json_reader.h"
#include "base/json/json_writer.h"
#include "base/strings/string_split.h"
#include "base/strings/string_util.h"
#include "base/time/time.h"
#include "base/values.h"
#include "brave/components/brave_shields/core/common/pref_names.h"
#include "components/pref_registry/pref_registry_syncable.h"
#include "components/prefs/pref_service.h"

namespace brave_shields {

ShieldsSyncBridge::ShieldsSyncBridge(PrefService* profile_prefs,
                                     PrefService* local_state)
    : profile_prefs_(profile_prefs), local_state_(local_state) {
  if (!profile_prefs_ || !local_state_) {
    return;
  }

  profile_pref_change_registrar_.Init(profile_prefs_);
  profile_pref_change_registrar_.Add(
      prefs::kShieldsSyncPayload,
      base::BindRepeating(&ShieldsSyncBridge::OnRemotePayloadChanged,
                          weak_factory_.GetWeakPtr()));

  local_pref_change_registrar_.Init(local_state_);
  local_pref_change_registrar_.Add(
      prefs::kAdBlockCustomFilters,
      base::BindRepeating(&ShieldsSyncBridge::OnLocalFiltersChanged,
                          weak_factory_.GetWeakPtr()));

  // Perform initial reconciliation if remote payload already exists
  if (profile_prefs_->GetBoolean(prefs::kShieldsSyncEnabled)) {
    const std::string& remote_payload =
        profile_prefs_->GetString(prefs::kShieldsSyncPayload);
    if (!remote_payload.empty()) {
      OnRemotePayloadChanged();
    } else {
      OnLocalFiltersChanged();
    }
  }
}

ShieldsSyncBridge::~ShieldsSyncBridge() = default;

// static
void ShieldsSyncBridge::RegisterProfilePrefs(
    user_prefs::PrefRegistrySyncable* registry) {
  registry->RegisterStringPref(
      prefs::kShieldsSyncPayload,
      std::string(),
      user_prefs::PrefRegistrySyncable::SYNCABLE_PREF);
  registry->RegisterBooleanPref(
      prefs::kShieldsSyncEnabled,
      true,
      user_prefs::PrefRegistrySyncable::SYNCABLE_PREF);
}

// static
std::string ShieldsSyncBridge::MergeRules(std::string_view local_rules,
                                          std::string_view remote_rules) {
  std::vector<std::string> local_lines = base::SplitString(
      local_rules, "\n", base::KEEP_WHITESPACE, base::SPLIT_WANT_ALL);
  std::vector<std::string> remote_lines = base::SplitString(
      remote_rules, "\n", base::KEEP_WHITESPACE, base::SPLIT_WANT_ALL);

  std::set<std::string> seen_rules;
  std::vector<std::string> merged_lines;

  auto process_line = [&](const std::string& line) {
    std::string trimmed =
        std::string(base::TrimWhitespaceASCII(line, base::TRIM_ALL));
    if (trimmed.empty()) {
      return;
    }
    if (seen_rules.insert(trimmed).second) {
      merged_lines.push_back(trimmed);
    }
  };

  for (const auto& line : local_lines) {
    process_line(line);
  }

  for (const auto& line : remote_lines) {
    process_line(line);
  }

  return base::JoinString(merged_lines, "\n");
}

void ShieldsSyncBridge::OnLocalFiltersChanged() {
  if (is_syncing_ || !profile_prefs_ || !local_state_) {
    return;
  }

  if (!profile_prefs_->GetBoolean(prefs::kShieldsSyncEnabled)) {
    return;
  }

  std::string current_rules =
      local_state_->GetString(prefs::kAdBlockCustomFilters);

  base::Value::Dict root;
  root.Set("version", 1);
  root.Set("timestamp", static_cast<double>(
                            base::Time::Now().InMillisecondsSinceUnixEpoch()));

  base::Value::Dict custom_filters;
  custom_filters.Set("rules", current_rules);
  custom_filters.Set("updated_at",
                     static_cast<double>(
                         base::Time::Now().InMillisecondsSinceUnixEpoch()));
  root.Set("custom_filters", std::move(custom_filters));

  std::string json_str;
  if (!base::JSONWriter::Write(root, &json_str)) {
    return;
  }

  is_syncing_ = true;
  profile_prefs_->SetString(prefs::kShieldsSyncPayload, json_str);
  is_syncing_ = false;
}

void ShieldsSyncBridge::OnRemotePayloadChanged() {
  if (is_syncing_ || !profile_prefs_ || !local_state_) {
    return;
  }

  if (!profile_prefs_->GetBoolean(prefs::kShieldsSyncEnabled)) {
    return;
  }

  const std::string& remote_payload =
      profile_prefs_->GetString(prefs::kShieldsSyncPayload);
  if (remote_payload.empty()) {
    return;
  }

  std::optional<base::Value> parsed = base::JSONReader::Read(remote_payload);
  if (!parsed || !parsed->is_dict()) {
    return;
  }

  const base::Value::Dict& root = parsed->GetDict();
  const base::Value::Dict* custom_filters = root.FindDict("custom_filters");
  if (!custom_filters) {
    return;
  }

  const std::string* remote_rules = custom_filters->FindString("rules");
  if (!remote_rules) {
    return;
  }

  std::string local_rules =
      local_state_->GetString(prefs::kAdBlockCustomFilters);
  std::string merged = MergeRules(local_rules, *remote_rules);

  if (merged != local_rules) {
    is_syncing_ = true;
    local_state_->SetString(prefs::kAdBlockCustomFilters, merged);
    is_syncing_ = false;
  }
}

}  // namespace brave_shields
