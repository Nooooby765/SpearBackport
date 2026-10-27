package com.spearbackport.combat;

/**
 * Whether the local player's click became a real spear jab.
 *
 * <p>1.12.2 swings the arm from {@code Minecraft#clickMouse} no matter what the
 * click did, and the swing is decided before the item ever gets a say. A spear
 * must behave like 1.21.11 instead, where {@code Minecraft#startAttack} returns
 * before the swing when {@code cannotAttackWithItem} rejects the attack
 * ({@code MINIMUM_ATTACK_CHARGE = 1.0}) — a click that is not charged enough
 * must do nothing at all.
 *
 * <p>So the client click handlers record the decision here and
 * {@code SpearItem.onEntitySwing} reads it back for the swing that immediately
 * follows. It is a single int: no map, no per-tick work, no allocation.
 */
public final class SpearJabState {

    /** No jab has been approved, and no tick can equal this. */
    private static final int NONE = Integer.MIN_VALUE;

    private static int approvedTick = NONE;

    private SpearJabState() {}

    /** The click at {@code tick} is a real jab, so its swing must play. */
    public static void approve(int tick) {
        approvedTick = tick;
    }

    /** The click at this tick was refused, so its swing must be dropped. */
    public static void refuse() {
        approvedTick = NONE;
    }

    /** True only for the swing that belongs to an approved click. */
    public static boolean isApproved(int tick) {
        return approvedTick == tick;
    }
}
