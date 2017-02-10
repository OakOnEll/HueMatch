package com.oakonell.huematch.utils;

import java.util.concurrent.TimeUnit;

/**
 * Created by Rob on 2/2/2017.
 */

public class RunningFPSAverager extends RunningAverager {
    private final static long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    @Override
    protected double calculate() {
        return NANOS_PER_SECOND / ((double) sampleSum / numsamples);
    }

}
