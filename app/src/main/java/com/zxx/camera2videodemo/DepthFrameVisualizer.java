package com.zxx.camera2videodemo;

import android.graphics.Bitmap;

public interface DepthFrameVisualizer {
    void onRawDataAvailable(Bitmap bitmap);
}
