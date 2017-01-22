package com.oakonell.huematch;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.cameraview.CameraView;
import com.philips.lighting.hue.listener.PHLightListener;
import com.philips.lighting.hue.sdk.PHHueSDK;
import com.philips.lighting.model.PHBridge;
import com.philips.lighting.model.PHBridgeResource;
import com.philips.lighting.model.PHHueError;
import com.philips.lighting.model.PHLight;
import com.philips.lighting.model.PHLightState;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class HueMatcherActivity extends AppCompatActivity {
    private static final int MAX_HUE = 65535;
    private static final int BRIGHTNESS_MAX = 254;
    private static final String TAG = "QuickStart";

    boolean isContinuous;

    CameraView mCameraView;
    long picStart;
    private PHHueSDK phHueSDK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hue_matcher);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        setTitle(R.string.app_name);
        phHueSDK = PHHueSDK.create();
        final Button snapShotButton = (Button) findViewById(R.id.snapshot);
        final Button continuousButton = (Button) findViewById(R.id.continuous);
        mCameraView = (CameraView) findViewById(R.id.camera);


        final CameraView.Callback cameraCallback = new CameraView.Callback() {

            @Override
            public void onCameraOpened(CameraView cameraView) {
                Log.i("HueMatcher", "Camera opened");
            }

            @Override
            public void onCameraClosed(CameraView cameraView) {
                // stop sampling
                Log.i("HueMatcher", "Camera closed");
            }

            @Override
            public void onPictureTaken(CameraView cameraView, byte[] data) {
                long picTime = System.nanoTime() - picStart;
                if (isContinuous) {

                    // delay for sanity...
                    mCameraView.postDelayed(new Runnable() {
                        @Override
                        public void run() {

                            if (!mCameraView.isCameraOpened() || !isContinuous) {
                                // abort
                                return;
                            }
                            takePicture();
                        }
                    }, 1);
                }


                long start = System.nanoTime();
                //Bitmap.createBitmap(new int[],width, height, Bitmap.Config.RGB_565)
                Bitmap picture = BitmapFactory.decodeByteArray(data, 0, data.length);
                long byteToBitmapTime = System.nanoTime() - start;
                start = System.nanoTime();
                final ImageUtils.ColorAndBrightness colorAndBrightness = ImageUtils.getDominantColor(picture);
                long statExtractTime = System.nanoTime() - start;

                Log.i("HueMatcher", "Picture take time: " + TimeUnit.NANOSECONDS.toMillis(picTime) + "ms, Byte conversion: " + TimeUnit.NANOSECONDS.toMillis(byteToBitmapTime)
                        + "ms, stat Extract: " + TimeUnit.NANOSECONDS.toMillis(statExtractTime) + "ms---  color = " + colorAndBrightness.color + ", brightness= " + colorAndBrightness.brightness);


                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        long start = System.nanoTime();
                        setLightsTo(colorAndBrightness);
                        long setLightTime = System.nanoTime() - start;
                        Log.i("HueMatcher", "Set Light time: " + TimeUnit.NANOSECONDS.toMillis(setLightTime) + "ms");
                    }
                });
            }
        };

        continuousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isContinuous) {
                    mCameraView.setKeepScreenOn(false);
                    snapShotButton.setEnabled(true);
                    isContinuous = false;
                    return;
                }
                isContinuous = true;
                mCameraView.setKeepScreenOn(true);
                snapShotButton.setEnabled(false);

                takePicture();
            }
        });

        snapShotButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }

        });

        mCameraView.addCallback(cameraCallback);
    }

    private void setLightsTo(ImageUtils.ColorAndBrightness colorAndBrightness) {
        PHBridge bridge = phHueSDK.getSelectedBridge();
        List<PHLight> allLights = bridge.getResourceCache().getAllLights();
//        Random rand = new Random();
        for (PHLight light : allLights) {
            PHLightState lightState = new PHLightState();
//            lightState.setBrightness(rand.nextInt(BRIGHTNESS_MAX));

//            if (light.getLightType() == PHLight.PHLightType.DIM_LIGHT) {
            lightState.setBrightness(colorAndBrightness.brightness);
            // To validate your lightstate is valid (before sending to the bridge) you can use:
            // String validState = lightState.validateState();
//            }
//            if (light.getLightType() == PHLight.PHLightType.COLOR_LIGHT) {
//                lightState.setBrightness(colorAndBrightness.brightness);
//                lightState.setHue(colorAndBrightness.color);
//            }
            bridge.updateLightState(light, lightState, listener);
            //  bridge.updateLightState(light, lightState);   // If no bridge response is required then use this simpler form.
        }

    }

    private void takePicture() {
        picStart = System.nanoTime();
        mCameraView.takePicture();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraView.start();
    }

    @Override
    protected void onPause() {
        mCameraView.stop();
        super.onPause();
    }


    // If you want to handle the response from the bridge, create a PHLightListener object.
    PHLightListener listener = new PHLightListener() {

        @Override
        public void onSuccess() {
            Toast.makeText(HueMatcherActivity.this, "Light success", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStateUpdate(Map<String, String> arg0, List<PHHueError> arg1) {
//            StringBuilder builder = new StringBuilder();
//            builder.append(arg0);

            Log.w(TAG, "Light has updated");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(HueMatcherActivity.this, "Light updated", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onError(final int arg0, final String arg1) {
            if (arg0 == 42) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(HueMatcherActivity.this, "Light error: " + arg0 + " - " + arg1, Toast.LENGTH_SHORT).show();
                }
            });

        }

        @Override
        public void onReceivingLightDetails(PHLight arg0) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(HueMatcherActivity.this, "Light details", Toast.LENGTH_SHORT).show();
                }
            });

        }

        @Override
        public void onReceivingLights(List<PHBridgeResource> arg0) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(HueMatcherActivity.this, "Lights received", Toast.LENGTH_SHORT).show();
                }
            });

        }

        @Override
        public void onSearchComplete() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(HueMatcherActivity.this, "Light search", Toast.LENGTH_SHORT).show();
                }
            });

        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_hue_matcher, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
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






}
