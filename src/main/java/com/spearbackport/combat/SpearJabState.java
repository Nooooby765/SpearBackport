package com.spearbackport.combat;

/**
 * Whether the local player's click became a real jab. 1.12.2 swings the arm from
 * {@code clickMouse} before the item gets a say; 1.21.11 returns early when
 * {@code cannotAttackWithItem} rejects it ({@code MINIMUM_ATTACK_CHARGE = 1.0}).
 */
public final class SpearJabState {

    /** No jab has been approved, and no tick can equal this. */
    private static final int NONE = Integer.MIN_VALUE;

    private static int approvedTick = NONE;

    private SpearJabState() {}

    public static void approve(int tick) {
        approvedTick = tick;
    }

    public static void refuse() {
        approvedTick = NONE;
    }

    public static boolean isApproved(int tick) {
        return approvedTick == tick;
    }
}
