package com.spearbackport.client.animation;

import javax.vecmath.Matrix4f;

/**
 * Builds the item pose for the spear animations, one-to-one with the
 * {@code PoseStack} calls in 1.21.11's {@code SpearAnimations}.
 *
 * <p>1.12.2 hands a {@code IBakedModel} a plain {@link Matrix4f} that Forge
 * multiplies onto the current GL matrix, so every {@code translate} /
 * {@code rotateAround} below is a <em>right</em> multiplication, exactly like
 * {@code PoseStack}. All the rotation math is inlined on the raw matrix fields:
 * the only allocation is the matrix the caller asks for.
 *
 * <p>The matrices are applied <em>before</em> the item's own display transform,
 * which matches 1.21.11 where the animation runs before {@code item.submit}.
 */
public final class SpearPose {

    /** {@code KineticWeapon.forwardMovement} of the 1.21.11 spear. */
    public static final float FORWARD_MOVEMENT = 0.38F;

    /** Local pivot the third person use animation rotates around. */
    private static final float USE_PIVOT_Y = -0.03125F;
    /** Local pivot the third person stab rotates around. */
    private static final float ATTACK_PIVOT_Y = -0.125F;
    /** Depth of the spear model inside the hand frame. */
    private static final float ITEM_IN_HAND_DEPTH = 0.125F;

    private SpearPose() {}

    /**
     * A new identity pose.
     *
     * <p>{@code javax.vecmath.Matrix4f} has no identity default constructor: its
     * no-argument constructor zeroes every element, so a pose has to be
     * initialised explicitly. This factory is the only place that happens.
     */
    public static Matrix4f newPose() {
        Matrix4f pose = new Matrix4f();
        pose.setIdentity();
        return pose;
    }

    // ------------------------------------------------------------------
    // First person
    // ------------------------------------------------------------------

    /** {@code SpearAnimations.firstPersonUse}, the right-click charge pose. */
    public static void applyFirstPersonUse(Matrix4f m, SpearUseParams p, int invert) {
        translate(m,
                invert * (0.15F * p.raise - 0.05F * p.raiseEnd - 0.1F * p.sway + 0.005F * p.swaySlow),
                -0.075F * p.raise + 0.075F * p.raiseMiddle + 0.01F * p.swayFast,
                0.05F * p.raiseStart - 0.05F * p.raiseEnd + 0.005F * p.swaySlow);
        // poseStack.rotateAround(Axis.XP.rotationDegrees(...), 0, 0.1, 0)
        rotateAroundX(m, -65.0F * SpearEase.inOutBack(p.raise) - 35.0F * p.lower
                + 100.0F * p.raiseBack - 0.5F * p.swayFast, 0.0F, 0.1F, 0.0F);
        // poseStack.rotateAround(Axis.YN.rotationDegrees(...), invert * 0.15, 0, 0)
        rotateAroundY(m, -invert * (-90.0F * SpearEase.progress(p.raise, 0.5F, 0.55F)
                        + 90.0F * p.sway + 2.0F * p.swaySlow),
                invert * 0.15F, 0.0F, 0.0F);
    }

    /** {@code SpearAnimations.firstPersonAttack}, the left-click stab. */
    public static void applyFirstPersonAttack(Matrix4f m, float swing, int invert) {
        float prepare = SpearEase.inOutSine(SpearEase.progress(swing, 0.0F, 0.05F));
        float attack = SpearEase.outBack(SpearEase.progress(swing, 0.05F, 0.2F));
        float retract = SpearEase.inOutExpo(SpearEase.progress(swing, 0.4F, 1.0F));

        translate(m,
                invert * 0.1F * (prepare - attack),
                -0.075F * (prepare - retract),
                0.65F * (prepare - attack));
        // poseStack.rotateDegrees(Axis.XP, -70 * (prepare - retract))
        rotateX(m, -70.0F * (prepare - retract));
        translate(m, 0.0F, 0.0F, -0.25F * (retract - attack));
    }

    // ------------------------------------------------------------------
    // Third person
    // ------------------------------------------------------------------

