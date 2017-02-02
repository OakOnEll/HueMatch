package com.oakonell.huematch.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.util.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
    public static ColorAndBrightness getDominantColor(Bitmap bitmap) {
        //http://stackoverflow.com/questions/12408431/how-can-i-get-the-average-colour-of-an-image
        if (null == bitmap) return new ColorAndBrightness(Color.TRANSPARENT, NO_IMAGE_BRIGHTNESS);

        int redBucket = 0;
        int greenBucket = 0;
        int blueBucket = 0;
        int alphaBucket = 0;

        boolean hasAlpha = bitmap.hasAlpha();
        int pixelCount = bitmap.getWidth() * bitmap.getHeight();
        int[] pixels = new int[pixelCount];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        for (int y = 0, h = bitmap.getHeight(); y < h; y++) {
            for (int x = 0, w = bitmap.getWidth(); x < w; x++) {
                int color = pixels[x + y * w]; // x + y * width
                redBucket += (color >> 16) & 0xFF; // Color.red
                greenBucket += (color >> 8) & 0xFF; // Color.greed
                blueBucket += (color & 0xFF); // Color.blue
                if (hasAlpha) alphaBucket += (color >>> 24); // Color.alpha
            }
        }

//        return new ColorAndBrightness(Color.argb(
//                (hasAlpha) ? (alphaBucket / pixelCount) : 255,
//                redBucket / pixelCount,
//                greenBucket / pixelCount,
//                blueBucket / pixelCount),
//                (redBucket + greenBucket + blueBucket) / (3 * pixelCount)
//        );
        return new ColorAndBrightness(Color.rgb(
                redBucket / pixelCount,
                greenBucket / pixelCount,
                blueBucket / pixelCount),
                (redBucket + greenBucket + blueBucket) / (3 * pixelCount)
        );
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
