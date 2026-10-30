package com.dungeonscore.item;

import com.dungeonscore.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static com.dungeonscore.item.Category.*;
import static com.dungeonscore.item.Rarity.*;

/**
 * All custom items. Every id here must have a matching entry in ITEMS / CROWNS of
 * resourcepack/generate_pack.py (and every armor set prefix in ARMOR_SETS) -
 * PackService warns on startup if they drift apart.
 */
public final class ItemRegistry {

    public static final String NAMESPACE = "dungeons";
    private static final int LORE_WIDTH = 34;

    private final Plugin plugin;
    private final Map<String, DungeonItem> items = new LinkedHashMap<>();

    public ItemRegistry(Plugin plugin) {
        this.plugin = plugin;
        registerAll();
    }

    // ------------------------------------------------------------------ //
    // Definitions (ability numbers live in ability/*Listener - keep the text in sync)
    // ------------------------------------------------------------------ //

    private void registerAll() {
        // ---- Weapons ----
        add("rusty_blade", Material.IRON_SWORD, "Rusty Blade", COMMON, WEAPON,
                "A pitted blade pulled from the crypt floor. Still sharp enough.",
                null, null, weapon(5, 1.6));
        add("crypt_cleaver", Material.IRON_SWORD, "Crypt Cleaver", UNCOMMON, WEAPON,
                "Carried by the wardens who once guarded the crypt.",
                "Undead Bane", "Deals 30% more damage to undead.", weapon(7, 1.6));
        add("ember_blade", Material.DIAMOND_SWORD, "Ember Blade", RARE, WEAPON,
                "Forged around a living ember. Warm to the touch.",
                "Ignite", "Sets targets on fire for 4 seconds.", weapon(8, 1.6));
        add("frostbite_dagger", Material.IRON_SWORD, "Frostbite Dagger", RARE, WEAPON,
                "Light and quick. The edge never stops frosting over.",
                "Frostbite", "Hits slow and chill the target.", weapon(5, 2.4));
        add("thunder_hammer", Material.NETHERITE_AXE, "Thunder Hammer", EPIC, WEAPON,
                "Every swing hums like a coming storm.",
                "Thunderstrike", "20% chance to call down lightning for 5 bonus damage.", weapon(12, 0.8));
        add("soulreaper_scythe", Material.NETHERITE_HOE, "Soulreaper Scythe", EPIC, WEAPON,
                "Its blade glows brighter with every soul it reaps.",
                "Soul Harvest", "Heals you for 20% of the damage you deal.", weapon(10, 1.2));
        add("void_greatsword", Material.NETHERITE_SWORD, "Void Greatsword", LEGENDARY, WEAPON,
                "A sliver of the void, given an edge.",
                "Void Rend", "20% chance to deal 50% bonus damage.", weapon(13, 0.9));

        // ---- Bows ----
        add("bone_bow", Material.BOW, "Bone Bow", UNCOMMON, BOW,
                "Strung with sinew and carved from giant bones.",
                "Piercing Shot", "Arrows pierce one extra target and deal +1 damage.", none());
        add("frostfang_bow", Material.BOW, "Frostfang Bow", RARE, BOW,
                "Arrows loosed from it leave a trail of frost.",
                "Frost Arrow", "Arrows slow and freeze what they hit.", none());
        add("inferno_longbow", Material.BOW, "Inferno Longbow", EPIC, BOW,
                "The string is a thread of molten gold.",
                "Inferno Arrow", "Flaming arrows that ignite enemies around the impact.", none());

        // ---- Staves (right-click spells, cost mana) ----
        add("apprentice_wand", Material.STICK, "Apprentice Wand", COMMON, STAFF,
                "Every mage starts somewhere.",
                "Arcane Bolt", "Right-click to fire a bolt dealing 5 damage. Costs 8 mana.", single());
        add("necro_staff", Material.STICK, "Necromancer's Staff", RARE, STAFF,
                "Whispers rise from the skull at its tip.",
                "Soul Drain", "Right-click to drain 6 health from a target and heal yourself. Costs 18 mana.",
                single());
        add("frost_staff", Material.STICK, "Frost Staff", RARE, STAFF,
                "Cold radiates from the crystal at its head.",
                "Frost Nova", "Right-click to freeze, slow and damage nearby enemies. Costs 25 mana.", single());
        add("staff_of_renewal", Material.STICK, "Staff of Renewal", EPIC, STAFF,
                "Blooms with life in the darkest places.",
                "Renewal", "Right-click to heal yourself and nearby players. Costs 30 mana.", single());
        add("lich_scepter", Material.STICK, "Lich Scepter", LEGENDARY, STAFF,
                "Taken from the Lich King himself.",
                "Death Skull", "Right-click to launch an exploding wither skull. Costs 30 mana.", single());

        // ---- Armor (worn look comes from assets/dungeons/equipment/<set>.json) ----
        armorSet("crypt_warden", "Crypt Warden", UNCOMMON, "IRON", Sound.ITEM_ARMOR_EQUIP_IRON,
                "Armor of the crypt's undying guardians.",
                "Take 25% less damage from undead.", 2, 6, 5, 2, 0.5, 0);
        armorSet("frostforged", "Frostforged", RARE, "DIAMOND", Sound.ITEM_ARMOR_EQUIP_DIAMOND,
                "Hammered from ice that never melts.",
                "Immune to freezing. Melee attackers are slowed.", 3, 8, 6, 3, 2, 0);
        armorSet("infernal", "Infernal", EPIC, "NETHERITE", Sound.ITEM_ARMOR_EQUIP_NETHERITE,
                "Tempered in the heart of a volcano.",
                "Permanent fire resistance. Melee attackers are set on fire.", 3, 8, 6, 3, 3, 0.1);

        // ---- Consumables / artifacts ----
        add("health_potion", Material.POTION, "Health Potion", UNCOMMON, CONSUMABLE,
                "Restores a little health.", null, null, potion(PotionType.HEALING, 0xe8344a));
        add("greater_health_potion", Material.POTION, "Greater Health Potion", RARE, CONSUMABLE,
                "Restores a lot of health.", null, null, potion(PotionType.STRONG_HEALING, 0xff2f8a));
        add("mana_potion", Material.POTION, "Mana Potion", UNCOMMON, CONSUMABLE,
                "Tastes faintly of blueberries.", "Restore Mana", "Restores 60 mana.",
                potion(null, 0x3a7bff));
        add("wind_horn", Material.GOAT_HORN, "Wind Horn", RARE, ARTIFACT,
                "Its call carries across the whole dungeon.",
                "Gale", "Blow to hurl nearby mobs away and gain a burst of speed.", none());
        add("totem_of_shielding", Material.TOTEM_OF_UNDYING, "Totem of Shielding", EPIC, ARTIFACT,
                "Saves its holder from death once.",
                "Last Stand", "When it saves you, also grants a strong shield and repels nearby mobs.", none());

        // ---- Materials ----
        add("bone_shard", Material.PAPER, "Bone Shard", COMMON, MATERIAL,
                "Dropped by crypt skeletons.", null, null, none());
        add("ember_core", Material.PAPER, "Ember Core", UNCOMMON, MATERIAL,
                "A still-glowing core from an infernal mob.", null, null, none());
        add("frost_crystal", Material.PAPER, "Frost Crystal", UNCOMMON, MATERIAL,
                "Chipped from the walls of the frozen depths.", null, null, none());
        add("lich_soul", Material.PAPER, "Lich Soul", EPIC, MATERIAL,
                "Dropped by the Lich King.", null, null, none());
        add("molten_heart", Material.PAPER, "Molten Heart", EPIC, MATERIAL,
                "Dropped by the Molten Tyrant.", null, null, none());
        add("glacial_heart", Material.PAPER, "Glacial Heart", EPIC, MATERIAL,
                "Dropped by the Frost Queen.", null, null, none());

        // ---- Boss crowns (3D models, worn on the head) ----
        add("lich_crown", Material.PAPER, "Lich Crown", LEGENDARY, CROWN,
                "The crown of the Lich King.", null, null, crown());
        add("molten_crown", Material.PAPER, "Molten Crown", LEGENDARY, CROWN,
                "The crown of the Molten Tyrant.", null, null, crown());
        add("frost_crown", Material.PAPER, "Frost Crown", LEGENDARY, CROWN,
                "The crown of the Frost Queen.", null, null, crown());

        // ---- Menu icons ----
        add("crypt_icon", Material.PAPER, "The Crypt", COMMON, ICON, "Dungeon menu icon.", null, null, none());
        add("infernal_icon", Material.PAPER, "Infernal Depths", COMMON, ICON, "Dungeon menu icon.", null, null, none());
        add("frozen_icon", Material.PAPER, "Frozen Halls", COMMON, ICON, "Dungeon menu icon.", null, null, none());
        add("coin_icon", Material.PAPER, "Dungeon Coin", COMMON, ICON, "Currency icon.", null, null, none());
    }

