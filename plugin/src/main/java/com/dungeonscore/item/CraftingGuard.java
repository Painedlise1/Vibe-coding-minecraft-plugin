package com.dungeonscore.item;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

/**
 * Custom items are built on vanilla bases (paper, sticks, iron swords...). Without this,
 * a Lich Soul could be crafted into a book, a staff burned as fuel, or Frostforged armor
 * upgraded to netherite at a smithing table.
 */
public final class CraftingGuard implements Listener {

    private final ItemRegistry items;

    public CraftingGuard(ItemRegistry items) {
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (items.isCustom(stack)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmith(PrepareSmithingEvent event) {
        SmithingInventory inv = event.getInventory();
        if (items.isCustom(inv.getInputTemplate())
                || items.isCustom(inv.getInputEquipment())
                || items.isCustom(inv.getInputMineral())) {
            event.setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFuel(FurnaceBurnEvent event) {
        if (items.isCustom(event.getFuel())) {
            event.setCancelled(true);
        }
    }
}
