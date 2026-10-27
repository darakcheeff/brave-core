/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package com.google.android.gms.vision.barcode;

/**
 * Lightweight offline drop-in replacement for GMS Barcode to decouple from Google Play Services.
 */
public class Barcode {
    public static final int ALL_FORMATS = 0;
    public static final int QR_CODE = 256;

    public String displayValue;
    public String rawValue;
    public int format;

    public Barcode() {
        this.format = QR_CODE;
    }

    public Barcode(String value) {
        this.displayValue = value;
        this.rawValue = value;
        this.format = QR_CODE;
    }
}
