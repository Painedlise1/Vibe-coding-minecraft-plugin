package com.dungeonscore;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Persistent-data keys shared between features (all under the "dungeonscore" namespace). */
public final class Keys {

    /** Custom item id on an ItemStack. */
    public static NamespacedKey ITEM_ID;
    /** Custom item id of the bow/staff that fired a projectile. */
    public static NamespacedKey PROJECTILE;
    /** Boss type id on a boss entity. */
    public static NamespacedKey BOSS;
    /** Marks mobs summoned by a boss. */
    public static NamespacedKey MINION;
    /** Dungeon mob type id. */
    public static NamespacedKey DUNGEON_MOB;
    /** Run id a dungeon mob or dungeon boss belongs to (stale ones are removed). */
    public static NamespacedKey DUNGEON_RUN;
    /** Encounter (cell) a dungeon mob was spawned for. */
    public static NamespacedKey ENCOUNTER;
    /** Where a player came from before entering a dungeon (stored on the player). */
    public static NamespacedKey DUNGEON_RETURN;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        ITEM_ID = new NamespacedKey(plugin, "item_id");
        PROJECTILE = new NamespacedKey(plugin, "projectile");
        BOSS = new NamespacedKey(plugin, "boss");
        MINION = new NamespacedKey(plugin, "minion");
        DUNGEON_MOB = new NamespacedKey(plugin, "dungeon_mob");
        DUNGEON_RUN = new NamespacedKey(plugin, "dungeon_run");
        ENCOUNTER = new NamespacedKey(plugin, "encounter");
        DUNGEON_RETURN = new NamespacedKey(plugin, "dungeon_return");
    }
}
