package com.oakonell.huematch;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
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
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.philips.lighting.hue.listener.PHLightListener;
import com.philips.lighting.hue.sdk.PHHueSDK;
import com.philips.lighting.model.PHBridge;
import com.philips.lighting.model.PHBridgeResource;
import com.philips.lighting.model.PHHueError;
import com.philips.lighting.model.PHLight;
import com.philips.lighting.model.PHLightState;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by Rob on 1/20/2017.
 */

public class Camera2Activity extends AppCompatActivity {
    private static final int BRIGHTNESS_MAX = 254;

    private static final String TAG = "AndroidCameraApi";
    private Button takePictureButton;
    private TextureView textureView;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private Button takePreviewButton;
    private ImageReader previewReader;
    private View sampleView;
    private TextView brightnessView;
    SeekBar brightnessSeekbar;

    int brightnessScale = 255;
    private PHHueSDK phHueSDK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2);
        phHueSDK = PHHueSDK.create();


        textureView = (TextureView) findViewById(R.id.texture);
        textureView.setSurfaceTextureListener(textureListener);
        takePictureButton = (Button) findViewById(R.id.btn_takepicture);

        takePreviewButton = (Button) findViewById(R.id.btn_takepreview);
        sampleView = findViewById(R.id.sample);
        brightnessView = (TextView) findViewById(R.id.brightness);
        brightnessSeekbar = (SeekBar) findViewById(R.id.seekBar);

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

        assert takePictureButton != null;
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture(false);
            }
        });

        takePreviewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePicture(true);
            }
        });
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            if (cameraDevice != null) {
                cameraDevice.close();
            }
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            if (cameraDevice != null) {
                cameraDevice.close();
            }
            cameraDevice = null;
        }
    };
    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Toast.makeText(Camera2Activity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
            createCameraPreview();
        }
    };

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected void takePicture(boolean preview) {
        if (null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(preview ? CameraDevice.TEMPLATE_PREVIEW : CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            final long start = System.nanoTime();

            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    long picTime = System.nanoTime() - start;
                    Log.i("Camera2", "Pic take time " + TimeUnit.NANOSECONDS.toMillis(picTime) + "ms");
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);

                        long start = System.nanoTime();
                        Bitmap picture = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        long byteToBitmapTime = System.nanoTime() - start;

                        start = System.nanoTime();
                        final ImageUtils.ColorAndBrightness colorAndBrightness = ImageUtils.getDominantColor(picture);
                        long statExtractTime = System.nanoTime() - start;

                        final String message = "Picture take time: " + TimeUnit.NANOSECONDS.toMillis(picTime) + "ms, Byte conversion: " + TimeUnit.NANOSECONDS.toMillis(byteToBitmapTime)
                                + "ms, stat Extract: " + TimeUnit.NANOSECONDS.toMillis(statExtractTime) + "ms---  color = " + colorAndBrightness.color + ", brightness= " + colorAndBrightness.brightness;
                        Log.i("HueMatcher", message);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(Camera2Activity.this, message, Toast.LENGTH_SHORT).show();
                            }
                        });

                        setLightsTo(colorAndBrightness);
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }


            };
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    createCameraPreview();
                }
            };
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private long start;

    protected void createCameraPreview() {
        boolean useImageReader = true;
        //int imageFormat = ImageFormat.NV21; not supported
        //int imageFormat = ImageFormat.RGB_565; not supported
        int imageFormat = ImageFormat.YUV_420_888;
        //int imageFormat = ImageFormat.JPEG;
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(imageFormat);
            }
            // choose the smallest resolution size, for speed
            Size sizeToUse = null;
            if (jpegSizes != null) {
                sizeToUse = jpegSizes[0];
                int currentArea = sizeToUse.getHeight() * sizeToUse.getWidth();
                for (int i = 1; i < jpegSizes.length; i++) {
                    int thisArea = jpegSizes[i].getHeight() * jpegSizes[i].getWidth();
                    if (thisArea < currentArea) {
                        sizeToUse = jpegSizes[i];
                        currentArea = thisArea;
                    }
                }
            }
            int width = 640;
            int height = 480;

            if (sizeToUse != null) {
                width = sizeToUse.getWidth();
                height = sizeToUse.getHeight();
            }

            SurfaceTexture texture = textureView.getSurfaceTexture();
            Surface surface = new Surface(texture);

            previewReader = ImageReader.newInstance(width, height, imageFormat, 10);
            List<Surface> outputSurfaces = new ArrayList<>(2);
            if (useImageReader) {
                outputSurfaces.add(previewReader.getSurface());
            }

            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {

                @Override
                public void onImageAvailable(ImageReader reader) {
                    long picTime = System.nanoTime() - start;
                    Log.i("Camera2", "Pic preview time " + TimeUnit.NANOSECONDS.toMillis(picTime) + "ms");
                    Image image = null;
                    try {

                        long start = System.nanoTime();
                        Bitmap bitmap = textureView.getBitmap();
                        long bitmapTime = System.nanoTime() - start;
                        Log.i("Camera2", "Bitmap retrieval time: " + TimeUnit.NANOSECONDS.toMillis(bitmapTime));

//                        final int imageFormat1 = reader.getImageFormat();
                        if (previewReader == null) return;

                        image = reader.acquireLatestImage();
//                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
//                        //Bitmap.Config.RGB_565
//                        byte[] bytes = new byte[buffer.capacity()];
//                        buffer.get(bytes);

//                        start = System.nanoTime();
//                        final Bitmap bitmap = ImageUtils.getBitmapFromYUV420Bytes(bytes, reader.getWidth(), reader.getHeight());
//                        long bitmapTime = System.nanoTime() - start;
//                        Log.i("Camera2", "Bitmap time: " + TimeUnit.NANOSECONDS.toMillis(bitmapTime));
                        start = System.nanoTime();
                        final ImageUtils.ColorAndBrightness colorAndBrightness = ImageUtils.getDominantColor(bitmap);
                        Log.i("Camera2", "ColorInfo time=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + "ms: color=" + colorAndBrightness.color + ", bright=" + colorAndBrightness.brightness);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                sampleView.setBackgroundColor(colorAndBrightness.color);

                                int adjustedBrightness = Math.min(BRIGHTNESS_MAX, (int) (1.0 * colorAndBrightness.brightness / brightnessScale * BRIGHTNESS_MAX));

                                brightnessView.setText("" + adjustedBrightness);
                            }
                        });
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                    start = System.nanoTime();
                }


            };
            previewReader.setOnImageAvailableListener(readerListener, mBackgroundHandler);

            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            if (useImageReader) {
                captureRequestBuilder.addTarget(previewReader.getSurface());
            }

            cameraDevice.createCaptureSession(Arrays.asList(surface, previewReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(Camera2Activity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(Camera2Activity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "openCamera X");
    }

    protected void updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);

        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                }

                @Override
                public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                    super.onCaptureFailed(session, request, failure);
                    Log.i("Camera2", "capture failed");
                }

                @Override
                public void onCaptureBufferLost(CameraCaptureSession session, CaptureRequest request, Surface target, long frameNumber) {
                    super.onCaptureBufferLost(session, request, target, frameNumber);
                    Log.i("Camera2", "onCaptureBufferLost");
                }
            }, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
        if (null != previewReader) {
            previewReader.close();
            previewReader = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(Camera2Activity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        PHBridge bridge = phHueSDK.getSelectedBridge();
        if (bridge != null) {

            if (phHueSDK.isHeartbeatEnabled(bridge)) {
                phHueSDK.disableHeartbeat(bridge);
            }

            phHueSDK.disconnect(bridge);
            super.onDestroy();
        }
    }

    private void setLightsTo(ImageUtils.ColorAndBrightness colorAndBrightness) {
        PHBridge bridge = phHueSDK.getSelectedBridge();
        List<PHLight> allLights = bridge.getResourceCache().getAllLights();
//        Random rand = new Random();
        for (PHLight light : allLights) {
            PHLightState lightState = new PHLightState();
//            lightState.setBrightness(rand.nextInt(BRIGHTNESS_MAX));

//            if (light.getLightType() == PHLight.PHLightType.DIM_LIGHT) {
            if (light.getLightType() == PHLight.PHLightType.CT_COLOR_LIGHT || light.getLightType() == PHLight.PHLightType.COLOR_LIGHT ||
                    light.getLightType() == PHLight.PHLightType.DIM_LIGHT || light.getLightType() == PHLight.PHLightType.CT_LIGHT) {
                lightState.setBrightness(colorAndBrightness.brightness);
            }
            // To validate your lightstate is valid (before sending to the bridge) you can use:
            // String validState = lightState.validateState();
//            }
            if (light.getLightType() == PHLight.PHLightType.CT_COLOR_LIGHT || light.getLightType() == PHLight.PHLightType.COLOR_LIGHT) {
                float[] xy = HueUtils.colorToXY(colorAndBrightness.color, light);
                lightState.setX(xy[0]);
                lightState.setY(xy[1]);
            }
            bridge.updateLightState(light, lightState, listener);
            //  bridge.updateLightState(light, lightState);   // If no bridge response is required then use this simpler form.
        }

    }

    // If you want to handle the response from the bridge, create a PHLightListener object.
    PHLightListener listener = new PHLightListener() {

        @Override
        public void onSuccess() {
            Toast.makeText(Camera2Activity.this, "Light success", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStateUpdate(Map<String, String> arg0, List<PHHueError> arg1) {
//            StringBuilder builder = new StringBuilder();
//            builder.append(arg0);

            Log.w(TAG, "Light has updated");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Camera2Activity.this, "Light updated", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onError(final int arg0, final String arg1) {
            if (arg0 == 42) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Camera2Activity.this, "Light error: " + arg0 + " - " + arg1, Toast.LENGTH_SHORT).show();
                }
            });

        }

        @Override
        public void onReceivingLightDetails(PHLight arg0) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Camera2Activity.this, "Light details", Toast.LENGTH_SHORT).show();
                }
            });

        }

        @Override
        public void onReceivingLights(List<PHBridgeResource> arg0) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Camera2Activity.this, "Lights received", Toast.LENGTH_SHORT).show();
                }
            });

        }

        @Override
        public void onSearchComplete() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Camera2Activity.this, "Light search", Toast.LENGTH_SHORT).show();
                }
            });

        }
    };
}