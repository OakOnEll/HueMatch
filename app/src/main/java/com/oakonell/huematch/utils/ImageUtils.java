package com.oakonell.huematch.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.util.Size;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Rob on 1/20/2017.
 */

public class ImageUtils {
    @SuppressWarnings("FieldCanBeLocal")
    private static final String TAG = "ImageUtils";
    private static final int NO_IMAGE_BRIGHTNESS = 0;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    public static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    public static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    public static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                         int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    public static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    //    /*
//Calculates the estimated brightness of an Android Bitmap.
//pixelSpacing tells how many pixels to skip each pixel. Higher values result in better performance, but a more rough estimate.
//When pixelSpacing = 1, the method actually calculates the real average brightness, not an estimate.
//This is what the calculateBrightness() shorthand is for.
//Do not use values for pixelSpacing that are smaller than 1.
//*/
//    public int calculateBrightnessEstimate(android.graphics.Bitmap bitmap, int pixelSpacing) {
////https://gist.github.com/bodyflex/b1d772caf76cdc0c11e2
//        int R = 0;
//        int G = 0;
//        int B = 0;
//        int height = bitmap.getHeight();
//        int width = bitmap.getWidth();
//        int n = 0;
//        int[] pixels = new int[width * height];
//        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
//        for (int i = 0; i < pixels.length; i += pixelSpacing) {
//            int color = pixels[i];
//            R += Color.red(color);
//            G += Color.green(color);
//            B += Color.blue(color);
//            n++;
//        }
//        return (R + B + G) / (n * 3);
//    }
//
    private static class ColorBuckets {
        int redBucket;
        int greenBucket;
        int blueBucket;
    }

    public static Map<ScreenSection, ColorAndBrightness> getDominantColor(Bitmap bitmap) {
        //http://stackoverflow.com/questions/12408431/how-can-i-get-the-average-colour-of-an-image
        if (null == bitmap) {
            Map<ScreenSection, ColorAndBrightness> result = new HashMap<>();
            for (ScreenSection each : ScreenSection.values()) {
                result.put(each, new ColorAndBrightness(Color.TRANSPARENT, NO_IMAGE_BRIGHTNESS));
            }
            return result;
        }

        ColorBuckets[] bucketsBySectionOrdinal = new ColorBuckets[ScreenSection.values().length];

        int pixelCount = bitmap.getWidth() * bitmap.getHeight();
        int[] pixels = new int[pixelCount];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        for (ScreenSection each : ScreenSection.values()) {
            bucketsBySectionOrdinal[each.ordinal()] = new ColorBuckets();
        }


        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final int halfWidth = width / 2;
        final int halfHeight = height / 2;
        for (int y = 0, h = height; y < h; y++) {
            for (int x = 0, w = width; x < w; x++) {
                int color = pixels[x + y * w]; // x + y * width

                ScreenSection[] sections;
                if (x < halfWidth) {
                    if (y < halfHeight) {
                        sections = UPPER_LEFT_SECTIONS;
                    } else {
                        sections = LOWER_LEFT_SECTIONS;
                    }
                } else {
                    if (y < halfHeight) {
                        sections = UPPER_RIGHT_SECTIONS;
                    } else {
                        sections = LOWER_RIGHT_SECTIONS;
                    }
                }

                int red = (color >> 16) & 0xFF;
                int green = (color >> 8) & 0xFF;
                int blue = (color & 0xFF);
                for (int i = 0; i < NUM_SECTIONS_FOR_PIXEL; i++) {
                    ScreenSection each = sections[i];

                    ColorBuckets buckets = bucketsBySectionOrdinal[each.ordinal()];
                    buckets.redBucket += red;
                    buckets.greenBucket += green;
                    buckets.blueBucket += blue;
                }

            }
        }


        Map<ScreenSection, ColorAndBrightness> result = new HashMap<>();
        for (ScreenSection each : ScreenSection.values()) {
            final ColorBuckets buckets = bucketsBySectionOrdinal[each.ordinal()];
            final int red = buckets.redBucket;
            final int green = buckets.greenBucket;
            final int blue = buckets.blueBucket;
            int numPixelsInSection = pixelCount / each.getNum();

            result.put(each, new ColorAndBrightness(Color.rgb(
                    red / numPixelsInSection,
                    green / numPixelsInSection,
                    blue / numPixelsInSection),
                    (red + green + blue) / (3 * numPixelsInSection)
            ));
        }
        return result;
    }

    private static final int NUM_SECTIONS_FOR_PIXEL = 4;
    private static ScreenSection[] UPPER_LEFT_SECTIONS = new ScreenSection[NUM_SECTIONS_FOR_PIXEL];
    private static ScreenSection[] UPPER_RIGHT_SECTIONS = new ScreenSection[NUM_SECTIONS_FOR_PIXEL];
    private static ScreenSection[] LOWER_LEFT_SECTIONS = new ScreenSection[NUM_SECTIONS_FOR_PIXEL];
    private static ScreenSection[] LOWER_RIGHT_SECTIONS = new ScreenSection[NUM_SECTIONS_FOR_PIXEL];

    static {
        UPPER_LEFT_SECTIONS[0] = ScreenSection.OVERALL;
        UPPER_LEFT_SECTIONS[1] = ScreenSection.UPPER;
        UPPER_LEFT_SECTIONS[2] = ScreenSection.UPPER_LEFT;
        UPPER_LEFT_SECTIONS[3] = ScreenSection.LEFT;

        UPPER_RIGHT_SECTIONS[0] = ScreenSection.OVERALL;
        UPPER_RIGHT_SECTIONS[1] = ScreenSection.UPPER;
        UPPER_RIGHT_SECTIONS[2] = ScreenSection.UPPER_RIGHT;
        UPPER_RIGHT_SECTIONS[3] = ScreenSection.RIGHT;

        LOWER_LEFT_SECTIONS[0] = ScreenSection.OVERALL;
        LOWER_LEFT_SECTIONS[1] = ScreenSection.LOWER;
        LOWER_LEFT_SECTIONS[2] = ScreenSection.LOWER_LEFT;
        LOWER_LEFT_SECTIONS[3] = ScreenSection.LEFT;

        LOWER_RIGHT_SECTIONS[0] = ScreenSection.OVERALL;
        LOWER_RIGHT_SECTIONS[1] = ScreenSection.LOWER;
        LOWER_RIGHT_SECTIONS[2] = ScreenSection.LOWER_RIGHT;
        LOWER_RIGHT_SECTIONS[3] = ScreenSection.RIGHT;
    }

    public static class ColorAndBrightness {
        private final int color;
        private final int brightness;

        public ColorAndBrightness(int color, int brightness) {
            this.color = color;
            this.brightness = brightness;
        }

        public int getColor() {
            return color;
        }

        public int getBrightness() {
            return brightness;
        }
    }

}
