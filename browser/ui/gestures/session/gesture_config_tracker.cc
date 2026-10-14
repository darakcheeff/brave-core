// Copyright (c) 2026 The Brave Authors. All rights reserved.
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this file,
// You can obtain one at https://mozilla.org/MPL/2.0/.

#include "brave/browser/ui/gestures/session/gesture_config_tracker.h"

#include <algorithm>
#include <optional>
#include <string>
#include <utility>

#include "base/functional/bind.h"
#include "base/strings/string_util.h"
#include "base/values.h"
#include "brave/browser/ui/gestures/session/default_directional_gesture_commands_util.h"
#include "brave/components/gestures/core/browser/directional/directional_gesture_mapping_pref_loader.h"
#include "brave/components/gestures/core/browser/pref_names.h"
#include "brave/components/gestures/core/browser/smart/smart_gesture_mapping_pref_loader.h"
#include "brave/components/gestures/core/common/custom/custom_gesture_info.h"
#include "brave/components/gestures/core/common/custom/custom_gesture_parser.h"
#include "components/prefs/pref_service.h"

namespace gestures {

GestureConfigTracker::GestureConfigTracker(PrefService& prefs)
    : prefs_(prefs),
      directional_gesture_mapping_(DefaultDirectionalGestureCommands()) {
  LoadDirectionalGestures();
  LoadCustomGestures();
  LoadSmartGestures();

  pref_change_registrar_.Init(&*prefs_);
  pref_change_registrar_.Add(
      prefs::kDirectionalGestures,
      base::BindRepeating(&GestureConfigTracker::LoadDirectionalGestures,
                          weak_factory_.GetWeakPtr()));
  pref_change_registrar_.Add(
      prefs::kCustomGestures,
      base::BindRepeating(&GestureConfigTracker::LoadCustomGestures,
                          weak_factory_.GetWeakPtr()));
  pref_change_registrar_.Add(
      prefs::kSmartGestures,
      base::BindRepeating(&GestureConfigTracker::LoadSmartGestures,
                          weak_factory_.GetWeakPtr()));
}

GestureConfigTracker::~GestureConfigTracker() = default;

bool GestureConfigTracker::IsAllowedForHost(std::string_view scheme,
                                            std::string_view host) const {
  if (!prefs_->GetBoolean(prefs::kEnabled)) {
    return false;
  }

  // The synced pref value isn't guaranteed to be lowercased already, so
  // lowercase it here too, or a mixed-case entry could silently fail open.
  return std::ranges::none_of(
      prefs_->GetDict(prefs::kDisallowedSites),
      [&scheme, &host](const auto& entry) {
        const auto& [disallowed_key, value] = entry;
        if (!value.GetIfBool().value_or(false)) {
          return false;
        }
        const std::string lower_key = base::ToLowerASCII(disallowed_key);
        const size_t scheme_separator = lower_key.find("://");
        if (scheme_separator != std::string::npos) {
          return scheme ==
                     std::string_view(lower_key).substr(0, scheme_separator) &&
                 host ==
                     std::string_view(lower_key).substr(scheme_separator + 3);
        }
        return host == lower_key ||
               (host.size() > lower_key.size() && host.ends_with(lower_key) &&
                host[host.size() - lower_key.size() - 1] == '.');
      });
}

void GestureConfigTracker::LoadDirectionalGestures() {
  LoadDirectionalGestureMapping(*prefs_, directional_gesture_mapping_);
}

void GestureConfigTracker::LoadCustomGestures() {
  custom_gesture_recognizer_.Clear();

  for (const base::Value& custom_gesture_value :
       prefs_->GetList(prefs::kCustomGestures)) {
    const base::DictValue* custom_gesture_dict =
        custom_gesture_value.GetIfDict();
    if (!custom_gesture_dict) {
      continue;
    }
    if (std::optional<CustomGestureInfo> custom_gesture =
            MaybeParseCustomGesture(*custom_gesture_dict)) {
      custom_gesture_recognizer_.Add(std::move(*custom_gesture));
    }
  }
}

void GestureConfigTracker::LoadSmartGestures() {
  LoadSmartGestureMapping(*prefs_, smart_gesture_mapping_);
}

}  // namespace gestures
