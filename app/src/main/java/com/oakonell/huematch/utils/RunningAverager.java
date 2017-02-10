package com.oakonell.huematch.utils;

import java.util.concurrent.TimeUnit;

/**
 * Created by Rob on 2/9/2017.
 */

public class RunningAverager {
    public static final int MAXSAMPLES = 100;

    protected long sampleSum = 0;
    protected int numsamples = 0;

    private int sampleIndex = 0;
    private final long samples[] = new long[MAXSAMPLES];
    private double currentAvg;


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
        currentAvg = calculate();
        return currentAvg;
    }

    protected double calculate() {
        return (double) sampleSum / numsamples;
    }

    public double getAverage() {
        return currentAvg;
    }
}