    private void armorSet(String set, String name, Rarity rarity, String tier, Sound sound, String desc,
                          String bonus, double head, double chest, double legs, double feet,
                          double toughness, double knockback) {
        String[][] pieces = {
                {"helmet", "HELMET", "Helmet"},
                {"chestplate", "CHESTPLATE", "Chestplate"},
                {"leggings", "LEGGINGS", "Leggings"},
                {"boots", "BOOTS", "Boots"},
        };
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        double[] armor = {head, chest, legs, feet};
        for (int i = 0; i < 4; i++) {
            add(set + "_" + pieces[i][0], Material.valueOf(tier + "_" + pieces[i][1]),
                    name + " " + pieces[i][2], rarity, ARMOR, desc, "Full Set Bonus", bonus,
                    armor(set, slots[i], sound, armor[i], toughness, knockback));
        }
    }

    private void add(String id, Material material, String name, Rarity rarity, Category category,
                     String description, String abilityName, String abilityText, Consumer<ItemMeta> extra) {
        if (items.containsKey(id)) {
            throw new IllegalStateException("Duplicate item id " + id);
        }
        items.put(id, new DungeonItem(id, material, name, rarity, category, description,
                abilityName, abilityText, extra));
    }

    // ------------------------------------------------------------------ //
    // Meta helpers
    // ------------------------------------------------------------------ //

