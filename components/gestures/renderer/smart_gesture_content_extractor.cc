// Copyright (c) 2026 The Brave Authors. All rights reserved.
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this file,
// You can obtain one at https://mozilla.org/MPL/2.0/.

#include "brave/components/gestures/renderer/smart_gesture_content_extractor.h"

#include <utility>

#include "base/functional/bind.h"
#include "brave/components/gestures/renderer/smart_gesture_content_extractor_util.h"
#include "content/public/renderer/render_frame.h"
#include "services/service_manager/public/cpp/binder_registry.h"
#include "third_party/blink/public/web/web_frame_widget.h"
#include "third_party/blink/public/web/web_hit_test_result.h"
#include "third_party/blink/public/web/web_local_frame.h"

namespace gestures {

SmartGestureContentExtractor::SmartGestureContentExtractor(
    content::RenderFrame* render_frame,
    service_manager::BinderRegistry* registry)
    : content::RenderFrameObserver(render_frame) {
  // Being a `RenderFrameObserver`, this object is scoped to `render_frame`;
  // `registry` is too, so `base::Unretained` is safe here.
  registry->AddInterface(base::BindRepeating(
      &SmartGestureContentExtractor::BindReceiver, base::Unretained(this)));
}

SmartGestureContentExtractor::~SmartGestureContentExtractor() = default;

void SmartGestureContentExtractor::GetContentAtPoint(
    const gfx::PointF& point,
    mojom::SmartGestureContentExtractor::GetContentAtPointCallback callback) {
  blink::WebLocalFrame* const web_frame =
      render_frame() ? render_frame()->GetWebFrame() : nullptr;
  if (!web_frame || !web_frame->FrameWidget()) {
    std::move(callback).Run(nullptr);
    return;
  }

  const blink::WebHitTestResult hit_test_result =
      web_frame->FrameWidget()->HitTestResultAt(point);
  std::move(callback).Run(
      ExtractSmartGestureContent(hit_test_result, render_frame()));
}

void SmartGestureContentExtractor::OnDestruct() {
  delete this;
}

void SmartGestureContentExtractor::BindReceiver(
    mojo::PendingReceiver<mojom::SmartGestureContentExtractor> receiver) {
  receivers_.Add(this, std::move(receiver));
}

}  // namespace gestures
