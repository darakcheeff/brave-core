/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package com.google.android.gms.vision;

public class MultiProcessor<T> implements Detector.Processor<T> {
    public interface Factory<T> {
        Tracker<T> create(T item);
    }

    public static class Builder<T> {
        private final Factory<T> mFactory;

        public Builder(Factory<T> factory) {
            mFactory = factory;
        }

        public MultiProcessor<T> build() {
            return new MultiProcessor<>(mFactory);
        }
    }

    private final Factory<T> mFactory;
    private Tracker<T> mTracker;

    public MultiProcessor(Factory<T> factory) {
        mFactory = factory;
    }

    @Override
    public void receiveDetections(Detector.Detections<T> detections) {}

    @Override
    public void release() {}

    public Tracker<T> getTracker(T item) {
        if (mTracker == null) {
            mTracker = mFactory.create(item);
        }
        return mTracker;
    }
}
