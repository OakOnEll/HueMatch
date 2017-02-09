package com.oakonell.huematch.utils;

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

    public static int xyToTemperature(float xy[]) {
        float x = xy[0];
        float y = xy[1];
        // Method 1
        //http://stackoverflow.com/questions/13975917/calculate-colour-temperature-in-k
        //=(-449*((R1-0,332)/(S1-0,1858))^3)+(3525*((R1-0,332)/(S1-0,1858))^2)-(6823,3*((R1-0,332)/(S1-0,1858)))+(5520,33)
//        double temp1 = -449 * Math.pow((x - 0.332) / (y - 0.1858), 3)
//                + 3525 * Math.pow((x - 0.332) / (y - 0.1858), 2)
//                - 6823.3 * ((x - 0.332) / (y - 0.1858))
//                + 5520.33;
//        float micro1 = (float) (1 / temp1 * 1000000);

        // Method 2
        //http://www.vinland.com/Correlated_Color_Temperature.html
        //         437*((x - 0,332)/(0,1858 - y))^3+
        //                3601*((x - 0,332)/(0,1858 - y))^2+
        //                6831*((x - 0,332)/(0,1858 - y)) +
        //                5517
        double temp2 = (437 * Math.pow((x - 0.332) / (0.1858 - y), 3) +
                3601 * Math.pow((x - 0.332) / (0.1858 - y), 2) +
                6831 * ((x - 0.332) / (0.1858 - y))) +
                5517;
        //To set the light to a white value you need to interact with the “ct” (color temperature) resource,
        // which takes values in a scale called “reciprocal megakelvin” or “mirek”.
        // Using this scale, the warmest color 2000K is 500 mirek ("ct":500) and the coldest color 6500K is 153 mirek ("ct":153)
        float micro2 = (float) (1 / temp2 * 1000000);
        return (int) micro2;
    }

}