    private static Consumer<ItemMeta> none() {
        return meta -> { };
    }

    private static Consumer<ItemMeta> single() {
        return meta -> meta.setMaxStackSize(1);
    }

    /** Uses the vanilla base modifier ids so the tooltip shows "8 Attack Damage" like vanilla swords. */
    private static Consumer<ItemMeta> weapon(double damage, double speed) {
        return meta -> {
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    NamespacedKey.minecraft("base_attack_damage"), damage - 1,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                    NamespacedKey.minecraft("base_attack_speed"), speed - 4,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        };
    }

    private Consumer<ItemMeta> armor(String set, EquipmentSlot slot, Sound sound,
                                     double armor, double toughness, double knockback) {
        EquipmentSlotGroup group = slot.getGroup();
        String slotName = slot.name().toLowerCase();
        return meta -> {
            meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                    new NamespacedKey(plugin, "armor_" + slotName), armor,
                    AttributeModifier.Operation.ADD_NUMBER, group));
            if (toughness > 0) {
                meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(
                        new NamespacedKey(plugin, "toughness_" + slotName), toughness,
                        AttributeModifier.Operation.ADD_NUMBER, group));
            }
            if (knockback > 0) {
                meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(
                        new NamespacedKey(plugin, "knockback_" + slotName), knockback,
                        AttributeModifier.Operation.ADD_NUMBER, group));
            }
            EquippableComponent equippable = meta.getEquippable();
            equippable.setSlot(slot);
            equippable.setModel(new NamespacedKey(NAMESPACE, set));
            equippable.setEquipSound(sound);
            meta.setEquippable(equippable);
        };
    }

    private static Consumer<ItemMeta> potion(PotionType type, int rgb) {
        return meta -> {
            if (meta instanceof PotionMeta potion) {
                if (type != null) {
                    potion.setBasePotionType(type);
                }
                potion.setColor(Color.fromRGB(rgb));
            }
        };
    }

    private static Consumer<ItemMeta> crown() {
        return meta -> {
            meta.setMaxStackSize(1);
            EquippableComponent equippable = meta.getEquippable();
            equippable.setSlot(EquipmentSlot.HEAD);
            meta.setEquippable(equippable);
        };
    }

    // ------------------------------------------------------------------ //
    // Public API
    // ------------------------------------------------------------------ //

    public Set<String> ids() {
        return Collections.unmodifiableSet(items.keySet());
    }

    public DungeonItem get(String id) {
        return items.get(id);
    }

    public List<DungeonItem> all() {
        return List.copyOf(items.values());
    }

    public ItemStack create(String id, int amount) {
        DungeonItem def = items.get(id);
        if (def == null) {
            throw new IllegalArgumentException("Unknown item " + id);
        }
        return create(def, amount);
    }

    public ItemStack create(DungeonItem def, int amount) {
        ItemStack stack = new ItemStack(def.material(), Math.max(1, amount));
        stack.editMeta(meta -> {
            meta.displayName(plain(def.name(), def.rarity().color()));

            List<Component> lore = new ArrayList<>();
            for (String line : wrap(def.description())) {
                lore.add(plain(line, NamedTextColor.GRAY));
            }
            if (def.abilityName() != null) {
                lore.add(Component.empty());
                lore.add(plain(def.abilityName(), NamedTextColor.GOLD));
                for (String line : wrap(def.abilityText())) {
                    lore.add(plain(line, NamedTextColor.DARK_AQUA));
                }
            }
            lore.add(Component.empty());
            lore.add(plain(def.rarity().name() + " " + def.category().label().toUpperCase(), def.rarity().color())
                    .decoration(TextDecoration.BOLD, true));
            meta.lore(lore);

            meta.setItemModel(new NamespacedKey(NAMESPACE, def.id()));
            meta.getPersistentDataContainer().set(Keys.ITEM_ID, PersistentDataType.STRING, def.id());
            def.extra().accept(meta);
        });
        return stack;
    }

    /** Returns the custom id of a stack, or null for vanilla items. */
    public String idOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_ID, PersistentDataType.STRING);
    }

    public boolean isCustom(ItemStack stack) {
        return idOf(stack) != null;
    }

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > LORE_WIDTH) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }
}
