package com.spearbackport.client.animation;

/**
 * 1.21.11's {@code Ease} and {@code Mth} primitives, ported one function at a
 * time; {@code Mth.sin}/{@code cos} are plain {@link Math} there too, so no 1.12.2
 * lookup table. Stateless.
 */
public final class SpearEase {

    private static final float DEG_TO_RAD = (float) Math.PI / 180.0F;

    private SpearEase() {}

    /** {@code Mth.clamp(Mth.inverseLerp(time, start, end), 0, 1)}. */
    public static float progress(float time, float start, float end) {
        float span = end - start;
        if (span <= 0.0F) {
            return time >= end ? 1.0F : 0.0F;
        }
        float value = (time - start) / span;
        return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
    }

    public static float inQuad(float x) {
        return x * x;
    }

    public static float inCirc(float x) {
        return 1.0F - (float) Math.sqrt(1.0F - x * x);
    }

    public static float outCirc(float x) {
        float d = x - 1.0F;
        return (float) Math.sqrt(1.0F - d * d);
    }

    public static float outCubic(float x) {
        float d = 1.0F - x;
        return 1.0F - d * d * d;
    }

    public static float outBack(float x) {
        float d = x - 1.0F;
        return 1.0F + 2.70158F * d * d * d + 1.70158F * d * d;
    }

    public static float inOutSine(float x) {
        return -((float) Math.cos(Math.PI * x) - 1.0F) / 2.0F;
    }

    public static float inOutExpo(float x) {
        if (x < 0.5F) {
            return x == 0.0F ? 0.0F : (float) (Math.pow(2.0D, 20.0D * x - 10.0D) / 2.0D);
        }
        return x == 1.0F ? 1.0F : (float) ((2.0D - Math.pow(2.0D, -20.0D * x + 10.0D)) / 2.0D);
    }

    public static float inOutBack(float x) {
        if (x < 0.5F) {
            return 4.0F * x * x * (7.189819F * x - 2.5949094F) / 2.0F;
        }
        float d = 2.0F * x - 2.0F;
        return (d * d * (3.5949094F * d + 2.5949094F) + 2.0F) / 2.0F;
    }

    public static float inOutElastic(float x) {
        if (x == 0.0F) {
            return 0.0F;
        }
        if (x == 1.0F) {
            return 1.0F;
        }
        double sin = Math.sin((20.0D * x - 11.125D) * 1.3962634801864624D);
        if (x < 0.5F) {
            return (float) (-(Math.pow(2.0D, 20.0D * x - 10.0D) * sin) / 2.0D);
        }
        return (float) (Math.pow(2.0D, -20.0D * x + 10.0D) * sin / 2.0D + 1.0D);
    }

    public static float sinDegrees(float degrees) {
        return (float) Math.sin(degrees * DEG_TO_RAD);
    }
}
