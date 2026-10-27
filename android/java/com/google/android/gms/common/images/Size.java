/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package com.google.android.gms.common.images;

/**
 * Offline drop-in replacement for GMS Size to remove Google Play Services dependency.
 */
public class Size {
    private final int mWidth;
    private final int mHeight;

    public Size(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Size size = (Size) o;
        return mWidth == size.mWidth && mHeight == size.mHeight;
    }

    @Override
    public int hashCode() {
        return mHeight ^ ((mWidth << 5) | (mWidth >>> 27));
    }
}
