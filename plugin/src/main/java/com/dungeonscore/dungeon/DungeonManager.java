package com.dungeonscore.dungeon;

import com.dungeonscore.Keys;
import com.dungeonscore.boss.BossManager;
import com.dungeonscore.boss.BossType;
import com.dungeonscore.item.ItemRegistry;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Witch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Runs the Overgrown City dungeon level: its world, the shared run (party, encounters, gates, boss), and the
 * rules that keep the city intact.
 *
 * <p>Flow: players join and arrive in the entrance courtyard. Walking into an infested district spawns a group of
 * dungeon mobs; killing all of them clears the district. Clearing {@link #REQUIRED_CLEARS} districts opens the
 * root-sealed gates of the Heart of the Grove; entering it awakens the Mossheart Behemoth. Killing the boss
 * completes the run, and after a short countdown everyone is sent home and the dungeon resets.</p>
 */
public final class DungeonManager implements Listener {

    public static final String WORLD_NAME = "dungeon_overgrown";
    /** Bump when the generator changes: the dungeon world is then deleted and regenerated on startup. */
    private static final int GENERATOR_VERSION = 1;
    private static final String MARKER_FILE = "dungeonscore_generator.txt";
    private static final int REQUIRED_CLEARS = 10;
    private static final int COMPLETE_SECONDS = 30;
    private static final double BOSS_LEASH = 20;
    private static final String ADMIN = "dungeons.admin";

    public enum State { IDLE, CLEARING, GATES_OPEN, BOSS, COMPLETE }

    private static final class Encounter {
        final Set<UUID> alive = new LinkedHashSet<>();
        boolean cleared;
    }

    private final JavaPlugin plugin;
    private final ItemRegistry items;
    private final BossManager bosses;
    private final Set<UUID> party = new LinkedHashSet<>();
    private final Map<Long, Encounter> encounters = new HashMap<>();
    private final BossBar progressBar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.GREEN,
            BossBar.Overlay.NOTCHED_10);
    private final int required = Math.min(REQUIRED_CLEARS, CityLayout.infestedCount());

    private World world;
    private State state = State.IDLE;
    private long runId = ThreadLocalRandom.current().nextLong();
    private int cleared;
    private UUID bossId;
    private int countdown;
    private int ticks;
    private BukkitTask task;

    public DungeonManager(JavaPlugin plugin, ItemRegistry items, BossManager bosses) {
        this.plugin = plugin;
        this.items = items;
        this.bosses = bosses;
    }

    // ------------------------------------------------------------------ //
    // Lifecycle
    // ------------------------------------------------------------------ //

    public void start() {
        loadWorld();
        if (world != null) {
            cleanupEntities();
            setGates(false);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID id : List.copyOf(party)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(progressBar);
                try {
                    sendHome(p);
                } catch (RuntimeException e) {
                    // shutting down - the stored return location will be used when they next join
                }
            }
        }
        party.clear();
        if (world != null) {
            cleanupEntities();
        }
        state = State.IDLE;
    }

    private void loadWorld() {
        world = Bukkit.getWorld(WORLD_NAME);
        if (world != null) {
            return;
        }
        File folder = new File(Bukkit.getWorldContainer(), WORLD_NAME);
        File marker = new File(folder, MARKER_FILE);
        if (folder.exists() && !String.valueOf(GENERATOR_VERSION).equals(readMarker(marker))) {
            plugin.getLogger().info("Dungeon generator changed - regenerating " + WORLD_NAME + ".");
            deleteFolder(folder.toPath());
        }
        plugin.getLogger().info("Loading dungeon world " + WORLD_NAME + "...");
        world = new WorldCreator(WORLD_NAME)
                .generator(new OvergrownCityGenerator())
                .generateStructures(false)
                .createWorld();
        if (world == null) {
            plugin.getLogger().severe("Could not create the dungeon world - the dungeon is disabled.");
            return;
        }
        writeMarker(marker);
        configureWorld();
    }

    private void configureWorld() {
        world.setDifficulty(Difficulty.NORMAL);
        world.setPVP(false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        world.setGameRule(GameRule.DO_WARDEN_SPAWNING, false);
        world.setGameRule(GameRule.DO_INSOMNIA, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRule.SPAWN_RADIUS, 0);
        world.setGameRule(GameRule.SPAWN_CHUNK_RADIUS, 0);
        world.setTime(1000);
        world.setStorm(false);
        world.setThundering(false);
        world.setSpawnLocation(CityLayout.startLocation(world));
    }

    /** Admin: throw everyone out, delete the dungeon world and generate it again. */
    public boolean rebuild() {
        if (world == null) {
            loadWorld();
            return world != null;
        }
        endRun(false);
        Location fallback = mainSpawn();
        for (Player p : List.copyOf(world.getPlayers())) {
            p.teleport(fallback);
        }
        File folder = world.getWorldFolder();
        if (!Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().warning("Could not unload " + WORLD_NAME + " for rebuilding.");
            return false;
        }
        world = null;
        deleteFolder(folder.toPath());
        loadWorld();
        return world != null;
    }

    private static String readMarker(File marker) {
        try {
            return marker.exists() ? Files.readString(marker.toPath(), StandardCharsets.UTF_8).trim() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void writeMarker(File marker) {
        try {
            Files.writeString(marker.toPath(), String.valueOf(GENERATOR_VERSION), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write " + marker + ": " + e.getMessage());
        }
    }

    private void deleteFolder(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not fully delete " + root + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    // Public API (commands)
    // ------------------------------------------------------------------ //

    public World world() {
        return world;
    }

    public State state() {
        return state;
    }

    public int partySize() {
        return party.size();
    }

    public boolean inParty(Player p) {
        return party.contains(p.getUniqueId());
    }

    public String status() {
        if (world == null) {
            return "The dungeon world failed to load.";
        }
        String players = party.isEmpty() ? "none" : String.join(", ", party.stream()
                .map(Bukkit::getPlayer).filter(p -> p != null).map(Player::getName).toList());
        return switch (state) {
            case IDLE -> "The Overgrown City is quiet. Nobody is inside.";
            case CLEARING -> "Clearing districts: " + cleared + "/" + required + ". Party: " + players;
            case GATES_OPEN -> "The Heart of the Grove is open. Party: " + players;
            case BOSS -> "Fighting the Mossheart Behemoth. Party: " + players;
            case COMPLETE -> "Cleared! Resetting in " + countdown + "s. Party: " + players;
        };
    }

    /** Returns an error message, or null on success. */
    public String join(Player p) {
        if (world == null) {
            return "The dungeon is unavailable - check the server console.";
        }
        if (inParty(p)) {
            return "You are already in the dungeon. Use /dungeons dungeon leave to get out.";
        }
        if (state == State.COMPLETE) {
            return "The dungeon is resetting - try again in " + countdown + " seconds.";
        }
        if (state == State.IDLE) {
            beginRun();
        }
        if (!p.getWorld().equals(world)) {
            saveReturn(p);
        }
        party.add(p.getUniqueId());
        p.teleport(CityLayout.startLocation(world));
        p.showBossBar(progressBar);
        p.showTitle(Title.title(
                Component.text("The Overgrown City", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("Clear the infested districts", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))));
        p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 1f, 0.8f);
        p.sendMessage(Component.text("Roots have swallowed this city. Clear " + required
                + " infested districts to break the seal on the Heart of the Grove.", NamedTextColor.GREEN));
        tellParty(Component.text(p.getName() + " entered the Overgrown City.", NamedTextColor.YELLOW), p);
        updateBar();
        return null;
    }

    /** Returns an error message, or null on success. */
    public String leave(Player p) {
        if (!inParty(p)) {
            if (world != null && p.getWorld().equals(world)) {
                sendHome(p);
                return null;
            }
            return "You are not in the dungeon.";
        }
        removeFromParty(p);
        sendHome(p);
        return null;
    }

    /** Admin: end the current run and send everyone home. */
    public void reset() {
        endRun(true);
    }

    /** Admin: open the grove gates now. Returns false if no run is in the clearing phase. */
    public boolean forceOpen() {
        if (state != State.CLEARING) {
            return false;
        }
        openGates();
        return true;
    }

    // ------------------------------------------------------------------ //
    // Run flow
    // ------------------------------------------------------------------ //

    private void beginRun() {
        cleanupEntities();
        runId = ThreadLocalRandom.current().nextLong();
        encounters.clear();
        cleared = 0;
        bossId = null;
        setGates(false);
        world.setTime(1000);
        state = State.CLEARING;
        updateBar();
    }

    private void endRun(boolean announce) {
        for (UUID id : List.copyOf(party)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(progressBar);
                if (announce) {
                    p.sendMessage(Component.text("The dungeon has been reset.", NamedTextColor.YELLOW));
                }
                party.remove(id);
                sendHome(p);
            }
        }
        party.clear();
        state = State.IDLE;
        encounters.clear();
        bossId = null;
        if (world != null) {
            cleanupEntities();
            setGates(false);
        }
    }

    private void removeFromParty(Player p) {
        party.remove(p.getUniqueId());
        p.hideBossBar(progressBar);
        tellParty(Component.text(p.getName() + " left the dungeon.", NamedTextColor.YELLOW), p);
        if (party.isEmpty() && state != State.IDLE) {
            endRun(false);
        }
    }

    private void tick() {
        if (world == null || state == State.IDLE) {
            return;
        }
        ticks++;
        for (UUID id : List.copyOf(party)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.getWorld().equals(world)) {
                continue;
            }
            Location loc = p.getLocation();
            if (!CityLayout.inBounds(loc.getX(), loc.getY(), loc.getZ()) && !p.isDead()) {
                p.teleport(CityLayout.startLocation(world));
                p.sendMessage(Component.text("The roots drag you back to the entrance.", NamedTextColor.GRAY));
                continue;
            }
            boolean fighting = p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
            if (fighting && state != State.COMPLETE && !p.isDead()) {
                tryEncounter(Math.floorDiv(loc.getBlockX(), 16), Math.floorDiv(loc.getBlockZ(), 16), p);
                if (state == State.GATES_OPEN && CityLayout.inPlazaInterior(loc.getX(), loc.getZ())) {
                    spawnBoss();
                }
            }
        }
        if (state == State.BOSS) {
            tickBoss();
        }
        if (ticks % 4 == 0) {
            shamanPulse();
        }
        if (state == State.COMPLETE) {
            countdown--;
            if (countdown == 10 || (countdown <= 5 && countdown > 0)) {
                tellParty(Component.text("Returning home in " + countdown + "...", NamedTextColor.GRAY), null);
            }
            updateBar();
            if (countdown <= 0) {
                endRun(false);
            }
        }
    }

    private void tickBoss() {
        Entity boss = bossId == null ? null : Bukkit.getEntity(bossId);
        Location spawn = CityLayout.bossSpawn(world);
        if (boss == null || !boss.isValid()) {
            if (world.isChunkLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4)) {
                // removed by an admin or otherwise lost: re-arm so it wakes again when someone enters
                bossId = null;
                state = State.GATES_OPEN;
                updateBar();
            }
            return;
        }
        if (boss.getLocation().distanceSquared(CityLayout.plazaCentre(world)) > BOSS_LEASH * BOSS_LEASH) {
            boss.teleport(spawn);
            world.spawnParticle(Particle.BLOCK, spawn.clone().add(0, 1, 0), 60, 1, 1, 1, 0.1,
                    Material.ROOTED_DIRT.createBlockData());
        }
    }

    // ------------------------------------------------------------------ //
    // Encounters
    // ------------------------------------------------------------------ //

    private static long cellKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    private void tryEncounter(int cx, int cz, Player trigger) {
        if (!CityLayout.infested(cx, cz)) {
            return;
        }
        long key = cellKey(cx, cz);
        if (encounters.containsKey(key)) {
            return;
        }
        Encounter encounter = new Encounter();
        encounters.put(key, encounter);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int count = 3 + random.nextInt(2) + Math.min(3, party.size() - 1);
        boolean elite = CityLayout.elite(cx, cz);
        for (int i = 0; i < count; i++) {
            Location at = findSpawn(cx, cz, trigger.getLocation(), random);
            if (at == null) {
                continue;
            }
            DungeonMob type = elite && i == 0 ? DungeonMob.ROOTBOUND_BRUTE : DungeonMob.pick(random);
            Mob mob = type.spawn(at, runId, key);
            mob.setTarget(trigger);
            encounter.alive.add(mob.getUniqueId());
            world.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.3, 0), 25, 0.4, 0.2, 0.4, 0.1,
                    Material.MOSS_BLOCK.createBlockData());
            world.playSound(at, Sound.BLOCK_ROOTED_DIRT_BREAK, 1f, 0.7f);
        }
        if (encounter.alive.isEmpty()) {
            encounter.cleared = true;
            return;
        }
        trigger.playSound(trigger.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1f, 0.6f);
        trigger.sendActionBar(Component.text(elite ? "Something big stirs in the undergrowth..."
                : "The overgrowth stirs...", elite ? NamedTextColor.RED : NamedTextColor.GREEN));
    }

    private Location findSpawn(int cx, int cz, Location player, ThreadLocalRandom random) {
        int floor = CityLayout.FLOOR;
        Location fallback = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            int x = cx * 16 + 1 + random.nextInt(14);
            int z = cz * 16 + 1 + random.nextInt(14);
            if (!CityLayout.inBounds(x, floor + 1, z)) {
                continue;
            }
            Block ground = world.getBlockAt(x, floor, z);
            if (!ground.getType().isSolid() || !ground.getRelative(0, 1, 0).isPassable()
                    || !ground.getRelative(0, 2, 0).isPassable()
                    || ground.getRelative(0, 1, 0).isLiquid()) {
                continue;
            }
            Location at = new Location(world, x + 0.5, floor + 1, z + 0.5, random.nextFloat() * 360f, 0f);
            if (at.distanceSquared(player) >= 25) {
                return at;
            }
            fallback = at;
        }
        return fallback;
    }

    private void onEncounterCleared() {
        cleared++;
        if (state == State.CLEARING) {
            tellParty(Component.text("District cleared! ", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .append(Component.text("(" + Math.min(cleared, required) + "/" + required + ")",
                            NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false)), null);
            for (Player p : partyPlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
            }
            if (cleared >= required) {
                openGates();
            }
        }
        updateBar();
    }

    /** Spore Shamans periodically mend nearby dungeon mobs. */
    private void shamanPulse() {
        for (Witch witch : world.getEntitiesByClass(Witch.class)) {
            if (DungeonMob.of(witch) != DungeonMob.SPORE_SHAMAN || witch.getTarget() == null) {
                continue;
            }
            Location at = witch.getLocation().add(0, 1, 0);
            world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, at, 40, 3, 1, 3, 0);
            world.spawnParticle(Particle.HAPPY_VILLAGER, at, 10, 2, 1, 2, 0);
            world.playSound(at, Sound.BLOCK_AZALEA_LEAVES_BREAK, 1f, 0.6f);
            for (Entity near : witch.getNearbyEntities(6, 3, 6)) {
                if (near instanceof LivingEntity ally && DungeonMob.of(ally) != null && !ally.isDead()) {
                    double max = ally.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                    ally.setHealth(Math.min(max, ally.getHealth() + 4));
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Gates and boss
    // ------------------------------------------------------------------ //

    private void setGates(boolean open) {
        for (int[] b : CityLayout.gateBlocks()) {
            world.getBlockAt(b[0], b[1], b[2]).setType(open ? Material.AIR : CityLayout.GATE_MATERIAL, false);
        }
    }

    private void openGates() {
        setGates(true);
        state = State.GATES_OPEN;
        for (Location gate : CityLayout.gateCentres(world)) {
            world.spawnParticle(Particle.BLOCK, gate, 120, 1.5, 1.5, 1.5, 0.2,
                    CityLayout.GATE_MATERIAL.createBlockData());
            world.playSound(gate, Sound.BLOCK_ROOTED_DIRT_BREAK, 2f, 0.5f);
            world.playSound(gate, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.5f, 0.6f);
        }
        for (Player p : partyPlayers()) {
            p.showTitle(Title.title(
                    Component.text("The seal breaks", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text("The Heart of the Grove is open - head to the city centre", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofSeconds(1))));
            p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.6f, 1.2f);
        }
        updateBar();
    }

    private void spawnBoss() {
        Location at = CityLayout.bossSpawn(world);
        Mob boss = bosses.spawn(BossType.MOSSHEART_BEHEMOTH, at);
        boss.getPersistentDataContainer().set(Keys.DUNGEON_RUN, PersistentDataType.LONG, runId);
        bossId = boss.getUniqueId();
        state = State.BOSS;
        world.spawnParticle(Particle.BLOCK, at.clone().add(0, 1, 0), 150, 2, 1, 2, 0.2,
                Material.MOSS_BLOCK.createBlockData());
        for (Player p : partyPlayers()) {
            p.showTitle(Title.title(
                    Component.text("Mossheart Behemoth", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text("Guardian of the Grove", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(800))));
        }
        updateBar();
    }

    private void onBossDefeated() {
        state = State.COMPLETE;
        bossId = null;
        countdown = COMPLETE_SECONDS;
        for (Player p : partyPlayers()) {
            p.showTitle(Title.title(
                    Component.text("Dungeon Cleared!", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text("The Overgrown City falls silent", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofSeconds(1))));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            p.giveExp(150);
            for (ItemStack overflow : p.getInventory().addItem(items.create("greater_health_potion", 1)).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), overflow);
            }
            p.sendMessage(Component.text("Grab your loot! You'll be sent home in " + COMPLETE_SECONDS
                    + " seconds, or use /dungeons dungeon leave.", NamedTextColor.YELLOW));
        }
        updateBar();
    }

    // ------------------------------------------------------------------ //
    // Helpers
    // ------------------------------------------------------------------ //

    private void updateBar() {
        switch (state) {
            case IDLE, CLEARING -> {
                progressBar.name(Component.text("Overgrown City - Clear infested districts ", NamedTextColor.GREEN)
                        .append(Component.text("(" + Math.min(cleared, required) + "/" + required + ")",
                                NamedTextColor.WHITE)));
                progressBar.progress(required == 0 ? 1f : Math.min(1f, cleared / (float) required));
                progressBar.color(BossBar.Color.GREEN);
            }
            case GATES_OPEN -> {
                progressBar.name(Component.text("The Heart of the Grove is open - reach the city centre",
                        NamedTextColor.GOLD));
                progressBar.progress(1f);
                progressBar.color(BossBar.Color.YELLOW);
            }
            case BOSS -> {
                progressBar.name(Component.text("Defeat the Mossheart Behemoth", NamedTextColor.RED));
                progressBar.progress(1f);
                progressBar.color(BossBar.Color.RED);
            }
            case COMPLETE -> {
                progressBar.name(Component.text("Dungeon cleared! Returning home in " + countdown + "s",
                        NamedTextColor.GOLD));
                progressBar.progress(Math.max(0f, countdown / (float) COMPLETE_SECONDS));
                progressBar.color(BossBar.Color.WHITE);
            }
        }
    }

    private List<Player> partyPlayers() {
        List<Player> list = new ArrayList<>();
        for (UUID id : party) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    private void tellParty(Component message, Player except) {
        for (Player p : partyPlayers()) {
            if (!p.equals(except)) {
                p.sendMessage(message);
            }
        }
    }

    /** Removes every dungeon mob, dungeon boss, minion, dropped item and projectile in loaded dungeon chunks. */
    private void cleanupEntities() {
        for (Entity e : world.getEntities()) {
            if (e instanceof Player) {
                continue;
            }
            PersistentDataContainer pdc = e.getPersistentDataContainer();
            if (e instanceof Item || e instanceof ExperienceOrb || e instanceof Projectile
                    || pdc.has(Keys.DUNGEON_MOB, PersistentDataType.STRING)
                    || pdc.has(Keys.BOSS, PersistentDataType.STRING)
                    || pdc.has(Keys.MINION, PersistentDataType.STRING)) {
                e.remove();
            }
        }
    }

    private static boolean isHostileFaction(Entity e) {
        if (e == null) {
            return false;
        }
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        return pdc.has(Keys.DUNGEON_MOB, PersistentDataType.STRING)
                || pdc.has(Keys.BOSS, PersistentDataType.STRING)
                || pdc.has(Keys.MINION, PersistentDataType.STRING);
    }

    private boolean inDungeon(World w) {
        return world != null && world.equals(w);
    }

    private static Location mainSpawn() {
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    private void saveReturn(Player p) {
        Location l = p.getLocation();
        p.getPersistentDataContainer().set(Keys.DUNGEON_RETURN, PersistentDataType.STRING,
                l.getWorld().getUID() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";"
                        + l.getYaw() + ";" + l.getPitch());
    }

    private Location readReturn(Player p) {
        String raw = p.getPersistentDataContainer().get(Keys.DUNGEON_RETURN, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            String[] parts = raw.split(";");
            World w = Bukkit.getWorld(UUID.fromString(parts[0]));
            if (w == null || inDungeon(w)) {
                return null;
            }
            return new Location(w, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void sendHome(Player p) {
        Location back = readReturn(p);
        p.getPersistentDataContainer().remove(Keys.DUNGEON_RETURN);
        p.teleport(back != null ? back : mainSpawn());
    }

    // ------------------------------------------------------------------ //
    // Events: run
    // ------------------------------------------------------------------ //

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!inDungeon(dead.getWorld()) && DungeonMob.of(dead) == null) {
            return;
        }
        DungeonMob type = DungeonMob.of(dead);
        if (type != null) {
            type.applyDrops(event, items);
            PersistentDataContainer pdc = dead.getPersistentDataContainer();
            Long run = pdc.get(Keys.DUNGEON_RUN, PersistentDataType.LONG);
            Long key = pdc.get(Keys.ENCOUNTER, PersistentDataType.LONG);
            if (run != null && run == runId && key != null) {
                Encounter encounter = encounters.get(key);
                if (encounter != null && encounter.alive.remove(dead.getUniqueId())
                        && encounter.alive.isEmpty() && !encounter.cleared) {
                    encounter.cleared = true;
                    onEncounterCleared();
                }
            }
            return;
        }
        if (bossId != null && dead.getUniqueId().equals(bossId) && state == State.BOSS) {
            onBossDefeated();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (inParty(p)) {
            removeFromParty(p);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (inDungeon(p.getWorld()) && !inParty(p)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && inDungeon(p.getWorld()) && !inParty(p)) {
                    sendHome(p);
                    p.sendMessage(Component.text("The dungeon run you were in has ended.", NamedTextColor.YELLOW));
                }
            }, 5L);
        }
    }

    @EventHandler
    public void onChangeWorld(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        if (inDungeon(event.getFrom()) && inParty(p)) {
            p.getPersistentDataContainer().remove(Keys.DUNGEON_RETURN);
            removeFromParty(p);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (world != null && inParty(event.getPlayer()) && state != State.IDLE) {
            event.setRespawnLocation(CityLayout.startLocation(world));
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!inDungeon(event.getWorld())) {
            return;
        }
        for (Entity e : event.getEntities()) {
            if (!isHostileFaction(e)) {
                continue;
            }
            Long run = e.getPersistentDataContainer().get(Keys.DUNGEON_RUN, PersistentDataType.LONG);
            if (state == State.IDLE || run == null || run != runId) {
                e.remove();
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Events: mob behaviour
    // ------------------------------------------------------------------ //

    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        if (event instanceof EntityCombustByEntityEvent || event instanceof EntityCombustByBlockEvent) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity living && DungeonMob.of(living) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (isHostileFaction(event.getEntity()) && isHostileFaction(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (!inDungeon(event.getEntity().getWorld()) || !isHostileFaction(event.getEntity())) {
            return;
        }
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            source = shooter instanceof Entity e ? e : null;
        }
        if (isHostileFaction(source)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!inDungeon(event.getLocation().getWorld())) {
            return;
        }
        switch (event.getSpawnReason()) {
            case NATURAL, CHUNK_GEN, PATROL, JOCKEY, MOUNT, REINFORCEMENTS, VILLAGE_DEFENSE, TRAP ->
                    event.setCancelled(true);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ //
    // Events: keep the city intact
    // ------------------------------------------------------------------ //

    private static boolean canBuild(Player p) {
        return p.hasPermission(ADMIN) && p.getGameMode() == GameMode.CREATIVE;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (inDungeon(event.getBlock().getWorld()) && !canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (inDungeon(event.getBlock().getWorld()) && !canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (inDungeon(event.getBlock().getWorld()) && !canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (inDungeon(event.getBlock().getWorld()) && !canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (inDungeon(event.getLocation().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (inDungeon(event.getBlock().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (inDungeon(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (inDungeon(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (inDungeon(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (inDungeon(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        if (inDungeon(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (inDungeon(event.getBlock().getWorld()) && !(event.getEntity() instanceof Player)) {
            event.setCancelled(true);
        }
    }
}
