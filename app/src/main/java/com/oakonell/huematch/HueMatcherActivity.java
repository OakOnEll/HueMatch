package com.oakonell.huematch;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.oakonell.huematch.utils.AutoFitTextureView;
import com.oakonell.huematch.utils.HueUtils;
import com.oakonell.huematch.utils.ImageUtils;
import com.oakonell.huematch.utils.LicenseUtils;
import com.oakonell.huematch.utils.RunningFPSAverager;
import com.philips.lighting.hue.listener.PHLightListener;
import com.philips.lighting.hue.sdk.PHAccessPoint;
import com.philips.lighting.hue.sdk.PHHueSDK;
import com.philips.lighting.hue.sdk.PHSDKListener;
import com.philips.lighting.model.PHBridge;
import com.philips.lighting.model.PHBridgeResource;
import com.philips.lighting.model.PHHueError;
import com.philips.lighting.model.PHHueParsingError;
import com.philips.lighting.model.PHLight;
import com.philips.lighting.model.PHLightState;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import hugo.weaving.DebugLog;
import io.fabric.sdk.android.Fabric;

/**
 * Created by Rob on 1/20/2017.
 */

public class HueMatcherActivity extends AppCompatActivity {
    public static final boolean DEBUG = false;

    private static final String TAG = "HueMatcherActivity";

    private static final java.lang.String BRIGHTNESS_SCALE_SAVE_KEY = "brightnessScale";
    private static final java.lang.String IS_CONTINUOUS_SAVE_KEY = "continuous";
    private static final java.lang.String ZOOM_RECT_SAVE_KEY = "zoomRect";

    private static final String FRAGMENT_DIALOG = "dialog";


    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final boolean DEEP_DEBUG = false;
    private static final int REQUEST_CODE_CONFIG = 101;

    private Size mPreviewSize;
    private HueSharedPreferences prefs;
    private Set<String> controlledIds;
    private Set<String> currentSessionLightIds;
    private boolean autostartContinuous;
    private float finger_spacing;
    private int zoom_level;
    private Rect zoomRect;

    private final LicenseUtils licenseUtils = new LicenseUtils();
    private View fps_heads_up;
    private View lights_fps_heads_up;
    private TextView cam_fps;
    private TextView light_fps;

    public PHHueSDK getPhHueSDK() {
        return phHueSDK;
    }

    public Set<String> getControlledIds() {
        return controlledIds;
    }

    public Set<String> getCurrentSessionLightIds() {
        if (currentSessionLightIds != null) return currentSessionLightIds;
        return controlledIds;
    }

    public BridgeUpdateListener getBridgeUpdateListener() {
        return bridgeUpdateListener;
    }

    public void setBridgeUpdateListener(BridgeUpdateListener bridgeUpdateListener) {
        this.bridgeUpdateListener = bridgeUpdateListener;
    }

    public void setCurrentSessionLights(Collection<PHLight> currentSessionLights) {
        if (currentSessionLights.size() == controlledIds.size()) {
            this.currentSessionLightIds = null;
            return;
        }
        currentSessionLightIds = new HashSet<>();
        for (PHLight each : currentSessionLights) {
            currentSessionLightIds.add(each.getIdentifier());
        }
    }


    enum CaptureState {
        OFF, STILL, CONTINUOUS
    }

    private AutoFitTextureView textureView;

    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;
    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private final Semaphore mCameraOpenCloseLock = new Semaphore(1);


    private Size imageDimension;

    private ImageButton takeStillButton;
    private ImageButton takeContinuousButton;

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;


    private View sampleView;

    private TextView brightnessView;

    private int brightnessScale = HueUtils.BRIGHTNESS_MAX;
    private CaptureState captureState = CaptureState.OFF;

    private PHHueSDK phHueSDK;
    private int transitionTimeHundredsOfMs;

    @Override
    @DebugLog
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hue_matcher);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
//                        .detectDiskReads()
                    .penaltyLog()
                    .penaltyFlashScreen()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
