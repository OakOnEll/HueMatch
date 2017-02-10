package com.oakonell.huematch;

import com.oakonell.huematch.utils.HueUtils;
import com.oakonell.huematch.utils.RunningFPSAverager;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {

    @Test
    public void testXyToCt() {
        float xy[] = new float[2];


        //153->500, cold->hot
        xy[0] = 0.675f;
        xy[1] = 0.322f;
        // 283
        assertEquals(239, HueUtils.xyToTemperatureMirek(xy));

        xy[0] = 0.1691f;
        xy[1] = 0.0441f;
        // 605
        assertEquals(568, HueUtils.xyToTemperatureMirek(xy));

    }

    @Test
    public void testRunningAverager() {
        RunningFPSAverager avger = new RunningFPSAverager();
        long oneSecInNanos = TimeUnit.SECONDS.toNanos(1);
        for (int i = 0; i < 2 * RunningFPSAverager.MAXSAMPLES; i++) {
            assertFloatEquals("Not equals", 1, avger.addSample(oneSecInNanos));
        }
        avger = new RunningFPSAverager();
        assertFloatEquals("Not equals", 1, avger.addSample(oneSecInNanos));
        assertFloatEquals("Not equals", 2.0 / 3, avger.addSample(oneSecInNanos * 2));

        avger = new RunningFPSAverager();
        assertFloatEquals("Not equals", 1, avger.addSample(oneSecInNanos));
        assertFloatEquals("Not equals", 1, avger.addSample(oneSecInNanos));
        assertFloatEquals("Not equals", 3.0 / 4, avger.addSample(oneSecInNanos * 2));

        avger = new RunningFPSAverager();
        assertFloatEquals("Not equals", 1, avger.addSample(oneSecInNanos));
        assertFloatEquals("Not equals", 1, avger.addSample(oneSecInNanos));
        assertFloatEquals("Not equals", 3.0 / 4, avger.addSample(oneSecInNanos * 2));
        assertFloatEquals("Not equals", 8.0 / 9, avger.addSample(oneSecInNanos / 2));

    }

    static double EPSILON_FACTOR = 1e-3;

    public static void assertFloatEquals(String message, double expected, double val) {
        assertFloatEquals(message, expected, val, EPSILON_FACTOR);
    }

    public static void assertFloatEquals(String message, double expected, double val, double epsilon) {
        final double absDiff = Math.abs(expected - val);
        if (absDiff > epsilon * expected) {
            fail(message + "- expected: " + expected + ", but was: " + val + " with abs diff: " + absDiff + " greater than epsilon: " + epsilon * expected);
        }
    }
}
