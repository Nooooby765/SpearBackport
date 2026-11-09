package com.spearbackport.client.animation;

import com.spearbackport.item.SpearMaterial;

/**
 * The charge animation state, ported from {@code SpearAnimations.UseParams}; every
 * field is a pure function of the held time, and {@link #fill} refills the
 * instance so an animating frame allocates nothing.
 */
public final class SpearUseParams {

    public float raise;
    public float raiseStart;
    public float raiseMiddle;
    public float raiseEnd;
    public float sway;
    public float lower;
    public float raiseBack;
    public float swayIntensity;
    public float swaySlow;
    public float swayFast;

    public SpearUseParams() {}

    /**
     * True when every translate and rotate is a no-op, so the cached display
     * transform can be reused as is.
     */
    public boolean isNeutral() {
        return raise <= 0.0F || raiseBack >= 1.0F;
    }

    /** Refills this instance in place for the given tier and held time. */
    public SpearUseParams fill(SpearMaterial tier, float timeHeld) {
        int finishRaising = tier.activationDelay;
        int finishSwaying = finishRaising + tier.engagedDuration;
        int startSwaying = finishSwaying - 20;
        int finishLowering = finishRaising + tier.engagedDuration + tier.tiredDuration;
        int startLowering = finishLowering - 40;
        int finishRaisingBack = tier.expiresAt;

        this.raise = SpearEase.progress(timeHeld, 0.0F, finishRaising);
        this.raiseStart = SpearEase.progress(raise, 0.0F, 0.5F);
        this.raiseMiddle = SpearEase.progress(raise, 0.5F, 0.8F);
        this.raiseEnd = SpearEase.progress(raise, 0.8F, 1.0F);
        this.sway = SpearEase.progress(timeHeld, startSwaying, finishSwaying);
        this.lower = SpearEase.outCubic(SpearEase.inOutElastic(
                SpearEase.progress(timeHeld - 20.0F, startLowering, finishLowering)));
        this.raiseBack = SpearEase.progress(timeHeld, finishRaisingBack - 5, finishRaisingBack);

        this.swayIntensity = 2.0F * SpearEase.outCirc(sway) - 2.0F * SpearEase.inCirc(raiseBack);
        this.swaySlow = SpearEase.sinDegrees(timeHeld * 19.0F) * swayIntensity;
        this.swayFast = SpearEase.sinDegrees(timeHeld * 30.0F) * swayIntensity;
        return this;
    }
}
