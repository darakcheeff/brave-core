/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package com.google.android.gms.vision;

public class Tracker<T> {
    public void onNewItem(int id, T item) {}
    public void onUpdate(Detector.Detections<T> detections, T item) {}
    public void onMissing(Detector.Detections<T> detections) {}
    public void onDone() {}
}
