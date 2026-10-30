package com.dungeonscore;

import com.dungeonscore.ability.CombatListener;
import com.dungeonscore.ability.ManaManager;
import com.dungeonscore.ability.StaffListener;
import com.dungeonscore.boss.BossManager;
import com.dungeonscore.command.DungeonsCommand;
import com.dungeonscore.dungeon.DungeonManager;
import com.dungeonscore.item.CraftingGuard;
import com.dungeonscore.item.ItemRegistry;
import com.dungeonscore.pack.PackService;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class DungeonsCore extends JavaPlugin {

    private PackService pack;
    private ManaManager mana;
    private CombatListener combat;
    private BossManager bosses;
    private DungeonManager dungeon;

    @Override
    public void onEnable() {
        writeDefaultConfig();
        Keys.init(this);

        ItemRegistry items = new ItemRegistry(this);
        pack = new PackService(this);
        pack.start();
        pack.checkItems(items.ids());

        mana = new ManaManager(this, items);
        combat = new CombatListener(this, items);
        bosses = new BossManager(this, items);
        bosses.start();
        dungeon = new DungeonManager(this, items, bosses);
        dungeon.start();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(pack, this);
        pm.registerEvents(new CraftingGuard(items), this);
        pm.registerEvents(mana, this);
        pm.registerEvents(new StaffListener(items, mana), this);
        pm.registerEvents(combat, this);
        pm.registerEvents(bosses, this);
        pm.registerEvents(dungeon, this);

        DungeonsCommand command = new DungeonsCommand(items, pack, bosses, mana, dungeon);
        PluginCommand cmd = getCommand("dungeons");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }
        getLogger().info("Loaded " + items.ids().size() + " custom items.");
    }

    @Override
    public void onDisable() {
        if (dungeon != null) {
            dungeon.stop();
        }
        if (bosses != null) {
            bosses.stop();
        }
        if (combat != null) {
            combat.stop();
        }
        if (mana != null) {
            mana.stop();
        }
        if (pack != null) {
            pack.stop();
        }
    }

    /** Defaults live in code; plugins/DungeonsCore/config.yml is created on first start. */
    private void writeDefaultConfig() {
        FileConfiguration cfg = getConfig();
        cfg.addDefault("resource-pack.enabled", true);
        cfg.addDefault("resource-pack.external-url", "");
        cfg.addDefault("resource-pack.port", 8164);
        cfg.addDefault("resource-pack.public-address", "");
        cfg.addDefault("resource-pack.required", false);
        cfg.addDefault("resource-pack.prompt", "This server uses custom Dungeons textures.");
        cfg.options().copyDefaults(true);
        cfg.options().setHeader(List.of(
                "DungeonsCore configuration",
                "",
                "resource-pack.external-url: leave empty to let the plugin host the pack itself on 'port'.",
                "  If you upload the zip elsewhere, put the direct download link here. It must be identical",
                "  to the resourcepack.zip inside the jar (the SHA-1 is printed on startup).",
                "resource-pack.port: built-in web server port - must be open/forwarded like the game port.",
                "resource-pack.public-address: domain or public IP players use to reach this machine.",
                "  Empty = server-ip from server.properties, or 127.0.0.1 (local testing only).",
                "resource-pack.required: true = players who decline the pack are disconnected."));
        saveConfig();
    }
}
