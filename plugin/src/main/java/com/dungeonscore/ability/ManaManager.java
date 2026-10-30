package com.dungeonscore.ability;

import com.dungeonscore.item.Category;
import com.dungeonscore.item.DungeonItem;
import com.dungeonscore.item.ItemRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mana for staff spells. Regenerates over time, shown on the action bar while a staff is held.
 * Mana is not saved - everyone starts full after a restart or rejoin.
 */
public final class ManaManager implements Listener {

    public static final double MAX_MANA = 100;
    private static final double REGEN_PER_SECOND = 4;
    private static final double MANA_POTION = 60;
    private static final int BAR_SEGMENTS = 20;

    private final ItemRegistry items;
    private final Map<UUID, Double> mana = new HashMap<>();
    private BukkitTask task;

    public ManaManager(Plugin plugin, ItemRegistry items) {
        this.items = items;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public double get(Player player) {
        return mana.getOrDefault(player.getUniqueId(), MAX_MANA);
    }

    public void set(Player player, double value) {
        mana.put(player.getUniqueId(), Math.max(0, Math.min(MAX_MANA, value)));
    }

    /** Spends mana if the player has enough; otherwise tells them and returns false. */
    public boolean tryConsume(Player player, double cost) {
        double current = get(player);
        if (current < cost) {
            player.sendActionBar(Component.text("Not enough mana! (" + (int) current + "/" + (int) cost + ")",
                    NamedTextColor.RED));
            return false;
        }
        set(player, current - cost);
        showBar(player);
        return true;
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            double current = get(player);
            if (current < MAX_MANA) {
                set(player, current + REGEN_PER_SECOND / 2);
            }
            if (holdingStaff(player)) {
                showBar(player);
            }
        }
    }

    private boolean holdingStaff(Player player) {
        DungeonItem def = items.get(String.valueOf(items.idOf(player.getInventory().getItemInMainHand())));
        return def != null && def.category() == Category.STAFF;
    }

    public void showBar(Player player) {
        double current = get(player);
        int filled = (int) Math.round(current / MAX_MANA * BAR_SEGMENTS);
        player.sendActionBar(Component.text("Mana ", NamedTextColor.AQUA)
                .append(Component.text("|".repeat(filled), NamedTextColor.BLUE))
                .append(Component.text("|".repeat(BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY))
                .append(Component.text(" " + (int) current + "/" + (int) MAX_MANA, NamedTextColor.AQUA)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        if ("mana_potion".equals(items.idOf(event.getItem()))) {
            Player player = event.getPlayer();
            set(player, get(player) + MANA_POTION);
            showBar(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        mana.remove(event.getPlayer().getUniqueId());
    }
}
