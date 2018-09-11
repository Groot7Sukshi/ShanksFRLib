package com.sukshi.frlib.Tracker;


import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.support.annotation.StringDef;
import android.util.Log;
import android.util.SparseArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.gms.common.images.Size;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;

import java.io.IOException;
import java.lang.Thread.State;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;


@SuppressWarnings("deprecation")
public class CamSource {
    @SuppressLint("InlinedApi")
    public static final int CAMERA_FACING_BACK = CameraInfo.CAMERA_FACING_BACK;
    @SuppressLint("InlinedApi")
    public static final int CAMERA_FACING_FRONT = CameraInfo.CAMERA_FACING_FRONT;

    private static final String TAG = "CameraSource";
    private static final double ratioTolerance = 0.1;
    private static final double maxRatioTolerance = 0.15;
    public static int faceSize;

    @StringDef({
            Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE,
            Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO,
            Camera.Parameters.FOCUS_MODE_AUTO,
            Camera.Parameters.FOCUS_MODE_EDOF,
            Camera.Parameters.FOCUS_MODE_FIXED,
            Camera.Parameters.FOCUS_MODE_INFINITY,
            Camera.Parameters.FOCUS_MODE_MACRO
    })
    @Retention(RetentionPolicy.SOURCE)
    private @interface FocusMode {}

    @StringDef({
            Camera.Parameters.FLASH_MODE_ON,
            Camera.Parameters.FLASH_MODE_OFF,
            Camera.Parameters.FLASH_MODE_AUTO,
            Camera.Parameters.FLASH_MODE_RED_EYE,
            Camera.Parameters.FLASH_MODE_TORCH
    })
    @Retention(RetentionPolicy.SOURCE)
    private @interface FlashMode {}

    @IntDef({
            CamcorderProfile.QUALITY_LOW,
            CamcorderProfile.QUALITY_HIGH,
            CamcorderProfile.QUALITY_480P,
            CamcorderProfile.QUALITY_720P,
            CamcorderProfile.QUALITY_1080P,
            CamcorderProfile.QUALITY_2160P,
            CamcorderProfile.QUALITY_CIF,
            CamcorderProfile.QUALITY_QCIF,
            CamcorderProfile.QUALITY_QVGA
    })
    @Retention(RetentionPolicy.SOURCE)
    private @interface VideoMode {}

    private Context mContext;

    private final Object mCameraLock = new Object();

    // Guarded by mCameraLock
    private Camera mCamera;

    private int mFacing = CAMERA_FACING_BACK;

    /**
     * Rotation of the device, and thus the associated preview images captured from the device.
     * See {@link Frame.Metadata#getRotation()}.
     */
    private int mRotation;

    private Size mPreviewSize;
    private Size mPictureSize;

    // These values may be requested by the caller.  Due to hardware limitations, we may need to
    // select close, but not exactly the same values for these.
    private float mRequestedFps = 30.0f;

    private SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
    private MediaRecorder mediaRecorder;


    private String mFocusMode = Camera.Parameters.FOCUS_MODE_AUTO;
    private String mFlashMode = Camera.Parameters.FLASH_MODE_AUTO;

    private SurfaceHolder previewSurfaceHolder = null;

    /**
     * Dedicated thread and associated runnable for calling into the detector with frames, as the
     * frames become available from the camera.
     */
    private Thread mProcessingThread;
    private FrameProcessingRunnable mFrameProcessor;

    /**
     * Map to convert between a byte array, received from the camera, and its associated byte
     * buffer.  We use byte buffers internally because this is a more efficient way to call into
     * native code later (avoids a potential copy).
     */
    private Map<byte[], ByteBuffer> mBytesToByteBuffer = new HashMap<>();

    //==============================================================================================
    // Builder
    //==============================================================================================

    /**
     * Builder for configuring and creating an associated camera source.
     */
    public static class Builder {
        private boolean usesFaceDetector = false;
        private final Detector<?> mDetector;
        private CamSource mCameraSource = new CamSource();

