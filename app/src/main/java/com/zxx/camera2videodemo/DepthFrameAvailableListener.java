package com.zxx.camera2videodemo;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

import java.nio.ShortBuffer;

public class DepthFrameAvailableListener implements ImageReader.OnImageAvailableListener {
    private static final String TAG = DepthFrameAvailableListener.class.getSimpleName();

    public static int WIDTH = 240;
    public static int HEIGHT = 180;

    private static float RANGE_MIN = 200.0f;
    private static float RANGE_MAX = 1600.0f;
    private static float CONFIDENCE_FILTER = 0.1f;

    private DepthFrameVisualizer depthFrameVisualizer;
    private int[] rawMask;

    public DepthFrameAvailableListener(DepthFrameVisualizer depthFrameVisualizer) {
        this.depthFrameVisualizer = depthFrameVisualizer;

        int size = WIDTH * HEIGHT;
        rawMask = new int[size];
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        try {
//            Log.d(TAG, "onImageAvailable: ");
            Image image = reader.acquireLatestImage();
            if (image != null) {
//                processImage(image);
                publishRawData(image);
//                Log.d(TAG, "onImageAvailable: close image");
                image.close();
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to acquireNextImage: " + e.getMessage());
        }
    }

    private void publishRawData(Image image) {
//        Log.d(TAG, "publishRawData: ");
        if (depthFrameVisualizer != null) {
            Bitmap bitmap = ConvertUtils.jpeg2Bitmap(image);
//            Bitmap depthBitmap = ConvertUtils.bitmap2Gray(bitmap);
            depthFrameVisualizer.onRawDataAvailable(bitmap);
            bitmap.recycle();
        }
    }
}