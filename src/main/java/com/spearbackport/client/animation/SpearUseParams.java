package com.spearbackport.client.animation;

import com.spearbackport.item.SpearMaterial;

/**
 * The time-derived state of the charge (right-click) animation, a direct port of
 * {@code SpearAnimations.UseParams.fromKineticWeapon}.
 *
 * <p>1.21.11 reads these numbers from the {@code kinetic_weapon} data component;
 * here they come from the tier table, which holds exactly the same values
 * ({@code delay}, {@code dismountTime}, {@code knockbackTime} and
 * {@code damageTime} in ticks).
 *
 * <p>Every field is a pure function of the held time, so nothing has to be
 * stored between frames and no attack or charge state is ever guessed. One
 * small instance is created per animated frame and thrown away immediately; the
 * class is immutable, so it is safe to hold on to.
 */
public final class SpearUseParams {

    /** 0 -> 1 while the spear is being raised. */
    public final float raise;
    /** First half of the raise. */
    public final float raiseStart;
    /** Middle of the raise. */
    public final float raiseMiddle;
    /** Last fifth of the raise. */
    public final float raiseEnd;
    /** 0 -> 1 over the final 20 ticks of the engaged stage. */
    public final float sway;
    /** Elastic 0 -> 1 -> overshoot over the final 40 ticks of the tired stage. */
    public final float lower;
    /** 0 -> 1 over the final 5 ticks of the disengaged stage. */
    public final float raiseBack;
    /** Sway envelope; 0 before the sway starts and after the spear settles. */
    public final float swayIntensity;
    /** Slow sine sway, already scaled by {@link #swayIntensity}. */
    public final float swaySlow;
    /** Fast sine sway, already scaled by {@link #swayIntensity}. */
    public final float swayFast;

    private SpearUseParams(float raise, float raiseStart, float raiseMiddle, float raiseEnd,
                           float sway, float lower, float raiseBack, float swayIntensity,
                           float swaySlow, float swayFast) {
        this.raise = raise;
        this.raiseStart = raiseStart;
        this.raiseMiddle = raiseMiddle;
        this.raiseEnd = raiseEnd;
        this.sway = sway;
        this.lower = lower;
        this.raiseBack = raiseBack;
        this.swayIntensity = swayIntensity;
        this.swaySlow = swaySlow;
        this.swayFast = swayFast;
    }

    /**
     * True when every translate and rotate of the pose is a no-op.
     *
     * <p>That is the case before the rise begins and again once the spear has
     * settled back at the very end of the charge, which together cover the whole
     * idle part of a long hold. Both callers can then skip the pose entirely and
     * reuse the cached display transform, without allocating anything.
     */
    public boolean isNeutral() {
        return raise <= 0.0F || raiseBack >= 1.0F;
    }

    /**
     * @param tier     the spear tier, holding the 1.21.11 charge timings
     * @param timeHeld ticks the item has been in use, with partial ticks applied
     */
    public static SpearUseParams of(SpearMaterial tier, float timeHeld) {
        int finishRaising = tier.activationDelay;
        int finishSwaying = finishRaising + tier.engagedDuration;
        int startSwaying = finishSwaying - 20;
        int finishLowering = finishRaising + tier.engagedDuration + tier.tiredDuration;
        int startLowering = finishLowering - 40;
        int finishRaisingBack = tier.expiresAt;

        float raise = SpearEase.progress(timeHeld, 0.0F, finishRaising);
        float raiseStart = SpearEase.progress(raise, 0.0F, 0.5F);
        float raiseMiddle = SpearEase.progress(raise, 0.5F, 0.8F);
        float raiseEnd = SpearEase.progress(raise, 0.8F, 1.0F);
        float sway = SpearEase.progress(timeHeld, startSwaying, finishSwaying);
        float lower = SpearEase.outCubic(SpearEase.inOutElastic(
                SpearEase.progress(timeHeld - 20.0F, startLowering, finishLowering)));
        float raiseBack = SpearEase.progress(timeHeld, finishRaisingBack - 5, finishRaisingBack);

        float swayIntensity = 2.0F * SpearEase.outCirc(sway) - 2.0F * SpearEase.inCirc(raiseBack);
        float swaySlow = SpearEase.sinDegrees(timeHeld * 19.0F) * swayIntensity;
        float swayFast = SpearEase.sinDegrees(timeHeld * 30.0F) * swayIntensity;

        return new SpearUseParams(raise, raiseStart, raiseMiddle, raiseEnd, sway, lower, raiseBack,
                swayIntensity, swaySlow, swayFast);
    }
}
