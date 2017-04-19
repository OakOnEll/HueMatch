package com.oakonell.huematch.utils;

import com.oakonell.huematch.R;

/**
 * Created by Rob on 4/19/2017.
 */

public enum ScreenSection {
    OVERALL(1, R.string.full_screen, R.drawable.full_screen),

    UPPER(2, R.string.upper_screen, R.drawable.upper_screen), LOWER(2, R.string.lower_screen, R.drawable.lower_screen),

    LEFT(2, R.string.left_screen, R.drawable.left_screen), RIGHT(2, R.string.right_screen, R.drawable.right_screen),

    UPPER_LEFT(4, R.string.upper_left_screen, R.drawable.upper_left), UPPER_RIGHT(4, R.string.upper_right_screen, R.drawable.upper_right),
    LOWER_LEFT(4, R.string.lower_left_screen, R.drawable.lower_left), LOWER_RIGHT(4, R.string.lower_right_screen, R.drawable.lower_right);

    private final int num;
    private final int stringResourceId;
    private final int imageResourceId;

    ScreenSection(int num, int stringResourceId, int imageResourceId) {
        this.num = num;
        this.stringResourceId = stringResourceId;
        this.imageResourceId = imageResourceId;
    }

    public int getNum() {
        return num;
    }

    public int getStringResourceId() {
        return stringResourceId;
    }


    public int getImageResourceId() {
        return imageResourceId;
    }

}
