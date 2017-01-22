package com.oakonell.huematch;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

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

public class HueExampleActivity extends AppCompatActivity {
    private PHHueSDK phHueSDK;
    private static final int MAX_HUE = 65535;
    private static final int BRIGHTNESS_MAX = 254;

    public static final String TAG = "QuickStart";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_name);
        setContentView(R.layout.activity_main);
        phHueSDK = PHHueSDK.create();
        Button randomButton;
        randomButton = (Button) findViewById(R.id.buttonRand);
        randomButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                randomLights();
            }

        });

    }

    public void randomLights() {
        PHBridge bridge = phHueSDK.getSelectedBridge();

        List<PHLight> allLights = bridge.getResourceCache().getAllLights();
        Random rand = new Random();

        for (PHLight light : allLights) {
            PHLightState lightState = new PHLightState();
            //lightState.setHue(rand.nextInt(MAX_HUE));
            lightState.setBrightness(rand.nextInt(BRIGHTNESS_MAX));
            // To validate your lightstate is valid (before sending to the bridge) you can use:
            // String validState = lightState.validateState();
            bridge.updateLightState(light, lightState, listener);
            //  bridge.updateLightState(light, lightState);   // If no bridge response is required then use this simpler form.
        }
    }

    // If you want to handle the response from the bridge, create a PHLightListener object.
    PHLightListener listener = new PHLightListener() {

        @Override
        public void onSuccess() {
            Toast.makeText(HueExampleActivity.this, "Light success", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStateUpdate(Map<String, String> arg0, List<PHHueError> arg1) {
//            StringBuilder builder = new StringBuilder();
//            builder.append(arg0);

            Log.w(TAG, "Light has updated");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(HueExampleActivity.this, "Light updated", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onError(final int arg0, final String arg1) {
            if (arg0 == 42) return;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(HueExampleActivity.this, "Light error: " + arg0 + " - " + arg1, Toast.LENGTH_SHORT).show();
                }
            });

        }

        @Override
        public void onReceivingLightDetails(PHLight arg0) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(HueExampleActivity.this, "Light details", Toast.LENGTH_SHORT).show();
                }
            });

        }

        @Override
        public void onReceivingLights(List<PHBridgeResource> arg0) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(HueExampleActivity.this, "Lights received", Toast.LENGTH_SHORT).show();
                }
            });

        }

        @Override
        public void onSearchComplete() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(HueExampleActivity.this, "Light search", Toast.LENGTH_SHORT).show();
                }
            });

        }
    };

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