//                        .detectLeakedSqlLiteObjects()
//                        .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build());
        }
        Fabric.with(this, new Crashlytics());

        licenseUtils.onCreateBind(this, null);

        phHueSDK = PHHueSDK.create();

        phHueSDK.getNotificationManager().registerSDKListener(phsdkListener);


        fps_heads_up = findViewById(R.id.fps_heads_up);
        cam_fps = (TextView) findViewById(R.id.cam_fps);
        lights_fps_heads_up = findViewById(R.id.lights_fps_heads_up);
        light_fps = (TextView) findViewById(R.id.light_fps);


        textureView = (AutoFitTextureView) findViewById(R.id.texture);
        textureView.setSurfaceTextureListener(textureListener);
        textureView.setOnTouchListener(surfaceTouchListener);

        takeStillButton = (ImageButton) findViewById(R.id.btn_sample_still);
        takeContinuousButton = (ImageButton) findViewById(R.id.btn_sample_continuously);

        ViewOutlineProvider viewOutlineProvider = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                // Or read size directly from the view's width/height
                int size = getResources().getDimensionPixelSize(R.dimen.fab_size);
                outline.setOval(0, 0, size, size);
            }
        };
        takeContinuousButton.setOutlineProvider(viewOutlineProvider);
        takeStillButton.setOutlineProvider(viewOutlineProvider);

        sampleView = findViewById(R.id.sample);

        brightnessView = (TextView) findViewById(R.id.brightness);
        SeekBar brightnessSeekbar = (SeekBar) findViewById(R.id.seekBar);
        brightnessSeekbar.setProgress(brightnessScale);


        brightnessSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                brightnessScale = i;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        if (savedInstanceState != null) {
            brightnessScale = savedInstanceState.getInt(BRIGHTNESS_SCALE_SAVE_KEY, HueUtils.BRIGHTNESS_MAX);
            brightnessSeekbar.setProgress(brightnessScale);

            zoomRect = savedInstanceState.getParcelable(ZOOM_RECT_SAVE_KEY);

            // startContinuous on init after camera is running, and after prefs is assigned to get controlledIds
            if (savedInstanceState.getBoolean(IS_CONTINUOUS_SAVE_KEY, false)) {
                autostartContinuous = true;
            }
        }


        takeStillButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takeStill(false);
            }
        });

        takeContinuousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takeContinuous(false);
            }
        });

        prefs = HueSharedPreferences.getInstance(getApplicationContext());

    }

    private final RunningFPSAverager camFpsAverager = new RunningFPSAverager();
    private final RunningFPSAverager lightFpsAverager = new RunningFPSAverager();

    private long captureCallBackEnd = System.nanoTime();
    private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            long captureCallBackStart = System.nanoTime();
            long picDuration = captureCallBackStart - captureCallBackEnd;
            if (DEEP_DEBUG) {
                Log.i("Camera2", "capture completed -pic preview time " + TimeUnit.NANOSECONDS.toMillis(picDuration) + "ms");
            }

            processCapturedImage(picDuration);
            if (prefs.getViewFPS()) {
                final double fps = camFpsAverager.addSample(System.nanoTime() - captureCallBackEnd);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        cam_fps.setText(NumberFormat.getNumberInstance().format(fps));
                    }
                });
            }
            captureCallBackEnd = System.nanoTime();
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Log.i("Camera2", "capture failed");
        }

        @Override
        public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
            super.onCaptureBufferLost(session, request, target, frameNumber);
            Log.i("Camera2", "onCaptureBufferLost");
        }
    };

    static class BitMapData {
        Bitmap bitmap;
        long bitmapDurationNs;
        long picDurationNs;
    }

    private void processCapturedImage(final long picDurationNs) {
        if (processImageTask != null && processImageTask.getStatus() != AsyncTask.Status.FINISHED)
            return;

        CaptureState currentCaptureState = captureState;
        if (captureState == CaptureState.STILL) {
            captureState = CaptureState.OFF;
        }

        long start = System.nanoTime();
        Bitmap bitmap = textureView.getBitmap();
        long bitmapDuration = System.nanoTime() - start;
        BitMapData data = new BitMapData();
        data.bitmap = bitmap;
        data.bitmapDurationNs = bitmapDuration;
        data.picDurationNs = picDurationNs;

        processImageTask = new ProcessImageTask(currentCaptureState);
        processImageTask.execute(data);
    }

    private long processImageCallBackEnd = System.nanoTime();

    class ProcessImageTask extends AsyncTask<BitMapData, Object, ImageUtils.ColorAndBrightness> {
        private final CaptureState captureState;

        ProcessImageTask(CaptureState captureState) {
            this.captureState = captureState;
        }

        @Override
        protected ImageUtils.ColorAndBrightness doInBackground(BitMapData[] objects) {
            long start = System.nanoTime();
            BitMapData bitmapData = objects[0];
            final ImageUtils.ColorAndBrightness colorAndBrightness = ImageUtils.getDominantColor(bitmapData.bitmap);
            long statExtractTime = System.nanoTime() - start;

            if (DEEP_DEBUG) {
                final String message = "Picture take time: " + TimeUnit.NANOSECONDS.toMillis(bitmapData.picDurationNs) + "ms" +
                        ", BitMap retrieval time: " + TimeUnit.NANOSECONDS.toMillis(bitmapData.bitmapDurationNs) + "ms, " +
                        "stat Extract: " + TimeUnit.NANOSECONDS.toMillis(statExtractTime) + "ms---" +
                        " color = " + colorAndBrightness.getColor() + ", brightness= " + colorAndBrightness.getBrightness();
                Log.i("HueMatcher", message);
                if (captureState == CaptureState.STILL) {
                    if (DEBUG) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(HueMatcherActivity.this, message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }

            final int adjustedBrightness = Math.min(HueUtils.BRIGHTNESS_MAX, (int) (1.0 * colorAndBrightness.getBrightness() / brightnessScale * HueUtils.BRIGHTNESS_MAX));
            final ImageUtils.ColorAndBrightness adjustedColorAndBrightness = new ImageUtils.ColorAndBrightness(colorAndBrightness.getColor(), adjustedBrightness);
            if (captureState != CaptureState.OFF) {
                setLightsTo(adjustedColorAndBrightness);
            }
            return adjustedColorAndBrightness;
        }

        @Override
        protected void onPostExecute(ImageUtils.ColorAndBrightness adjustedColorAndBrightness) {
            sampleView.setBackgroundColor(adjustedColorAndBrightness.getColor());
            brightnessView.setText(NumberFormat.getIntegerInstance().format(adjustedColorAndBrightness.getBrightness()));

            if (captureState == CaptureState.CONTINUOUS && prefs.getViewFPS()) {
                double fps = lightFpsAverager.addSample(System.nanoTime() - processImageCallBackEnd);
                light_fps.setText(NumberFormat.getNumberInstance().format(fps));
            }
            processImageCallBackEnd = System.nanoTime();
        }
    }

    private ProcessImageTask processImageTask;

    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform the image captured size according to the surface width and height
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };


    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            //This is called when the camera is open
            mCameraOpenCloseLock.release();
            Log.i(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCameraOpenCloseLock.release();
            if (cameraDevice != null) {
                cameraDevice.close();
            }
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            mCameraOpenCloseLock.release();
            if (cameraDevice != null) {
                cameraDevice.close();
            }
            cameraDevice = null;
        }
    };

    @DebugLog
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    @DebugLog
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while joining to background thread", e);
        }
    }

    @DebugLog
    protected void takeStill(boolean skipLightValidation) {
        if (!skipLightValidation && lightsHaveProblems(CaptureState.STILL)) return;
        captureState = CaptureState.STILL;
    }

    @DebugLog
    private boolean lightsHaveProblems(CaptureState captureType) {
        PHBridge bridge = phHueSDK.getSelectedBridge();
        Map<String, PHLight> lightsMap = bridge.getResourceCache().getLights();

        // accumulate lights off, prompt to turn on
        // accumulate unreachable lights
        // assure that at least one light is reachable/on
        Collection<PHLight> offLights = new HashSet<>();
        Collection<PHLight> unreachableLights = new HashSet<>();
        Collection<PHLight> okLights = new HashSet<>();

        for (String id : controlledIds) {
            PHLight light = lightsMap.get(id);
            final PHLightState state = light.getLastKnownLightState();
            if (!state.isOn()) {
                offLights.add(light);
            } else if (!state.isReachable()) {
                unreachableLights.add(light);
            } else {
                okLights.add(light);
            }
        }
        if (!offLights.isEmpty() || !unreachableLights.isEmpty() || okLights.isEmpty()) {
            LightsProblemDialogFragment dialog = LightsProblemDialogFragment.create(captureType, false);
            dialog.show(getSupportFragmentManager(), FRAGMENT_DIALOG);
            return true;
        }

        return false;
    }

    @DebugLog
    protected void takeContinuous(boolean force) {
        if (captureState == CaptureState.OFF) {
            startContinuous(force);
        } else {
            stopContinuous();
        }
    }

    private void stopContinuous() {
        captureState = CaptureState.OFF;
        takeStillButton.setEnabled(true);
        takeContinuousButton.setImageResource(R.drawable.ic_videocam_black_24dp);

        if (prefs.getViewFPS()) {
            lights_fps_heads_up.setVisibility(View.GONE);
        }


        // allow screen to turn off
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @DebugLog
    private void startContinuous(boolean skipLightValidation) {
        if (!skipLightValidation && lightsHaveProblems(CaptureState.CONTINUOUS)) return;
        if (prefs.getViewFPS()) {
            lights_fps_heads_up.setVisibility(View.VISIBLE);
        }

        takeStillButton.setEnabled(false);
        takeContinuousButton.setImageResource(R.drawable.ic_stop_black_24dp);
        captureState = CaptureState.CONTINUOUS;
        // keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }


    @DebugLog
    private void createCameraPreview() {
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

            SurfaceTexture texture = textureView.getSurfaceTexture();
            Surface surface = new Surface(texture);

            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                    if (autostartContinuous) {
                        startContinuous(false);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(HueMatcherActivity.this, "Configuration failed", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error creating camera preview", e);
        }
    }

    @DebugLog
    private void openCamera(int width, int height) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        try {
            setUpCameraOutputs(width, height);
            configureTransform(width, height);

            String cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(HueMatcherActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                Toast.makeText(this, R.string.camera_timeout, Toast.LENGTH_LONG).show();
                finish();
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Can't open camera", e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
        Log.e(TAG, "openCamera X");
    }

    @DebugLog
    private void updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);

        if (zoomRect != null) {
            captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
        }

        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), captureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Can't open camera preview", e);
        }
    }

    @DebugLog
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != cameraCaptureSessions) {
                cameraCaptureSessions.close();
                cameraCaptureSessions = null;
            }
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    @Override
    @DebugLog
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(HueMatcherActivity.this, "Sorry!!!, you can't use this app without granting camera permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    @DebugLog
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        licenseUtils.onResumeCheckLicense(this);

        controlledIds = prefs.getControlledLightIds();
        if (controlledIds.isEmpty()) {
            launchLightChooser();
        }
        transitionTimeHundredsOfMs = prefs.getTransitionTime();

        if (!prefs.getViewFPS()) {
            fps_heads_up.setVisibility(View.GONE);
        } else {
            fps_heads_up.setVisibility(View.VISIBLE);
        }

        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
        Log.i(TAG, "Starting heartbeat to update cache");
        phHueSDK.enableHeartbeat(phHueSDK.getSelectedBridge(), 2000);
    }

    @Override
    @DebugLog
    protected void onPause() {
        Log.e(TAG, "onPause");
        closeCamera();
        stopBackgroundThread();
        if (processImageTask != null) {
            processImageTask.cancel(true);
            processImageTask = null;
        }
        phHueSDK.disableHeartbeat(phHueSDK.getSelectedBridge());

        super.onPause();
    }

    @Override
    @DebugLog
    protected void onDestroy() {
        super.onDestroy();
        licenseUtils.onDestroyRelease(this);

        PHBridge bridge = phHueSDK.getSelectedBridge();
        if (bridge != null) {
            if (phHueSDK.isHeartbeatEnabled(bridge)) {
                phHueSDK.disableHeartbeat(bridge);
            }
            phHueSDK.getNotificationManager().unregisterSDKListener(phsdkListener);
        }
    }


    @DebugLog
    private void setLightsTo(ImageUtils.ColorAndBrightness colorAndBrightness) {
        PHBridge bridge = phHueSDK.getSelectedBridge();
        Map<String, PHLight> lightsMap = bridge.getResourceCache().getLights();


        for (String id : getCurrentSessionLightIds()) {
            PHLight light = lightsMap.get(id);
            if (light == null) {
                Log.e(TAG, "No light with id '" + id + "' was found!");
                continue;
            }
            PHLightState lightState = new PHLightState();
            // API takes in 100s of ms, not ms itself.
            lightState.setTransitionTime(transitionTimeHundredsOfMs);


            final PHLight.PHLightType lightType = light.getLightType();
            if (lightType == PHLight.PHLightType.CT_COLOR_LIGHT || lightType == PHLight.PHLightType.COLOR_LIGHT ||
                    lightType == PHLight.PHLightType.DIM_LIGHT || lightType == PHLight.PHLightType.CT_LIGHT) {
                lightState.setBrightness(colorAndBrightness.getBrightness());
            }

            float[] xy = HueUtils.colorToXY(colorAndBrightness.getColor(), light);
            if (lightType == PHLight.PHLightType.CT_LIGHT) {
                int ct = HueUtils.xyToTemperature(xy);
                lightState.setCt((int) ct, true);
            } else if (lightType == PHLight.PHLightType.CT_COLOR_LIGHT || lightType == PHLight.PHLightType.COLOR_LIGHT) {
                lightState.setX(xy[0]);
                lightState.setY(xy[1]);
            }

            // To validate your lightstate is valid (before sending to the bridge) you can use:
            // String validState = lightState.validateState();

            bridge.updateLightState(light, lightState, lightListener);
            //  bridge.updateLightState(light, lightState);   // If no bridge response is required then use this simpler form.
        }

    }

    // If you want to handle the response from the bridge, create a PHLightListener object.
    private final PHLightListener lightListener = new PHLightListener() {

        @Override
        public void onSuccess() {
            if (!DEBUG) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(HueMatcherActivity.this, "Light success", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onStateUpdate(Map<String, String> arg0, List<PHHueError> arg1) {
            StringBuilder builder = new StringBuilder("Updated lights:");
            for (Map.Entry<String, String> entry : arg0.entrySet()) {
                builder.append("\n");
                builder.append(entry.getKey()).append(",").append(entry.getValue());
            }
            for (PHHueError each : arg1) {
                builder.append("\n\t");
                builder.append(each.getAddress()).append(":").append(each.getCode()).append("-").append(each.getMessage());
            }
            Log.w(TAG, "Light has updated: " + builder.toString());
            if (!DEBUG) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(HueMatcherActivity.this, "Lights updated", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onError(final int arg0, final String arg1) {
            if (arg0 == 42) {
                //Log.e(TAG, "Received (ignorable?) light error:" + arg0 + "-" + arg1);
                return;
            }
            Log.e(TAG, "Received light error:" + arg0 + "-" + arg1);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (captureState == CaptureState.CONTINUOUS) {
                        stopContinuous();
                        LightsProblemDialogFragment dialog = LightsProblemDialogFragment.create(CaptureState.CONTINUOUS, true);
                        dialog.show(getSupportFragmentManager(), FRAGMENT_DIALOG);
                    } else {
                        Toast.makeText(HueMatcherActivity.this, "Light error: " + arg0 + " - " + arg1, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @Override
        public void onReceivingLightDetails(PHLight arg0) {
            if (!DEBUG) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(HueMatcherActivity.this, "Light details", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onReceivingLights(List<PHBridgeResource> arg0) {
            if (!DEBUG) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(HueMatcherActivity.this, "Lights received", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onSearchComplete() {
            if (!DEBUG) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(HueMatcherActivity.this, "Light search", Toast.LENGTH_SHORT).show();
                }
            });
        }
    };


    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    @DebugLog
    private void setUpCameraOutputs(int width, int height) {
        Activity activity = this;
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // For still image captures, we use the largest available size.
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new ImageUtils.CompareSizesByArea());

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                int mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    //noinspection SuspiciousNameCombination
                    rotatedPreviewWidth = height;
                    //noinspection SuspiciousNameCombination
                    rotatedPreviewHeight = width;
                    //noinspection SuspiciousNameCombination
                    maxPreviewWidth = displaySize.y;
                    //noinspection SuspiciousNameCombination
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > ImageUtils.MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = ImageUtils.MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > ImageUtils.MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = ImageUtils.MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = ImageUtils.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    textureView.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                // Check if the flash is supported.
                //Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                //mFlashSupported = available == null ? false : available;

                //mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Can't set camera outputs", e);
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.

            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getSupportFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    @DebugLog
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == textureView || null == mPreviewSize) {
            return;
        }
        int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
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
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@NonNull Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }


    @Override
    @DebugLog
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.w(TAG, "Inflating home menu");
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_hue_matcher, menu);
        return true;
    }

    /**
     * Called when option is selected.
     *
     * @param item the MenuItem object.
     * @return boolean Return false to allow normal menu processing to proceed,  true to consume it here.
     */
    @Override
    @DebugLog
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                launchLightChooser();
                break;
            case R.id.action_about:
                Intent intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @DebugLog
    private void launchLightChooser() {
        Intent intent = new Intent(getApplicationContext(), ControlledLightsActivity.class);
        startActivityForResult(intent, REQUEST_CODE_CONFIG);
//        startActivity(intent);
    }


    @Override
    @DebugLog
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(BRIGHTNESS_SCALE_SAVE_KEY, brightnessScale);
        if (captureState == CaptureState.CONTINUOUS) {
            outState.putBoolean(IS_CONTINUOUS_SAVE_KEY, true);
        }
        if (zoomRect != null) {
            outState.putParcelable(ZOOM_RECT_SAVE_KEY, zoomRect);
        }
    }

    /**
     * Determine the space between the first two fingers
     */
    @SuppressWarnings("deprecation")
    private float getFingerSpacing(MotionEvent event) {

        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }


    private final View.OnTouchListener surfaceTouchListener = new View.OnTouchListener() {
        public boolean onTouch(View view, MotionEvent event) {
            // http://stackoverflow.com/questions/35968315/android-camera2-handle-zoom
            try {
                CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
                float maxZoom = (characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)) * 10;

                Rect m = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                int action = event.getAction();
                float current_finger_spacing;

                if (event.getPointerCount() > 1) {
                    // Multi touch logic
                    current_finger_spacing = getFingerSpacing(event);

                    if (finger_spacing != 0) {
                        if (current_finger_spacing > finger_spacing && maxZoom > zoom_level) {
                            zoom_level++;

                        } else if (current_finger_spacing < finger_spacing && zoom_level > 1) {
                            zoom_level--;

                        }
                        int minW = (int) (m.width() / maxZoom);
                        int minH = (int) (m.height() / maxZoom);
                        int difW = m.width() - minW;
                        int difH = m.height() - minH;
                        int cropW = (int) (difW / 100.0 * zoom_level);
                        int cropH = (int) (difH / 100.0 * zoom_level);
                        cropW -= cropW & 3;
                        cropH -= cropH & 3;
                        zoomRect = new Rect(cropW, cropH, m.width() - cropW, m.height() - cropH);
                        captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
                    }
                    finger_spacing = current_finger_spacing;
                } else {
                    if (action == MotionEvent.ACTION_UP) {
                        //single touch logic
                    }
                }

                try {
                    cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), captureCallback, mBackgroundHandler);
                } catch (CameraAccessException e) {
                    Log.e(TAG, "Camera error while zooming", e);
                } catch (NullPointerException ex) {
                    Log.e(TAG, "Camera error while zooming", ex);
                    ex.printStackTrace();
                }
            } catch (CameraAccessException e) {
                throw new RuntimeException("can not access camera.", e);
            }

            return true;
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
        // Pass on the activity result to the helper for handling
        if (licenseUtils.handleActivityResult(this, requestCode, resultCode, data)) {
            Log.d(TAG, "onActivityResult handled by IABUtil.");
            return;
        }
        // not handled, so handle it ourselves (here's where you'd
        // perform any handling of activity results not related to in-app
        // billing...
        if (requestCode == REQUEST_CODE_CONFIG) {
            if (resultCode == ControlledLightsActivity.RESULT_OK) {
                currentSessionLightIds = null;
            }
            return;
        }

        Log.d(TAG, "onActivityResult not handled by IABUtil.");
        super.onActivityResult(requestCode, resultCode, data);
    }


    interface BridgeUpdateListener {
        void onCacheUpdated();
    }

    private BridgeUpdateListener bridgeUpdateListener;

    private final PHSDKListener phsdkListener = new PHSDKListener() {
        long lastUpdate = System.currentTimeMillis();

        @Override
        public void onCacheUpdated(List<Integer> list, PHBridge phBridge) {
            Log.i(TAG, "cache update");
            if (!phBridge.getResourceCache().getBridgeConfiguration().getBridgeID().equals(phHueSDK.getSelectedBridge().getResourceCache().getBridgeConfiguration().getBridgeID())) {
                return;
            }

            long start = lastUpdate;
            lastUpdate = System.currentTimeMillis();

            Log.i(TAG, "Cache updated in " + (lastUpdate - start) + " ms: list=" + list.toString());
            final Map<String, Long> lastHeartbeat = phHueSDK.getLastHeartbeat();
            if (bridgeUpdateListener != null) {
                bridgeUpdateListener.onCacheUpdated();
            }
        }

        @Override
        public void onBridgeConnected(PHBridge phBridge, String s) {

        }

        @Override
        public void onAuthenticationRequired(PHAccessPoint phAccessPoint) {

        }

        @Override
        public void onAccessPointsFound(List<PHAccessPoint> list) {

        }

        @Override
        public void onError(int i, String s) {

        }

        @Override
        public void onConnectionResumed(PHBridge phBridge) {

        }

        @Override
        public void onConnectionLost(PHAccessPoint phAccessPoint) {

        }

        @Override
        public void onParsingErrors(List<PHHueParsingError> list) {

        }
    };

}