package com.tycooncraft.tyc2.village;

/**
 * Direction of a {@link VillageTradeOrder} from the village's perspective.
 */
public enum OrderSide {
    /**
     * Village sells goods to the player/company: player pays IC, purchased goods deposit into
     * Cooperation Hub Outbound slots ({@code 500..999}).
     */
    VILLAGE_SELLS,

    /**
     * Village buys goods from the player/company: goods are consumed from Cooperation Hub
     * Inbound slots ({@code 0..499}), and village pays IC from {@code villageBalance}.
     */
    VILLAGE_BUYS
}
