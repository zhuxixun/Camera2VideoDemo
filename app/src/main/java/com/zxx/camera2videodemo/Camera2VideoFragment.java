package com.zxx.camera2videodemo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Camera2VideoFragment extends Fragment {

    private static final String TAG = "VideoFragment";

    private static final String FILE_DIR = "sdcard/testVideo/";

    private static final int WIDTH = 1024;

    private static final int HEIGHT = 768;

    private static final int VIDEO_RGB = 0;

    private static final int VIDEO_DEPTH = 1;

    private TextureView mBackTextureView;

    private TextureView mDepthTextureView;

    private Button mRgbRecordVideoBtn;

    private Button mDepthRecordVideoBtn;

    private Button mRecordVideoBtn;

    private HandlerThread mBackgroundThread;

    private Handler mBackgroundHandler;

    private String mBackCameraId;

    private Size mVideoSize;

    private Size mPreviewSize;

    private CameraDevice mCameraDevice;

    private CaptureRequest.Builder mPreviewCaptureBuilder;

    private CameraCaptureSession mPreviewCaptureSession;

    private boolean mIsRgbRecording = false;

    private ImageReader mImageReader;

    private MediaRecorder mRgbRecorder;

    private Matrix mDefaultBitmapTransform;

    private DepthFrameAvailableListener mImageAvailableListener;

    private CameraDevice.StateCallback mBackCameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "onOpened: ");
            mCameraDevice = camera;
            startPreview();
            if (null != mDepthTextureView) {
                configureTransform(mDepthTextureView.getWidth(), mDepthTextureView.getHeight());
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: ");
        return inflater.inflate(R.layout.fragment_video, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onViewCreated: ");
        mBackTextureView = view.findViewById(R.id.rgb_texture);
        mDepthTextureView = view.findViewById(R.id.depth_texture);
        mRgbRecordVideoBtn = view.findViewById(R.id.rgb_record_video_btn);
        mRgbRecordVideoBtn.setOnClickListener(v -> {
            if (mIsRgbRecording) {
                stopRgbRecordingVideo();
            } else {
                startRgbRecordingVideo();
            }
        });
        mDepthRecordVideoBtn = view.findViewById(R.id.depth_record_video_btn);
        mRecordVideoBtn = view.findViewById(R.id.record_video_btn);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume: ");
        super.onResume();
        startBackgroundThread();
        if (mDepthTextureView.isAvailable()) {
            openCamera(mDepthTextureView.getWidth(), mDepthTextureView.getHeight());
        } else {
            mDepthTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    Log.d(TAG, "onSurfaceTextureAvailable: width: " + width + ", height: " + height);
                    openCamera(width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                    Log.d(TAG, "onSurfaceTextureSizeChanged: width: " + width + ", height: " + height);
//                    configureTransform(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                }
            });
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mDepthTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mDepthTextureView.setTransform(matrix);
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("backgroundThread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause: ");
        stopBackgroundThread();
        closeCamera();
        super.onPause();
    }

    private void closeCamera() {
        Log.d(TAG, "closeCamera: ");
        closePreviewCaptureSession();
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    private void stopBackgroundThread() {
        Log.d(TAG, "stopBackgroundThread: ");
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            Log.e(TAG, "stopBackgroundThread: InterruptedException");
        }
    }

    private void openCamera(int width, int height) {
        Log.d(TAG, "openBackCamera: ");
        final Activity activity = getActivity();
        if (null == activity) {
            return;
        }
        CameraManager cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        mImageAvailableListener = new DepthFrameAvailableListener(new DepthFrameVisualizer() {
            @Override
            public void onRawDataAvailable(Bitmap bitmap) {
                Log.d(TAG, "onRawDataAvailable: bitmap width: " + bitmap.getWidth() + ", height: " + bitmap.getHeight());
                renderBitmapToTextureView(bitmap, mDepthTextureView);
            }
        });
        try {
            String cameraId = cameraManager.getCameraIdList()[1];
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                throw new RuntimeException("Cannot get available preview/video sizes");
            }
            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, mVideoSize);
            Log.d(TAG, "openCamera: video size width: " + mVideoSize.getWidth() + ", height: " + mVideoSize.getHeight());
            Log.d(TAG, "openCamera: preview size width: " + mPreviewSize.getWidth() + ", height: " + mPreviewSize.getHeight());
            mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.JPEG, 2);
            mImageReader.setOnImageAvailableListener(mImageAvailableListener, null);
            configureTransform(width, height);
            mRgbRecorder = new MediaRecorder();
            cameraManager.openCamera(cameraId, mBackCameraStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "openCamera: CameraAccessException");
        }
    }

    private static Size chooseVideoSize(Size[] choices) {
        Log.d(TAG, "chooseVideoSize: ");
        for (Size size : choices) {
            Log.d(TAG, "chooseVideoSize: choice width: " + size.getWidth() + ", height: " + size.getHeight());
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                Log.d(TAG, "chooseVideoSize: video size width: " + size.getWidth() + ", height: " + size.getHeight());
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            Log.d(TAG, "chooseOptimalSize: choice width: " + option.getWidth() + ", height: " + option.getHeight());
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width) {
                Log.d(TAG, "chooseOptimalSize: meet option width: " + option.getWidth() + ", height: " + option.getHeight());
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new Comparator<Size>() {
                @Override
                public int compare(Size o1, Size o2) {
                    return Long.signum((long) o1.getWidth() * o1.getHeight() -
                            (long) o2.getWidth() * o2.getHeight());
                }
            });
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private void renderBitmapToTextureView(Bitmap bitmap, TextureView textureView) {
//        Log.d(TAG, "renderBitmapToTextureView: ");
        Canvas canvas = textureView.lockCanvas();
        canvas.drawBitmap(bitmap, defaultBitmapTransform(textureView), null);
        textureView.unlockCanvasAndPost(canvas);
    }

    private Matrix defaultBitmapTransform(TextureView view) {
//        Log.d(TAG, "defaultBitmapTransform: ");
        if (mDefaultBitmapTransform == null || view.getWidth() == 0 || view.getHeight() == 0) {
            Matrix matrix = new Matrix();
            int centerX = view.getWidth() / 2;
            int centerY = view.getHeight() / 2;

            int bufferWidth = WIDTH;
            int bufferHeight = HEIGHT;

            RectF bufferRect = new RectF(0, 0, bufferWidth, bufferHeight);
            RectF viewRect = new RectF(0, 0, view.getWidth(), view.getHeight());
            matrix.setRectToRect(bufferRect, viewRect, Matrix.ScaleToFit.CENTER);
            matrix.postRotate(270, centerX, centerY);

            mDefaultBitmapTransform = matrix;
        }
        return mDefaultBitmapTransform;
    }


    private void startPreview() {
        Log.d(TAG, "startBackPreview: ");
        try {
            mPreviewCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewCaptureBuilder.addTarget(mImageReader.getSurface());
            mCameraDevice.createCaptureSession(Collections.singletonList(mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mPreviewCaptureSession = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "onConfigureFailed: startPreview");
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closePreviewCaptureSession() {
        Log.d(TAG, "closeBackCaptureSession: ");
        if (null != mPreviewCaptureSession) {
            mPreviewCaptureSession.close();
            mPreviewCaptureSession = null;
        }
    }

    private void updatePreview() {
        Log.d(TAG, "updateBackPreview: ");
        if (null == mCameraDevice) {
            return;
        }
        mPreviewCaptureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            mPreviewCaptureSession.setRepeatingRequest(mPreviewCaptureBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "updateBackPreview: CameraAccessException");
        }
    }

    private String getVideoFilePath(int videoType) {
        Log.d(TAG, "getVideoFilePath: ");
        File dir = new File(FILE_DIR);
        if (!dir.exists()) {
            dir.mkdir();
        }
        String filePath = null;
        switch (videoType) {
            case VIDEO_RGB:
                filePath = FILE_DIR + "rgb" + System.currentTimeMillis() + ".mp4";
                break;
            case VIDEO_DEPTH:
                filePath = FILE_DIR + "depth" + System.currentTimeMillis() + ".mp4";
                break;
        }
        return filePath;
    }

    private void setUpRgbMediaRecorder() throws IOException {
        Log.d(TAG, "setUpRgbMediaRecorder: ");
        final Activity activity = getActivity();
        if (null == activity) {
            return;
        }
        mRgbRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mRgbRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        String filePath = getVideoFilePath(VIDEO_RGB);
        mRgbRecorder.setOutputFile(filePath);
        mRgbRecorder.setVideoEncodingBitRate(10000000);
        mRgbRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mRgbRecorder.setVideoFrameRate(30);
        mRgbRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mRgbRecorder.prepare();
    }

    private void startRgbRecordingVideo() {
        Log.d(TAG, "startRgbRecordingVideo: ");
        Log.w(TAG, "startRgbRecordingVideo: mPreviewSize width: " + mPreviewSize.getWidth() + ", height: " + mPreviewSize.getHeight());
        Log.w(TAG, "startRgbRecordingVideo: mDepthTextureView size width: " + mDepthTextureView.getWidth() + ", height: " + mDepthTextureView.getHeight());
        Log.w(TAG, "startRgbRecordingVideo: mVideoSize size width: " + mVideoSize.getWidth() + ", height: " + mVideoSize.getHeight());
        try {
            closePreviewCaptureSession();
            setUpRgbMediaRecorder();
            SurfaceTexture previewSurfaceTexture = mDepthTextureView.getSurfaceTexture();
            previewSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            List<Surface> surfaces = new ArrayList<>();
//            Surface previewSurface = new Surface(previewSurfaceTexture);
//            surfaces.add(previewSurface);
//            mPreviewCaptureBuilder.addTarget(previewSurface);
//
            Surface readerSurface = mImageReader.getSurface();
            surfaces.add(readerSurface);
            mPreviewCaptureBuilder.addTarget(readerSurface);

            Surface rgbRecorderSurface = mRgbRecorder.getSurface();
            surfaces.add(rgbRecorderSurface);
            mPreviewCaptureBuilder.addTarget(rgbRecorderSurface);

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "onConfigured: ");
                    mPreviewCaptureSession = session;
                    updatePreview();
                    getActivity().runOnUiThread(() -> {
                        mRgbRecordVideoBtn.setText("RGB结束录制");
                        mIsRgbRecording = true;
                        mRgbRecorder.start();
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "onConfigureFailed: startRgbRecordingVideo");
                }
            }, mBackgroundHandler);
        } catch (IOException e) {
            Log.e(TAG, "startRgbRecordingVideo: IOException");
        } catch (CameraAccessException e) {
            Log.e(TAG, "startRgbRecordingVideo: CameraAccessException");
        }
    }

    private void stopRgbRecordingVideo() {
        Log.d(TAG, "stopRgbRecordingVideo: ");
        mIsRgbRecording = false;
        mRgbRecordVideoBtn.setText("RGB开始录制");
        mRgbRecorder.stop();
        mRgbRecorder.reset();
        startPreview();
    }
}
