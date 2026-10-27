/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package com.google.android.gms.vision.barcode;

import android.content.Context;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.nio.ByteBuffer;

/**
 * Offline, standalone QR code detector backed by ZXing.
 * Zero dependency on Google Play Services (works on EMUI, AOSP, de-Googled ROMs).
 */
public class BarcodeDetector extends Detector<Barcode> {
    private final MultiFormatReader mReader = new MultiFormatReader();

    public static class Builder {
        public Builder(Context context) {}

        public Builder setBarcodeFormats(int formats) {
            return this;
        }

        public BarcodeDetector build() {
            return new BarcodeDetector();
        }
    }

    @Override
    public boolean isOperational() {
        return true;
    }

    @Override
    public void receiveFrame(Frame frame) {
        if (frame == null || frame.getGrayscaleImageData() == null || frame.getMetadata() == null) {
            return;
        }
        ByteBuffer buffer = frame.getGrayscaleImageData();
        byte[] data = buffer.array();
        int width = frame.getMetadata().getWidth();
        int height = frame.getMetadata().getHeight();

        try {
            PlanarYUVLuminanceSource source =
                    new PlanarYUVLuminanceSource(
                            data, width, height, 0, 0, width, height, false);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = mReader.decodeWithState(bitmap);
            if (result != null && result.getText() != null) {
                Barcode barcode = new Barcode(result.getText());
                if (getProcessor() instanceof MultiProcessor) {
                    @SuppressWarnings("unchecked")
                    MultiProcessor<Barcode> processor = (MultiProcessor<Barcode>) getProcessor();
                    Tracker<Barcode> tracker = processor.getTracker(barcode);
                    if (tracker != null) {
                        tracker.onNewItem(1, barcode);
                    }
                }
            }
        } catch (Exception e) {
            // Normal: frame does not contain a recognizable QR code
        } finally {
            mReader.reset();
        }
    }
}
