package com.dungeonscore.boss;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Ravager;

import org.bukkit.entity.Stray;
import org.bukkit.entity.WitherSkeleton;

import java.util.List;

/**
  * The dungeon bosses. Crown and weapon may be null (the boss then wears/holds nothing).
 * Drops: item id, min, max, chance (0-1).
 */
public enum BossType {

    LICH_KING("lich_king", "Lich King", WitherSkeleton.class, NamedTextColor.DARK_PURPLE, BossBar.Color.PURPLE,
            300, 10, "lich_crown", "lich_scepter", List.of(
            new Drop("lich_soul", 1, 2, 1.0),
            new Drop("bone_shard", 4, 8, 1.0),
            new Drop("lich_scepter", 1, 1, 0.25),
            new Drop("necro_staff", 1, 1, 0.20),
            new Drop("crypt_warden_helmet", 1, 1, 0.15),
            new Drop("crypt_warden_chestplate", 1, 1, 0.15),
            new Drop("crypt_warden_leggings", 1, 1, 0.15),
            new Drop("crypt_warden_boots", 1, 1, 0.15),
            new Drop("lich_crown", 1, 1, 0.10))),

    MOLTEN_TYRANT("molten_tyrant", "Molten Tyrant", PiglinBrute.class, NamedTextColor.GOLD, BossBar.Color.RED,
            350, 12, "molten_crown", "thunder_hammer", List.of(
            new Drop("molten_heart", 1, 2, 1.0),
            new Drop("ember_core", 3, 6, 1.0),
            new Drop("inferno_longbow", 1, 1, 0.25),
            new Drop("ember_blade", 1, 1, 0.20),
            new Drop("thunder_hammer", 1, 1, 0.10),
            new Drop("infernal_helmet", 1, 1, 0.15),
            new Drop("infernal_chestplate", 1, 1, 0.15),
            new Drop("infernal_leggings", 1, 1, 0.15),
            new Drop("infernal_boots", 1, 1, 0.15),
            new Drop("molten_crown", 1, 1, 0.10))),

    FROST_QUEEN("frost_queen", "Frost Queen", Stray.class, NamedTextColor.AQUA, BossBar.Color.BLUE,
            280, 8, "frost_crown", "frostfang_bow", List.of(
            new Drop("glacial_heart", 1, 2, 1.0),
            new Drop("frost_crystal", 3, 6, 1.0),
            new Drop("frost_staff", 1, 1, 0.25),
            new Drop("frostbite_dagger", 1, 1, 0.20),
            new Drop("frostfang_bow", 1, 1, 0.15),
            new Drop("frostforged_helmet", 1, 1, 0.15),
            new Drop("frostforged_chestplate", 1, 1, 0.15),
            new Drop("frostforged_leggings", 1, 1, 0.15),
            new Drop("frostforged_boots", 1, 1, 0.15),
            new Drop("frost_crown", 1, 1, 0.10))),

    /** Overgrown City boss. Uses only existing items as loot. */
    MOSSHEART_BEHEMOTH("mossheart_behemoth", "Mossheart Behemoth", Ravager.class, NamedTextColor.GREEN,
            BossBar.Color.GREEN, 400, 12, null, null, List.of(
            new Drop("greater_health_potion", 1, 2, 1.0),
            new Drop("health_potion", 2, 4, 1.0),
            new Drop("wind_horn", 1, 1, 0.30),
            new Drop("staff_of_renewal", 1, 1, 0.25),
            new Drop("soulreaper_scythe", 1, 1, 0.15),
            new Drop("totem_of_shielding", 1, 1, 0.15),
            new Drop("void_greatsword", 1, 1, 0.05)));

    public record Drop(String item, int min, int max, double chance) {
    }

    private final String id;
    private final String displayName;
    private final Class<? extends Mob> entityClass;
    private final NamedTextColor color;
    private final BossBar.Color barColor;
    private final double health;
    private final double damage;
    private final String crown;
    private final String weapon;
    private final List<Drop> drops;

    BossType(String id, String displayName, Class<? extends Mob> entityClass, NamedTextColor color,
             BossBar.Color barColor, double health, double damage, String crown, String weapon, List<Drop> drops) {
        this.id = id;
        this.displayName = displayName;
        this.entityClass = entityClass;
        this.color = color;
        this.barColor = barColor;
        this.health = health;
        this.damage = damage;
        this.crown = crown;
        this.weapon = weapon;
        this.drops = drops;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Class<? extends Mob> entityClass() {
        return entityClass;
    }

    public NamedTextColor color() {
        return color;
    }

    public BossBar.Color barColor() {
        return barColor;
    }

    public double health() {
        return health;
    }

    public double damage() {
        return damage;
    }

    public String crown() {
        return crown;
    }

    public String weapon() {
        return weapon;
    }

    public List<Drop> drops() {
        return drops;
    }

    public static BossType byId(String id) {
        for (BossType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
