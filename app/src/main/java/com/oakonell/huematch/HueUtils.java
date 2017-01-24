package com.oakonell.huematch;

import com.philips.lighting.hue.sdk.utilities.PHUtilities;
import com.philips.lighting.model.PHLight;

/**
 * Created by Rob on 1/20/2017.
 */

public class HueUtils {
    public static final int BRIGHTNESS_MAX = 254;

    public static float[] colorToXY(int color, PHLight light) {
        return PHUtilities.calculateXY(color, light.getModelNumber());
    }
}
