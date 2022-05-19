package com.zxx.camera2videodemo;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;

import java.nio.ByteBuffer;

public class ConvertUtils {
    private ConvertUtils() {
    }

    public static Bitmap drawable2Bitmap(Resources res, int drawable) {
        return BitmapFactory.decodeResource(res, drawable);
    }

    public static Bitmap jpeg2Bitmap(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
    }

//    public static Bitmap bitmap2Gray(Bitmap bitmap) {
//        Mat srcMat = new Mat();
//        Mat dstMat = new Mat();
//        Utils.bitmapToMat(bitmap, srcMat);
//        Imgproc.cvtColor(srcMat, dstMat, Imgproc.COLOR_BGR2GRAY);
//        Utils.matToBitmap(dstMat, bitmap);
//        return bitmap;
//    }
}
