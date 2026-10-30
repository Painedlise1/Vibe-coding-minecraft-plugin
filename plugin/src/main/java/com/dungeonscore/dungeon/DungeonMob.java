package com.dungeonscore.dungeon;

import com.dungeonscore.Keys;
import com.dungeonscore.item.ItemRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Bogged;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Witch;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/** The Overgrown City's mobs: vanilla creatures with their own names, stats and gear (no custom items). */
public enum DungeonMob {

    VINE_HUSK("vine_husk", "Vine-Choked Husk", Zombie.class, 26, 4, 0.25, 40),
    MOSS_ARCHER("moss_archer", "Moss Archer", Bogged.class, 22, 3, 0.25, 25),
    THORN_CRAWLER("thorn_crawler", "Thorn Crawler", CaveSpider.class, 14, 3, 0.33, 25),
    SPORE_SHAMAN("spore_shaman", "Spore Shaman", Witch.class, 30, 2, 0.25, 10),
    ROOTBOUND_BRUTE("rootbound_brute", "Rootbound Brute", Zombie.class, 70, 9, 0.2, 0);

    private static final TextColor NAME_COLOR = TextColor.color(0x7FBF4D);
    private static final Color MOSS_GREEN = Color.fromRGB(0x4A6B2A);
    private static final Color BARK_BROWN = Color.fromRGB(0x5C4326);
    private static final List<Material> HUSK_HEADS = List.of(Material.AZALEA_LEAVES,
            Material.FLOWERING_AZALEA_LEAVES, Material.MOSS_BLOCK);

    private final String id;
    private final String displayName;
    private final Class<? extends Mob> entityClass;
    private final double health;
    private final double damage;
    private final double speed;
    private final int weight;

    DungeonMob(String id, String displayName, Class<? extends Mob> entityClass, double health, double damage,
               double speed, int weight) {
        this.id = id;
        this.displayName = displayName;
        this.entityClass = entityClass;
        this.health = health;
        this.damage = damage;
        this.speed = speed;
        this.weight = weight;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static DungeonMob byId(String id) {
        for (DungeonMob mob : values()) {
            if (mob.id.equals(id)) {
                return mob;
            }
        }
        return null;
    }

    /** Weighted random pick of the regular mobs (brutes only appear in elite encounters). */
    public static DungeonMob pick(Random random) {
        int total = 0;
        for (DungeonMob m : values()) {
            total += m.weight;
        }
        int roll = random.nextInt(total);
        for (DungeonMob m : values()) {
            roll -= m.weight;
            if (roll < 0) {
                return m;
            }
        }
        return VINE_HUSK;
    }

    /** Type id of a dungeon mob entity, or null. */
    public static DungeonMob of(LivingEntity entity) {
        String id = entity.getPersistentDataContainer().get(Keys.DUNGEON_MOB, PersistentDataType.STRING);
        return id == null ? null : byId(id);
    }

    public Mob spawn(Location at, long runId, long encounter) {
        return at.getWorld().spawn(at, entityClass, mob -> configure(mob, runId, encounter));
    }

    private void configure(Mob mob, long runId, long encounter) {
        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        pdc.set(Keys.DUNGEON_MOB, PersistentDataType.STRING, id);
        pdc.set(Keys.DUNGEON_RUN, PersistentDataType.LONG, runId);
        pdc.set(Keys.ENCOUNTER, PersistentDataType.LONG, encounter);

        mob.customName(Component.text(displayName, this == ROOTBOUND_BRUTE ? NamedTextColor.DARK_GREEN : NAME_COLOR));
        mob.setCustomNameVisible(false);
        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(true);
        mob.setCanPickupItems(false);
        if (mob instanceof Ageable ageable) {
            ageable.setAdult();
        }

        setBase(mob, Attribute.MAX_HEALTH, health);
        mob.setHealth(health);
        setBase(mob, Attribute.ATTACK_DAMAGE, damage);
        setBase(mob, Attribute.MOVEMENT_SPEED, speed);
        setBase(mob, Attribute.FOLLOW_RANGE, 28);
        setBase(mob, Attribute.SPAWN_REINFORCEMENTS, 0);

        EntityEquipment eq = mob.getEquipment();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        switch (this) {
            case VINE_HUSK -> {
                eq.clear();
                if (random.nextDouble() < 0.75) {
                    eq.setHelmet(new ItemStack(HUSK_HEADS.get(random.nextInt(HUSK_HEADS.size()))));
                }
                eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, MOSS_GREEN));
                if (random.nextBoolean()) {
                    eq.setLeggings(leather(Material.LEATHER_LEGGINGS, BARK_BROWN));
                }
            }
            case MOSS_ARCHER -> eq.setItemInMainHand(new ItemStack(Material.BOW));
            case ROOTBOUND_BRUTE -> {
                eq.clear();
                eq.setHelmet(new ItemStack(Material.MOSSY_COBBLESTONE));
                eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, BARK_BROWN));
                eq.setLeggings(leather(Material.LEATHER_LEGGINGS, MOSS_GREEN));
                eq.setBoots(leather(Material.LEATHER_BOOTS, BARK_BROWN));
                setBase(mob, Attribute.SCALE, 1.4);
                setBase(mob, Attribute.ARMOR, 6);
                setBase(mob, Attribute.KNOCKBACK_RESISTANCE, 0.8);
            }
            default -> { }
        }
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
    }

    /** Replaces vanilla drops with dungeon loot (existing items only). */
    public void applyDrops(EntityDeathEvent event, ItemRegistry items) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<ItemStack> drops = event.getDrops();
        drops.clear();
        event.setDroppedExp(this == ROOTBOUND_BRUTE ? 20 : 6);
        double potion = this == ROOTBOUND_BRUTE ? 0.35 : 0.05;
        if (random.nextDouble() < potion) {
            drops.add(items.create("health_potion", 1));
        }
        if (random.nextDouble() < (this == SPORE_SHAMAN ? 0.3 : 0.04)) {
            drops.add(items.create("mana_potion", 1));
        }
        if (this == MOSS_ARCHER && random.nextBoolean()) {
            drops.add(new ItemStack(Material.ARROW, 1 + random.nextInt(3)));
        }
        if (this == VINE_HUSK && random.nextDouble() < 0.3) {
            drops.add(new ItemStack(Material.VINE, 1 + random.nextInt(2)));
        }
    }

    private static ItemStack leather(Material material, Color color) {
        ItemStack stack = new ItemStack(material);
        if (stack.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static void setBase(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
