// Copyright (c) 2026 The Brave Authors. All rights reserved.
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this file,
// You can obtain one at https://mozilla.org/MPL/2.0/.

#include "brave/components/gestures/core/common/smart/smart_gesture_match_util.h"

#include "brave/components/gestures/core/common/actions/gesture_command_constants.h"
#include "testing/gmock/include/gmock/gmock.h"
#include "testing/gtest/include/gtest/gtest.h"

namespace gestures {

TEST(SmartGestureMatchUtilTest,
     ShouldReturnActionsWhenContentAndDirectionMatchADefault) {
  SmartGestureMapping smart_gesture_mapping;
  const SmartGestureContentInfo smart_gesture_content{
      .type = SmartGestureContentType::kLink};

  EXPECT_THAT(MaybeMatchSmartGesture(smart_gesture_mapping,
                                     smart_gesture_content, {{0, 0}, {50, 0}}),
              testing::Optional(testing::ElementsAre(testing::Field(
                  &GestureActionInfo::command, kCopyLinkAddress))));
}

TEST(SmartGestureMatchUtilTest, ShouldReturnNulloptWhenNoContent) {
  EXPECT_FALSE(MaybeMatchSmartGesture(SmartGestureMapping(), std::nullopt,
                                      {{0, 0}, {50, 0}}));
}

TEST(SmartGestureMatchUtilTest, ShouldReturnNulloptWhenPathExceedsMaxDistance) {
  const SmartGestureContentInfo smart_gesture_content{
      .type = SmartGestureContentType::kLink};

  EXPECT_FALSE(MaybeMatchSmartGesture(
      SmartGestureMapping(), smart_gesture_content, {{0, 0}, {200, 0}}));
}

TEST(SmartGestureMatchUtilTest,
     ShouldReturnNulloptWhenSlotHasNoAssignedActions) {
  const SmartGestureContentInfo smart_gesture_content{
      .type = SmartGestureContentType::kSelectedText};

  // `selected_text:left` has no default (see `DefaultSmartGestureMappings`).
  EXPECT_FALSE(MaybeMatchSmartGesture(
      SmartGestureMapping(), smart_gesture_content, {{100, 0}, {0, 10}}));
}

}  // namespace gestures