        /**
         * Creates a camera source builder with the supplied context and detector.  Camera preview
         * images will be streamed to the associated detector upon starting the camera source.
         */
        public Builder(Context context, Detector<?> detector) {
            if (context == null) {
                throw new IllegalArgumentException("No context supplied.");
            }
            if (detector == null) {
                throw new IllegalArgumentException("No detector supplied.");
            }

            usesFaceDetector = true;
            mDetector = detector;
            mCameraSource.mContext = context;
        }

        /**
         * Sets the requested frame rate in frames per second.  If the exact requested value is not
         * not available, the best matching available value is selected.   Default: 30.
         */
        public Builder setRequestedFps(float fps) {
            if (fps <= 0) {
                throw new IllegalArgumentException("Invalid fps: " + fps);
            }
            mCameraSource.mRequestedFps = fps;
            return this;
        }

        public Builder setFocusMode(@FocusMode String mode) {
            mCameraSource.mFocusMode = mode;
            return this;
        }

        public Builder setFlashMode(@FlashMode String mode) {
            mCameraSource.mFlashMode = mode;
            return this;
        }

        /**
         * Sets the camera to use (either {@link #CAMERA_FACING_BACK} or
         * {@link #CAMERA_FACING_FRONT}). Default: back facing.
         */
        public Builder setFacing(int facing) {
            if ((facing != CAMERA_FACING_BACK) && (facing != CAMERA_FACING_FRONT)) {
                throw new IllegalArgumentException("Invalid camera: " + facing);
            }
            mCameraSource.mFacing = facing;
            return this;
        }

        /**
         * Creates an instance of the camera source.
         */
        public CamSource build() {
            if(usesFaceDetector)
                mCameraSource.mFrameProcessor = mCameraSource.new FrameProcessingRunnable(mDetector);
            return mCameraSource;
        }
    }

    //==============================================================================================
    // Bridge Functionality for the Camera1 API
    //==============================================================================================

    /**
     * Callback interface used to signal the moment of actual image capture.
     */
    public interface ShutterCallback {
        /**
         * Called as near as possible to the moment when a photo is captured from the sensor. This
         * is a good opportunity to play a shutter sound or give other feedback of camera operation.
         * This may be some time after the photo was triggered, but some time before the actual data
         * is available.
         */
        void onShutter();
    }

    /**
     * Callback interface used to supply image data from a photo capture.
     */
    public interface PictureCallback {
        /**
         * Called when image data is available after a picture is taken.  The format of the data
         * is a jpeg binary.
         */
        void onPictureTaken(byte[] data);
    }


    public interface AutoFocusCallback {
        /**
         * Called when the camera auto focus completes.  If the camera
         * does not support auto-focus and autoFocus is called,
         * onAutoFocus will be called immediately with a fake value of
         * <code>success</code> set to <code>true</code>.
         * <p/>
         * The auto-focus routine does not lock auto-exposure and auto-white
         * balance after it completes.
         *
         * @param success true if focus was successful, false if otherwise
         */
        void onAutoFocus(boolean success);
    }

    /**
     * Callback interface used to notify on auto focus start and stop.
     * <p/>
     * <p>This is only supported in continuous autofocus modes -- {@link
     * Camera.Parameters#FOCUS_MODE_CONTINUOUS_VIDEO} and {@link
     * Camera.Parameters#FOCUS_MODE_CONTINUOUS_PICTURE}. Applications can show
     * autofocus animation based on this.</p>
     */
    public interface AutoFocusMoveCallback {
        /**
         * Called when the camera auto focus starts or stops.
         *
         * @param start true if focus starts to move, false if focus stops to move
         */
        void onAutoFocusMoving(boolean start);
    }

    //==============================================================================================
    // Public
    //==============================================================================================

    /**
     * Stops the camera and releases the resources of the camera and underlying detector.
     */
    public void release() {
        synchronized (mCameraLock) {
            stop();
            mFrameProcessor.release();
        }
    }

