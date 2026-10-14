// Copyright (c) 2026 The Brave Authors. All rights reserved.
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this file,
// You can obtain one at https://mozilla.org/MPL/2.0/.

#include "brave/components/gestures/core/common/smart/smart_gesture_match_util.h"

#include "brave/components/gestures/core/common/features.h"
#include "brave/components/gestures/core/common/gesture_path_distance_util.h"
#include "brave/components/gestures/core/common/smart/smart_gesture_direction_util.h"

namespace gestures {

std::optional<GestureActions> MaybeMatchSmartGesture(
    const SmartGestureMapping& smart_gesture_mapping,
    const std::optional<SmartGestureContentInfo>& smart_gesture_content,
    const GesturePath& gesture_path) {
  // Beyond this distance, it reads as a directional/custom shape, not a
  // quick smart-gesture swipe, so it falls through to the normal cascade.
  if (!smart_gesture_content ||
      GesturePathLength(gesture_path) >
          features::kSmartGestureMaxDistancePixels.Get()) {
    return std::nullopt;
  }
  const SmartGestureDirectionType smart_direction_type =
      ClassifySmartGestureDirection(gesture_path);
  GestureActions actions = smart_gesture_mapping.ActionsFor(
      smart_gesture_content->type, smart_direction_type);
  if (actions.empty()) {
    return std::nullopt;
  }
  return actions;
}

}  // namespace gestures
