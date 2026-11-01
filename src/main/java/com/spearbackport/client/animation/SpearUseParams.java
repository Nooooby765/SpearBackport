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
 * stored between frames and no attack or charge state is ever guessed.
 *
 * <p>Unlike the original port this instance is <em>reusable</em>:
 * {@code SpearModelWrapper} owns one and fills it in place with
 * {@link #fill}, so an animating frame allocates nothing at all. The values
 * are identical to the old per-frame instance; only the storage is reused, and
 * it is read within the same call that filled it, on the single client render
 * thread.
 */
public final class SpearUseParams {

    /** 0 -> 1 while the spear is being raised. */
    public float raise;
    /** First half of the raise. */
    public float raiseStart;
    /** Middle of the raise. */
    public float raiseMiddle;
    /** Last fifth of the raise. */
    public float raiseEnd;
    /** 0 -> 1 over the final 20 ticks of the engaged stage. */
    public float sway;
    /** Elastic 0 -> 1 -> overshoot over the final 40 ticks of the tired stage. */
    public float lower;
    /** 0 -> 1 over the final 5 ticks of the disengaged stage. */
    public float raiseBack;
    /** Sway envelope; 0 before the sway starts and after the spear settles. */
    public float swayIntensity;
    /** Slow sine sway, already scaled by {@link #swayIntensity}. */
    public float swaySlow;
    /** Fast sine sway, already scaled by {@link #swayIntensity}. */
    public float swayFast;

    public SpearUseParams() {}

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
     * Refills this instance (no allocation) for the given tier and held time.
     *
     * @param tier     the spear tier, holding the 1.21.11 charge timings
     * @param timeHeld ticks the item has been in use, with partial ticks applied
     */
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
