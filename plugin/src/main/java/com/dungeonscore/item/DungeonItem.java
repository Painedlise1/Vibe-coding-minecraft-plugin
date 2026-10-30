package com.dungeonscore.item;

import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.Consumer;

/**
 * Definition of one custom item.
 *
 * @param id          id shared with the resource pack (assets/dungeons/items/&lt;id&gt;.json)
 * @param material    vanilla base item (decides behaviour: bow shoots, potion drinks, ...)
 * @param name        display name
 * @param rarity      name colour + rarity line
 * @param category    grouping for /dungeons list and the rarity line
 * @param description grey lore text (wrapped automatically)
 * @param abilityName ability / set bonus title shown in gold, or null
 * @param abilityText ability description, or null
 * @param extra       extra meta changes (attributes, potion type, equippable, ...)
 */
public record DungeonItem(
        String id,
        Material material,
        String name,
        Rarity rarity,
        Category category,
        String description,
        String abilityName,
        String abilityText,
        Consumer<ItemMeta> extra) {
}
