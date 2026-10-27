/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package com.google.android.gms.vision;

import java.nio.ByteBuffer;

public class Frame {
    public static class Metadata {
        public static final int ROTATION_0 = 0;
        public static final int ROTATION_90 = 1;
        public static final int ROTATION_180 = 2;
        public static final int ROTATION_270 = 3;

        private int mWidth;
        private int mHeight;
        private int mRotation;

        public int getWidth() { return mWidth; }
        public int getHeight() { return mHeight; }
        public int getRotation() { return mRotation; }
    }

    private ByteBuffer mData;
    private Metadata mMetadata;

    public ByteBuffer getGrayscaleImageData() { return mData; }
    public Metadata getMetadata() { return mMetadata; }

    public static class Builder {
        private final Frame mFrame = new Frame();

        public Builder setImageData(ByteBuffer data, int width, int height, int format) {
            mFrame.mData = data;
            if (mFrame.mMetadata == null) {
                mFrame.mMetadata = new Metadata();
            }
            mFrame.mMetadata.mWidth = width;
            mFrame.mMetadata.mHeight = height;
            return this;
        }

        public Builder setId(int id) {
            return this;
        }

        public Builder setRotation(int rotation) {
            if (mFrame.mMetadata == null) {
                mFrame.mMetadata = new Metadata();
            }
            mFrame.mMetadata.mRotation = rotation;
            return this;
        }

        public Frame build() {
            return mFrame;
        }
    }
}