    /** {@code SpearAnimations.thirdPersonUseItem}, the right-click charge pose. */
    public static void applyThirdPersonUse(Matrix4f m, SpearUseParams p, int invert, float swing,
                                           float forwardMovement) {
        float attack = SpearEase.inQuad(SpearEase.progress(swing, 0.05F, 0.2F));
        float retract = SpearEase.inOutExpo(SpearEase.progress(swing, 0.4F, 1.0F));
        float raiseModified = 1.0F - SpearEase.outBack(1.0F - p.raise);

        translate(m, 0.0F, 0.0F,
                -forwardMovement * (raiseModified - p.raiseBack));
        // poseStack.rotateAround(Axis.XN.rotationDegrees(...), 0, -1/32, 1/8)
        rotateAroundX(m, -(70.0F * (p.raise - p.raiseBack) - 40.0F * (attack - retract)),
                0.0F, USE_PIVOT_Y, ITEM_IN_HAND_DEPTH);
        // poseStack.rotateAround(Axis.YP.rotationDegrees(...), 0, 0, 1/8)
        rotateAroundY(m, invert * 90.0F * (p.raise - p.sway + 3.0F * retract + attack),
                0.0F, 0.0F, ITEM_IN_HAND_DEPTH);
    }

    /** {@code SpearAnimations.thirdPersonAttackItem}, the left-click stab. */
    public static void applyThirdPersonAttack(Matrix4f m, float swing, float forwardMovement) {
        if (swing <= 0.0F) {
            return;
        }
        float attack = SpearEase.inQuad(SpearEase.progress(swing, 0.05F, 0.2F));
        float retract = SpearEase.inOutExpo(SpearEase.progress(swing, 0.4F, 1.0F));

        // poseStack.rotateAround(Axis.XN.rotationDegrees(...), 0, -1/8, 1/8)
        rotateAroundX(m, -70.0F * (attack - retract), 0.0F, ATTACK_PIVOT_Y, ITEM_IN_HAND_DEPTH);
        translate(m, 0.0F, forwardMovement * (attack - retract), 0.0F);
    }

    // ------------------------------------------------------------------
    // Matrix primitives, matching PoseStack
    // ------------------------------------------------------------------

    /** {@code m = m * T(x, y, z)}. */
    public static void translate(Matrix4f m, float x, float y, float z) {
        m.m03 += m.m00 * x + m.m01 * y + m.m02 * z;
        m.m13 += m.m10 * x + m.m11 * y + m.m12 * z;
        m.m23 += m.m20 * x + m.m21 * y + m.m22 * z;
        m.m33 += m.m30 * x + m.m31 * y + m.m32 * z;
    }

    /** {@code m = m * Rx(degrees)}, i.e. {@code Axis.XP}. */
    public static void rotateX(Matrix4f m, float degrees) {
        double radians = degrees * (Math.PI / 180.0D);
        float s = (float) Math.sin(radians);
        float c = (float) Math.cos(radians);

        float c1 = m.m01;
        float c2 = m.m02;
        m.m01 = c * c1 + s * c2;
        m.m02 = c * c2 - s * c1;

        c1 = m.m11;
        c2 = m.m12;
        m.m11 = c * c1 + s * c2;
        m.m12 = c * c2 - s * c1;

        c1 = m.m21;
        c2 = m.m22;
        m.m21 = c * c1 + s * c2;
        m.m22 = c * c2 - s * c1;

        c1 = m.m31;
        c2 = m.m32;
        m.m31 = c * c1 + s * c2;
        m.m32 = c * c2 - s * c1;
    }

    /** {@code m = m * Ry(degrees)}, i.e. {@code Axis.YP}. */
    public static void rotateY(Matrix4f m, float degrees) {
        double radians = degrees * (Math.PI / 180.0D);
        float s = (float) Math.sin(radians);
        float c = (float) Math.cos(radians);

        float c0 = m.m00;
        float c2 = m.m02;
        m.m00 = c * c0 - s * c2;
        m.m02 = s * c0 + c * c2;

        c0 = m.m10;
        c2 = m.m12;
        m.m10 = c * c0 - s * c2;
        m.m12 = s * c0 + c * c2;

        c0 = m.m20;
        c2 = m.m22;
        m.m20 = c * c0 - s * c2;
        m.m22 = s * c0 + c * c2;

        c0 = m.m30;
        c2 = m.m32;
        m.m30 = c * c0 - s * c2;
        m.m32 = s * c0 + c * c2;
    }

    /** {@code PoseStack#rotateAround(X, degrees, ox, oy, oz)}. */
    public static void rotateAroundX(Matrix4f m, float degrees, float ox, float oy, float oz) {
        translate(m, ox, oy, oz);
        rotateX(m, degrees);
        translate(m, -ox, -oy, -oz);
    }

    /** {@code PoseStack#rotateAround(Y, degrees, ox, oy, oz)}. */
    public static void rotateAroundY(Matrix4f m, float degrees, float ox, float oy, float oz) {
        translate(m, ox, oy, oz);
        rotateY(m, degrees);
        translate(m, -ox, -oy, -oz);
    }
}
