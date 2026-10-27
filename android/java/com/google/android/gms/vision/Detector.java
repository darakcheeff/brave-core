/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package com.google.android.gms.vision;

public abstract class Detector<T> {
    public interface Processor<T> {
        void receiveDetections(Detections<T> detections);
        void release();
    }

    public static class Detections<T> {
        public Detections() {}
    }

    private Processor<T> mProcessor;

    public void setProcessor(Processor<T> processor) {
        mProcessor = processor;
    }

    public Processor<T> getProcessor() {
        return mProcessor;
    }

    public void release() {
        if (mProcessor != null) {
            mProcessor.release();
        }
    }

    public abstract void receiveFrame(Frame frame);

    public boolean isOperational() {
        return true;
    }
}
