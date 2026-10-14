// Copyright (c) 2026 The Brave Authors. All rights reserved.
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this file,
// You can obtain one at https://mozilla.org/MPL/2.0/.

#ifndef BRAVE_COMPONENTS_GESTURES_CORE_BROWSER_URL_NAVIGATION_UTIL_H_
#define BRAVE_COMPONENTS_GESTURES_CORE_BROWSER_URL_NAVIGATION_UTIL_H_

class GURL;

namespace gestures {

// `url` is untrusted (synced user text or page content), so restricted to
// http/https, Brave's own trusted `brave:` pages, and the always-inert
// `about:blank`; every other scheme is rejected.
bool IsGestureAllowedToNavigateToUrl(const GURL& url);

}  // namespace gestures

#endif  // BRAVE_COMPONENTS_GESTURES_CORE_BROWSER_URL_NAVIGATION_UTIL_H_