    /**
     * Opens the camera and starts sending preview frames to the underlying detector.  The supplied
     * surface holder is used for the preview so frames can be displayed to the user.
     *
     * @param surfaceHolder the surface holder to use for the preview frames
     * @throws IOException if the supplied surface holder could not be used as the preview display
     */
    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.CAMERA)
    public CamSource start(SurfaceHolder surfaceHolder) throws IOException {
        synchronized (mCameraLock) {
            if (mCamera != null) {
                return this;
            }
            try {
                previewSurfaceHolder = surfaceHolder;
                int requestedCameraId = getIdForRequestedCamera(mFacing);
                if (requestedCameraId == -1) {throw new RuntimeException("Could not find requested camera.");}

                Camera camera = Camera.open(requestedCameraId);

                Camera.Size pictureSize = getBestAspectPictureSize(camera, mContext);
                Camera.Size previewSize = getBestAspectPreviewSize(camera, mContext);
                mPreviewSize = new Size(previewSize.width, previewSize.height);
                mPictureSize = new Size(pictureSize.width, pictureSize.height);
                int[] previewFpsRange = selectPreviewFpsRange(camera, mRequestedFps);
                if (previewFpsRange == null) {throw new RuntimeException("Could not find suitable preview frames per second range.");}

                Camera.Parameters parameters = camera.getParameters();
                parameters.setPictureSize(pictureSize.width, pictureSize.height);
                parameters.setPreviewSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                parameters.setPreviewFpsRange(previewFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX], previewFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
                parameters.setPreviewFormat(ImageFormat.NV21);

                setRotation(camera, parameters, requestedCameraId);

                if (mFocusMode != null) {
                    if (parameters.getSupportedFocusModes().contains(mFocusMode)) {
                        parameters.setFocusMode(mFocusMode);
                    } else {
                        Log.i(TAG, "Camera focus mode: " + mFocusMode + " is not supported on this device.");
                    }
                }

                // setting mFocusMode to the one set in the params
                mFocusMode = parameters.getFocusMode();

                if (mFlashMode != null) {
                    if (parameters.getSupportedFlashModes() != null) {
                        if (parameters.getSupportedFlashModes().contains(mFlashMode)) {
                            parameters.setFlashMode(mFlashMode);
                        } else {
                            Log.i(TAG, "Camera flash mode: " + mFlashMode + " is not supported on this device.");
                        }
                    }
                }


                // setting mFlashMode to the one set in the params
                mFlashMode = parameters.getFlashMode();

                camera.setParameters(parameters);

                // Four frame buffers are needed for working with the camera:
                //
                //   one for the frame that is currently being executed upon in doing detection
                //   one for the next pending frame to process immediately upon completing detection
                //   two for the frames that the camera uses to populate future preview images
                camera.setPreviewCallbackWithBuffer(new CameraPreviewCallback());
                camera.addCallbackBuffer(createPreviewBuffer(mPreviewSize));
                camera.addCallbackBuffer(createPreviewBuffer(mPreviewSize));
                camera.addCallbackBuffer(createPreviewBuffer(mPreviewSize));
                camera.addCallbackBuffer(createPreviewBuffer(mPreviewSize));

                mCamera = camera;

                mCamera.setPreviewDisplay(previewSurfaceHolder);
                mCamera.startPreview();

                mProcessingThread = new Thread(mFrameProcessor);
                mFrameProcessor.setActive(true);
                mProcessingThread.start();

            } catch (RuntimeException e) {
                e.printStackTrace();
                Toast.makeText(mContext, e.getMessage(), Toast.LENGTH_LONG).show();
                return null;
            }
        }
        return this;
    }

    /**
     * Closes the camera and stops sending frames to the underlying frame detector.
     * <p/>
     * This camera source may be restarted again by calling {@link #start(SurfaceHolder)}.
     * <p/>
     * Call {@link #release()} instead to completely shut down this camera source and release the
     * resources of the underlying detector.
     */
    public void stop() {
        synchronized (mCameraLock) {
            mFrameProcessor.setActive(false);
            if (mProcessingThread != null) {
                try {
                    // Wait for the thread to complete to ensure that we can't have multiple threads
                    // executing at the same time (i.e., which would happen if we called start too
                    // quickly after stop).
                    mProcessingThread.join();
                } catch (InterruptedException e) {
                    Log.d(TAG, "Frame processing thread interrupted on release.");
                }
                mProcessingThread = null;
            }

            // clear the buffer to prevent oom exceptions
            mBytesToByteBuffer.clear();

            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.setPreviewCallbackWithBuffer(null);
                try {
                    // We want to be compatible back to Gingerbread, but SurfaceTexture
                    // wasn't introduced until Honeycomb.  Since the interface cannot use a SurfaceTexture, if the
                    // developer wants to display a preview we must use a SurfaceHolder.  If the developer doesn't
                    // want to display a preview we use a SurfaceTexture if we are running at least Honeycomb.

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        mCamera.setPreviewTexture(null);

                    } else {
                        mCamera.setPreviewDisplay(null);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to clear camera preview: " + e);
                }
                mCamera.release();
                mCamera = null;
            }
        }
    }



    /**
     * Returns the preview size that is currently in use by the underlying camera.
     */
    public Size getPreviewSize() {
        return mPreviewSize;
    }

    /*
     * Returns the picture size that is currently in use
     */
    public Size getPictureSize() {
        return mPictureSize;
    }

    /**
     * Returns the selected camera; one of {@link #CAMERA_FACING_BACK} or
     * {@link #CAMERA_FACING_FRONT}.
     */
    public int getCameraFacing() {
        return mFacing;
    }

    public int doZoom(float scale) {
        synchronized (mCameraLock) {
            if (mCamera == null) {
                return 0;
            }
            int currentZoom = 0;
            int maxZoom;
            Camera.Parameters parameters = mCamera.getParameters();
            if (!parameters.isZoomSupported()) {
                Log.w(TAG, "Zoom is not supported on this device");
                return currentZoom;
            }
            maxZoom = parameters.getMaxZoom();

            currentZoom = parameters.getZoom() + 1;
            float newZoom;
            if (scale > 1) {
                newZoom = currentZoom + scale * (maxZoom / 10);
            } else {
                newZoom = currentZoom * scale;
            }
            currentZoom = Math.round(newZoom) - 1;
            if (currentZoom < 0) {
                currentZoom = 0;
            } else if (currentZoom > maxZoom) {
                currentZoom = maxZoom;
            }
            parameters.setZoom(currentZoom);
            mCamera.setParameters(parameters);
            return currentZoom;
        }
    }

    /**
     * Initiates taking a picture, which happens asynchronously.  The camera source should have been
     * activated previously with {@link #start(SurfaceHolder)}.  The camera preview is suspended
     * while the picture is being taken, but will resume once picture taking is done.
     *
     * @param shutter the callback for image capture moment, or null
     * @param jpeg    the callback for JPEG image data, or null
     */
    public void takePicture(ShutterCallback shutter, PictureCallback jpeg) {
        Log.d("ASD", "TRYING TO TAKE PICTURE");
        synchronized (mCameraLock) {
            if (mCamera != null) {
                setFlashMode(mFlashMode);
                PictureStartCallback startCallback = new PictureStartCallback();
                startCallback.mDelegate = shutter;
                PictureDoneCallback doneCallback = new PictureDoneCallback();
                doneCallback.mDelegate = jpeg;
                mCamera.takePicture(startCallback, null, null, doneCallback);
            }
        }
    }


    public boolean setFlashMode(@FlashMode String mode) {
        synchronized (mCameraLock) {
            if (mCamera != null && mode != null) {
                Camera.Parameters parameters = mCamera.getParameters();
                List<String> supportedFlashModes = parameters.getSupportedFlashModes();
                if(supportedFlashModes == null || supportedFlashModes.isEmpty() || supportedFlashModes.size() == 1 && supportedFlashModes.get(0).equals(Camera.Parameters.FLASH_MODE_OFF)) {
                    return false;
                }
                if (supportedFlashModes.contains(mode)) {
                    parameters.setFlashMode(mode);
                    mCamera.setParameters(parameters);
                    mFlashMode = mode;
                    return true;
                }
            }
            return false;
        }
    }



    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public boolean setAutoFocusMoveCallback(@Nullable AutoFocusMoveCallback cb) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return false;
        }

        synchronized (mCameraLock) {
            if (mCamera != null) {
                CameraAutoFocusMoveCallback autoFocusMoveCallback = null;
                if (cb != null) {
                    autoFocusMoveCallback = new CameraAutoFocusMoveCallback();
                    autoFocusMoveCallback.mDelegate = cb;
                }
                mCamera.setAutoFocusMoveCallback(autoFocusMoveCallback);
            }
        }

        return true;
    }


    /**
     * Only allow creation via the builder class.
     */
    private CamSource() {}

    /**
     * Wraps the camera1 shutter callback so that the deprecated API isn't exposed.
     */
    private class PictureStartCallback implements Camera.ShutterCallback {
        private ShutterCallback mDelegate;




        @Override
        public void onShutter() {
            if (mDelegate != null) {
                mDelegate.onShutter();
            }
        }
    }

    /**
     * Wraps the final callback in the camera1 sequence, so that we can automatically turn the camera
     * preview back on after the picture has been taken.
     */
    private class PictureDoneCallback implements Camera.PictureCallback {
        private PictureCallback mDelegate;

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            if (mDelegate != null) {
                Bitmap pic = BitmapFactory.decodeByteArray(data, 0, data.length);
                mDelegate.onPictureTaken(data);
            }

            synchronized (mCameraLock) {
                if (mCamera != null) {
                    mCamera.startPreview();
                }
            }
        }
    }


    /**
     * Wraps the camera1 auto focus move callback so that the deprecated API isn't exposed.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private class CameraAutoFocusMoveCallback implements Camera.AutoFocusMoveCallback {
        private AutoFocusMoveCallback mDelegate;

        @Override
        public void onAutoFocusMoving(boolean start, Camera camera) {
            if (mDelegate != null) {
                mDelegate.onAutoFocusMoving(start);
            }
        }
    }

    /**
     * Gets the id for the camera specified by the direction it is facing.  Returns -1 if no such
     * camera was found.
     *
     * @param facing the desired camera (front-facing or rear-facing)
     */
    private static int getIdForRequestedCamera(int facing) {
        CameraInfo cameraInfo = new CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); ++i) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == facing) {
                return i;
            }
        }
        return -1;
    }

    public static Camera.Size getBestAspectPictureSize(Camera camera, Context context) {
        Camera.Parameters parameters = camera.getParameters();
        List<Camera.Size> supportedPictureSizes = parameters.getSupportedPictureSizes();
        float targetRatio = Utils2.getScreenRatio(context);
        Camera.Size bestSize = null;
        TreeMap<Double, List<Camera.Size>> diffs = new TreeMap<>();

        for (Camera.Size size : supportedPictureSizes) {
            float ratio = (float) size.width / size.height;
            double diff = Math.abs(ratio - targetRatio);
            if (diff < ratioTolerance){
                if (diffs.keySet().contains(diff)){
                    //add the value to the list
                    diffs.get(diff).add(size);
                } else {
                    List<Camera.Size> newList = new ArrayList<>();
                    newList.add(size);
                    diffs.put(diff, newList);
                }
            }
        }

        if(diffs.isEmpty()) {
            for (Camera.Size size : supportedPictureSizes) {
                float ratio = (float)size.width / size.height;
                double diff = Math.abs(ratio - targetRatio);
                if (diff < maxRatioTolerance){
                    if (diffs.keySet().contains(diff)){
                        //add the value to the list
                        diffs.get(diff).add(size);
                    } else {
                        List<Camera.Size> newList = new ArrayList<>();
                        newList.add(size);
                        diffs.put(diff, newList);
                    }
                }
            }
        }

        //diffs now contains all of the usable sizes
        //now let's see which one has the least amount of
        for (Map.Entry entry: diffs.entrySet()){
            List<?> entries = (List) entry.getValue();
            for (int i=0; i<entries.size(); i++) {
                Camera.Size s = (Camera.Size) entries.get(i);
                if(bestSize == null) {
                    bestSize = s;
                } else if(bestSize.width < s.width || bestSize.height < s.height) {
                    bestSize = s;
                }
            }
        }
        if(bestSize == null) return supportedPictureSizes.get(0);
        return bestSize;
    }

    public static Camera.Size getBestAspectPreviewSize(Camera camera, Context context) {
        Camera.Parameters parameters = camera.getParameters();
        List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
        float targetRatio = Utils2.getScreenRatio(context);
        Camera.Size bestSize = null;
        TreeMap<Double, List<Camera.Size>> diffs = new TreeMap<>();

        for (Camera.Size size : supportedPreviewSizes) {
            float ratio = (float)size.width / size.height;
            double diff = Math.abs(ratio - targetRatio);
            if (diff < ratioTolerance){
                if (diffs.keySet().contains(diff)){
                    //add the value to the list
                    diffs.get(diff).add(size);
                } else {
                    List<Camera.Size> newList = new ArrayList<>();
                    newList.add(size);
                    diffs.put(diff, newList);
                }
            }
        }

        if(diffs.isEmpty()) {
            for (Camera.Size size : supportedPreviewSizes) {
                float ratio = (float)size.width / size.height;
                double diff = Math.abs(ratio - targetRatio);
                if (diff < maxRatioTolerance){
                    if (diffs.keySet().contains(diff)){
                        //add the value to the list
                        diffs.get(diff).add(size);
                    } else {
                        List<Camera.Size> newList = new ArrayList<>();
                        newList.add(size);
                        diffs.put(diff, newList);
                    }
                }
            }
        }

        //diffs now contains all of the usable sizes
        //now let's see which one has the least amount of
        for (Map.Entry entry: diffs.entrySet()){
            List<?> entries = (List) entry.getValue();
            for (int i=0; i<entries.size(); i++) {
                Camera.Size s = (Camera.Size) entries.get(i);
                if(s.height <= 1080 && s.width <= 1920) {
                    if(bestSize == null) {
                        bestSize = s;
                    } else if(bestSize.width < s.width || bestSize.height < s.height) {
                        bestSize = s;
                    }
                }
            }
        }
        if(bestSize == null) return supportedPreviewSizes.get(0);
        return bestSize;
    }


    private int[] selectPreviewFpsRange(Camera camera, float desiredPreviewFps) {
        // The camera API uses integers scaled by a factor of 1000 instead of floating-point frame
        // rates.
        int desiredPreviewFpsScaled = (int) (desiredPreviewFps * 1000.0f);

        // The method for selecting the best range is to minimize the sum of the differences between
        // the desired value and the upper and lower bounds of the range.  This may select a range
        // that the desired value is outside of, but this is often preferred.  For example, if the
        // desired frame rate is 29.97, the range (30, 30) is probably more desirable than the
        // range (15, 30).
        int[] selectedFpsRange = null;
        int minDiff = Integer.MAX_VALUE;
        List<int[]> previewFpsRangeList = camera.getParameters().getSupportedPreviewFpsRange();
        for (int[] range : previewFpsRangeList) {
            int deltaMin = desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX];
            int deltaMax = desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX];
            int diff = Math.abs(deltaMin) + Math.abs(deltaMax);
            if (diff < minDiff) {
                selectedFpsRange = range;
                minDiff = diff;
            }
        }
        return selectedFpsRange;
    }

    /**
     * Calculates the correct rotation for the given camera id and sets the rotation in the
     * parameters.  It also sets the camera's display orientation and rotation.
     *
     * @param parameters the camera parameters for which to set the rotation
     * @param cameraId   the camera id to set rotation based on
     */
    private void setRotation(Camera camera, Camera.Parameters parameters, int cameraId) {
        WindowManager windowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        int degrees = 0;
        switch (windowManager.getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            default:
                Log.e(TAG, "Bad rotation value");
        }

        CameraInfo cameraInfo = new CameraInfo();
        Camera.getCameraInfo(cameraId, cameraInfo);

        int angle;
        int displayAngle;
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            angle = (cameraInfo.orientation + degrees) % 360;
            displayAngle = (360 - angle) % 360; // compensate for it being mirrored
        } else {  // back-facing
            angle = (cameraInfo.orientation - degrees + 360) % 360;
            displayAngle = angle;
        }

        // This corresponds to the rotation constants in {@link Frame}.
        mRotation = angle / 90;

        camera.setDisplayOrientation(displayAngle);
        parameters.setRotation(angle);
    }

    /**
     * Creates one buffer for the camera preview callback.  The size of the buffer is based off of
     * the camera preview size and the format of the camera image.
     *
     * @return a new preview buffer of the appropriate size for the current camera settings
     */
    private byte[] createPreviewBuffer(Size previewSize) {
        int bitsPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.NV21);
        long sizeInBits = previewSize.getHeight() * previewSize.getWidth() * bitsPerPixel;
        int bufferSize = (int) Math.ceil(sizeInBits / 8.0d) + 1;
        //
        // NOTICE: This code only works when using play services v. 8.1 or higher.
        //
        // Creating the byte array this way and wrapping it, as opposed to using .allocate(),
        // should guarantee that there will be an array to work with.
        byte[] byteArray = new byte[bufferSize];
        ByteBuffer buffer = ByteBuffer.wrap(byteArray);
        if (!buffer.hasArray() || (buffer.array() != byteArray)) {
            // I don't think that this will ever happen.  But if it does, then we wouldn't be
            // passing the preview content to the underlying detector later.
            throw new IllegalStateException("Failed to create valid buffer for camera source.");
        }
        mBytesToByteBuffer.put(byteArray, buffer);
        return byteArray;
    }

    //==============================================================================================
    // Frame processing
    //==============================================================================================

    /**
     * Called when the camera has a new preview frame.
     */
    private class CameraPreviewCallback implements Camera.PreviewCallback {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            mFrameProcessor.setNextFrame(data, camera);
        }
    }

    /**
     * This runnable controls access to the underlying receiver, calling it to process frames when
     * available from the camera.  This is designed to run detection on frames as fast as possible
     * (i.e., without unnecessary context switching or waiting on the next frame).
     * <p/>
     * While detection is running on a frame, new frames may be received from the camera.  As these
     * frames come in, the most recent frame is held onto as pending.  As soon as detection and its
     * associated processing are done for the previous frame, detection on the mostly recently
     * received frame will immediately start on the same thread.
     */
    private class FrameProcessingRunnable implements Runnable {
        private Detector<?> mDetector;
        private long mStartTimeMillis = SystemClock.elapsedRealtime();

        // This lock guards all of the member variables below.
        private final Object mLock = new Object();
        private boolean mActive = true;

        // These pending variables hold the state associated with the new frame awaiting processing.
        private long mPendingTimeMillis;
        private int mPendingFrameId = 0;
        private ByteBuffer mPendingFrameData;

        FrameProcessingRunnable(Detector<?> detector) {
            mDetector = detector;
        }

        /**
         * Releases the underlying receiver.  This is only safe to do after the associated thread
         * has completed, which is managed in camera source's release method above.
         */
        @SuppressLint("Assert")
        void release() {
            assert (mProcessingThread.getState() == State.TERMINATED);
            mDetector.release();
            mDetector = null;
        }

        /**
         * Marks the runnable as active/not active.  Signals any blocked threads to continue.
         */
        void setActive(boolean active) {
            synchronized (mLock) {
                mActive = active;
                mLock.notifyAll();
            }
        }

        /**
         * Sets the frame data received from the camera.  This adds the previous unused frame buffer
         * (if present) back to the camera, and keeps a pending reference to the frame data for
         * future use.
         */
        void setNextFrame(byte[] data, Camera camera) {
            synchronized (mLock) {
                if (mPendingFrameData != null) {
                    camera.addCallbackBuffer(mPendingFrameData.array());
                    mPendingFrameData = null;
                }
                if (!mBytesToByteBuffer.containsKey(data)) {
                    Log.d(TAG, "Skipping frame.  Could not find ByteBuffer associated with the image " + "data from the camera.");
                    return;
                }
                // Timestamp and frame ID are maintained here, which will give downstream code some
                // idea of the timing of frames received and when frames were dropped along the way.
                mPendingTimeMillis = SystemClock.elapsedRealtime() - mStartTimeMillis;
                mPendingFrameId++;
                mPendingFrameData = mBytesToByteBuffer.get(data);
                // Notify the processor thread if it is waiting on the next frame (see below).
                mLock.notifyAll();
            }
        }


        @Override
        public void run() {
            Frame outputFrame;
            ByteBuffer data;

            while (true) {
                synchronized (mLock) {
                    while (mActive && (mPendingFrameData == null)) {
                        try {
                            // Wait for the next frame to be received from the camera, since we
                            // don't have it yet.
                            mLock.wait();
                        } catch (InterruptedException e) {
                            Log.d(TAG, "Frame processing loop terminated.", e);
                            return;
                        }
                    }

                    if (!mActive) {
                        // Exit the loop once this camera source is stopped or released.  We check
                        // this here, immediately after the wait() above, to handle the case where
                        // setActive(false) had been called, triggering the termination of this
                        // loop.
                        return;
                    }

                    //REDUCE SIZE OF CAMERA PREVIEW
                    int previewW = mPreviewSize.getWidth();
                    int previewH = mPreviewSize.getHeight();
                    //Log.d("ASD", "FRAME SIZE: "+previewW+"x"+previewH);
                    outputFrame = new Frame.Builder()
                            .setImageData(quarterNV21(mPendingFrameData, previewW, previewH), previewW/4, previewH/4, ImageFormat.NV21)
                            .setId(mPendingFrameId)
                            .setTimestampMillis(mPendingTimeMillis)
                            .setRotation(mRotation)
                            .build();

                    // Hold onto the frame data locally, so that we can use this for detection
                    // below.  We need to clear mPendingFrameData to ensure that this buffer isn't
                    // recycled back to the camera before we are done using that data.
                    data = mPendingFrameData;
                    mPendingFrameData = null;
                }

                // The code below needs to run outside of synchronization, because this will allow
                // the camera to add pending frame(s) while we are running detection on the current
                // frame.

                try {
                    mDetector.receiveFrame(outputFrame);
                    SparseArray<Face> faces = (SparseArray<Face>) mDetector.detect(outputFrame);
                    faceSize = faces.size();
                } catch (Throwable t) {
                    Log.e(TAG, "Exception thrown from receiver.", t);
                } finally {
                    mCamera.addCallbackBuffer(data.array());
                }
            }
        }
    }

    //RESIZE PREVIEW FRAMES TO HALF FOR A FASTER FACE DETECTION
    private static ByteBuffer quarterNV21(ByteBuffer d, int imageWidth, int imageHeight) {
        byte[] data = d.array();
        byte[] yuv = new byte[imageWidth/4 * imageHeight/4 * 3 / 2];
        // halve yuma
        int i = 0;
        for (int y = 0; y < imageHeight; y+=4) {
            for (int x = 0; x < imageWidth; x+=4) {
                yuv[i] = data[y * imageWidth + x];
                i++;
            }
        }
        // halve U and V color components
        for (int y = 0; y < imageHeight / 2; y+=4) {
            for (int x = 0; x < imageWidth; x += 8) {
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                i++;
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + (x + 1)];
                i++;
            }
        }
        //REDUCED TO QUARTER QUALITY AND ONLY IN GRAY SCALE!
        return ByteBuffer.wrap(yuv);
    }


}
