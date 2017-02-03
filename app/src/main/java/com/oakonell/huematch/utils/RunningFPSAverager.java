package com.oakonell.huematch.utils;

import java.util.concurrent.TimeUnit;

/**
 * Created by Rob on 2/2/2017.
 */

public class RunningFPSAverager {
    public static final int MAXSAMPLES = 100;
    private final static long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private int sampleIndex = 0;
    private long sampleSum = 0;
    private final long samples[] = new long[MAXSAMPLES];
    private double currentAvg;
    private int numsamples = 0;

/* need to zero out the samples array before starting */
/* average will ramp up until the buffer is full */
/* returns average ticks per frame over the MAXSAMPLES last frames */

    public double addSample(long timeNs) {
        sampleSum -= samples[sampleIndex];  /* subtract value falling off */
        sampleSum += timeNs;              /* add new value */
        samples[sampleIndex] = timeNs;   /* save new value so it can be subtracted later */
        if (++sampleIndex == MAXSAMPLES)    /* inc buffer index */
            sampleIndex = 0;

        if (numsamples < MAXSAMPLES) numsamples++;

    /* return average */
        currentAvg = NANOS_PER_SECOND / ((double) sampleSum / numsamples);

        return currentAvg;
    }

    public double getAverage() {
        return currentAvg;
    }
}
