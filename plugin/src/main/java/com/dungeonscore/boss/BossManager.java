package com.dungeonscore.boss;

import com.dungeonscore.Keys;
import com.dungeonscore.item.ItemRegistry;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.Material;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Entity;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Raider;

import org.bukkit.entity.Skeleton;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Stray;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Spawns bosses and runs their abilities, boss bars and loot. */
public final class BossManager implements Listener {

    private static final int TICK_INTERVAL = 10;
    private static final double BAR_RANGE = 48;
    private static final double AGGRO_RANGE = 24;
    private static final int ABILITY_COOLDOWN = 140;
    private static final int ENRAGED_COOLDOWN = 80;
    private static final int MAX_MINIONS = 6;

    private final Plugin plugin;
    private final ItemRegistry items;
    private final Map<UUID, ActiveBoss> bosses = new HashMap<>();
    private BukkitTask task;

    private static final class ActiveBoss {
        final Mob entity;
        final BossType type;
        final BossBar bar;
        final Set<UUID> viewers = new HashSet<>();
        int cooldown = 60;
        boolean enraged;

        ActiveBoss(Mob entity, BossType type) {
            this.entity = entity;
            this.type = type;
            this.bar = BossBar.bossBar(title(type, false), 1f, type.barColor(), BossBar.Overlay.NOTCHED_10);
        }
    }

    public BossManager(Plugin plugin, ItemRegistry items) {
        this.plugin = plugin;
        this.items = items;
    }

    public void start() {
        for (World world : Bukkit.getWorlds()) {
            adopt(world.getEntities());
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (ActiveBoss boss : List.copyOf(bosses.values())) {
            forget(boss);
        }
    }

    // ------------------------------------------------------------------ //
    // Spawning
    // ------------------------------------------------------------------ //

    public Mob spawn(BossType type, Location location) {
        Mob mob = location.getWorld().spawn(location, type.entityClass(), entity -> configure(entity, type));
        track(mob, type);
        World world = location.getWorld();
        world.playSound(location, Sound.ENTITY_WITHER_SPAWN, 1.5f, type == BossType.FROST_QUEEN ? 1.4f : 0.8f);
        world.spawnParticle(Particle.LARGE_SMOKE, location.clone().add(0, 1, 0), 60, 0.8, 1.2, 0.8, 0.05);
        Component announce = Component.text(type.displayName(), type.color(), TextDecoration.BOLD)
                .append(Component.text(" has awoken!", NamedTextColor.RED).decoration(TextDecoration.BOLD, false));
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(location) <= BAR_RANGE * BAR_RANGE) {
                p.sendMessage(announce);
            }
        }
        return mob;
    }

    private void configure(Mob mob, BossType type) {
        mob.getPersistentDataContainer().set(Keys.BOSS, PersistentDataType.STRING, type.id());
        mob.customName(Component.text(type.displayName(), type.color(), TextDecoration.BOLD));
        mob.setCustomNameVisible(true);
        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(true);
        mob.setCanPickupItems(false);

                ItemStack weapon = type.weapon() == null ? null : items.create(type.weapon(), 1);
        setBase(mob, Attribute.MAX_HEALTH, type.health());
        mob.setHealth(type.health());
        // the held weapon's own attack damage is added on top of the base value
        double weaponDamage = weapon == null ? 0 : weaponDamage(weapon);
        setBase(mob, Attribute.ATTACK_DAMAGE, Math.max(1, type.damage() - weaponDamage));
        setBase(mob, Attribute.KNOCKBACK_RESISTANCE, 0.7);
        setBase(mob, Attribute.FOLLOW_RANGE, 40);
        setBase(mob, Attribute.SCALE, 1.35);
        setBase(mob, Attribute.ARMOR, 10);

        EntityEquipment eq = mob.getEquipment();
                eq.clear();
        if (type.crown() != null) {
            eq.setHelmet(items.create(type.crown(), 1));
        }
        if (weapon != null) {
            eq.setItemInMainHand(weapon);
        }
        eq.setHelmetDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInOffHandDropChance(0f);

                if (mob instanceof PiglinBrute brute) {
            brute.setImmuneToZombification(true);
        }
        if (mob instanceof Raider raider) {
            raider.setCanJoinRaid(false);
            raider.setPatrolLeader(false);
        }
        if (type == BossType.MOLTEN_TYRANT) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION,
                    0, true, false));
        }
    }

    private static double weaponDamage(ItemStack weapon) {
        if (!weapon.hasItemMeta()) {
            return 0;
        }
        Collection<AttributeModifier> mods = weapon.getItemMeta().getAttributeModifiers(Attribute.ATTACK_DAMAGE);
        if (mods == null) {
            return 0;
        }
        double total = 0;
        for (AttributeModifier mod : mods) {
            if (mod.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                total += mod.getAmount();
            }
        }
        return total;
    }

    private static void setBase(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** Removes every boss and boss minion in loaded chunks. */
    public int removeAll() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity e : world.getLivingEntities()) {
                PersistentDataContainer pdc = e.getPersistentDataContainer();
                if (pdc.has(Keys.BOSS, PersistentDataType.STRING) || pdc.has(Keys.MINION, PersistentDataType.STRING)) {
                    ActiveBoss boss = bosses.get(e.getUniqueId());
                    if (boss != null) {
                        forget(boss);
                    }
                    e.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    public int activeCount() {
        return bosses.size();
    }

    // ------------------------------------------------------------------ //
    // Tracking
    // ------------------------------------------------------------------ //

    private void track(Mob mob, BossType type) {
        if (!bosses.containsKey(mob.getUniqueId())) {
            bosses.put(mob.getUniqueId(), new ActiveBoss(mob, type));
        }
    }

    private void forget(ActiveBoss boss) {
        bosses.remove(boss.entity.getUniqueId());
        for (UUID id : boss.viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(boss.bar);
            }
        }
        boss.viewers.clear();
    }

    private void adopt(List<Entity> entities) {
        for (Entity e : entities) {
            if (e instanceof Mob mob) {
                BossType type = BossType.byId(String.valueOf(
                        mob.getPersistentDataContainer().get(Keys.BOSS, PersistentDataType.STRING)));
                if (type != null && !mob.isDead()) {
                    track(mob, type);
                }
            }
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        adopt(event.getEntities());
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity e : event.getEntities()) {
            ActiveBoss boss = bosses.get(e.getUniqueId());
            if (boss != null) {
                forget(boss);
            }
        }
    }

    private static Component title(BossType type, boolean enraged) {
        Component name = Component.text(type.displayName(), type.color(), TextDecoration.BOLD);
        return enraged ? name.append(Component.text(" (Enraged)", NamedTextColor.RED)) : name;
    }

    // ------------------------------------------------------------------ //
    // AI loop
    // ------------------------------------------------------------------ //

    private void tick() {
        for (ActiveBoss boss : List.copyOf(bosses.values())) {
            Mob mob = boss.entity;
            if (!mob.isValid() || mob.isDead()) {
                forget(boss);
                continue;
            }
            double max = mob.getAttribute(Attribute.MAX_HEALTH).getValue();
            boss.bar.progress((float) Math.max(0, Math.min(1, mob.getHealth() / max)));
            updateViewers(boss);

            if (!boss.enraged && mob.getHealth() <= max / 2) {
                enrage(boss);
            }

            Player target = acquireTarget(mob);
            boss.cooldown -= TICK_INTERVAL;
            if (target != null && boss.cooldown <= 0) {
                boss.cooldown = boss.enraged ? ENRAGED_COOLDOWN : ABILITY_COOLDOWN;
                castAbility(boss, target);
            }
        }
    }

    private void updateViewers(ActiveBoss boss) {
        Location at = boss.entity.getLocation();
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean near = p.getWorld().equals(at.getWorld())
                    && p.getLocation().distanceSquared(at) <= BAR_RANGE * BAR_RANGE;
            if (near && boss.viewers.add(p.getUniqueId())) {
                p.showBossBar(boss.bar);
            } else if (!near && boss.viewers.remove(p.getUniqueId())) {
                p.hideBossBar(boss.bar);
            }
        }
        boss.viewers.removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private void enrage(ActiveBoss boss) {
        boss.enraged = true;
        boss.bar.name(title(boss.type, true));
        Mob mob = boss.entity;
        AttributeInstance speed = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * 1.25);
        }
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 1.2f);
        mob.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, mob.getEyeLocation(), 12, 0.6, 0.4, 0.6);
        Component msg = Component.text(boss.type.displayName() + " is enraged!", NamedTextColor.RED, TextDecoration.BOLD);
        for (UUID id : boss.viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.sendMessage(msg);
            }
        }
    }

    private Player acquireTarget(Mob mob) {
        if (mob.getTarget() instanceof Player p && validTarget(p, mob)) {
            return p;
        }
        Player nearest = null;
        double best = AGGRO_RANGE * AGGRO_RANGE;
        for (Player p : mob.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(mob.getLocation());
            if (d < best && validTarget(p, mob)) {
                best = d;
                nearest = p;
            }
        }
        if (nearest != null) {
            mob.setTarget(nearest);
        }
        return nearest;
    }

    private static boolean validTarget(Player p, Mob mob) {
        return p.isValid() && !p.isDead() && p.getWorld().equals(mob.getWorld())
                && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                && p.getLocation().distanceSquared(mob.getLocation()) <= AGGRO_RANGE * AGGRO_RANGE;
    }

    private void castAbility(ActiveBoss boss, Player target) {
        int roll = ThreadLocalRandom.current().nextInt(3);
        switch (boss.type) {
            case LICH_KING -> {
                switch (roll) {
                    case 0 -> summonMinions(boss.entity, Skeleton.class, 3);
                    case 1 -> skullVolley(boss.entity, target);
                    default -> blink(boss.entity, target);
                }
            }
            case MOLTEN_TYRANT -> {
                switch (roll) {
                    case 0 -> fireballBarrage(boss.entity, target);
                    case 1 -> groundSlam(boss.entity);
                    default -> flameRing(boss.entity);
                }
            }
            case FROST_QUEEN -> {
                switch (roll) {
                    case 0 -> blizzard(boss.entity);
                    case 1 -> iceShards(boss.entity, target);
                                        default -> summonMinions(boss.entity, Stray.class, 2);
                }
            }
            case MOSSHEART_BEHEMOTH -> {
                switch (roll) {
                    case 0 -> rootSnare(boss.entity);
                    case 1 -> sporeBurst(boss.entity);
                    default -> summonMinions(boss.entity, CaveSpider.class, 3);
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Abilities
    // ------------------------------------------------------------------ //

    private void summonMinions(Mob boss, Class<? extends Mob> kind, int count) {
        long existing = boss.getNearbyEntities(30, 15, 30).stream()
                .filter(e -> e.getPersistentDataContainer().has(Keys.MINION, PersistentDataType.STRING))
                .count();
        int toSpawn = (int) Math.min(count, MAX_MINIONS - existing);
        World world = boss.getWorld();
        String bossId = boss.getPersistentDataContainer().get(Keys.BOSS, PersistentDataType.STRING);
        for (int i = 0; i < toSpawn; i++) {
            double a = Math.PI * 2 * i / Math.max(1, toSpawn);
            Location at = boss.getLocation().add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5);
            if (!at.getBlock().isPassable() || !at.clone().add(0, 1, 0).getBlock().isPassable()) {
                at = boss.getLocation();
            }
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, at.clone().add(0, 0.5, 0), 20, 0.3, 0.5, 0.3, 0.02);
            world.spawn(at, kind, m -> {
                m.getPersistentDataContainer().set(Keys.MINION, PersistentDataType.STRING, String.valueOf(bossId));
                m.setTarget(boss.getTarget());
                EntityEquipment eq = m.getEquipment();
                eq.setHelmetDropChance(0f);
                eq.setItemInMainHandDropChance(0f);
            });
        }
        world.playSound(boss.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.2f, 0.8f);
    }

    private void skullVolley(Mob boss, Player target) {
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.2f, 0.7f);
        for (int i = 0; i < 3; i++) {
            int delay = i * 6;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!boss.isValid() || !target.isValid()) {
                    return;
                }
                WitherSkull skull = boss.launchProjectile(WitherSkull.class, aim(boss, target, 1.0));
                tag(skull, "lich_skull");
            }, delay);
        }
    }

    private void blink(Mob boss, Player target) {
        World world = boss.getWorld();
        Vector behind = target.getLocation().getDirection().setY(0);
        if (behind.lengthSquared() < 0.01) {
            behind = new Vector(1, 0, 0);
        }
        Location to = target.getLocation().subtract(behind.normalize().multiply(2.5));
        to.setY(target.getLocation().getY());
        if (!to.getBlock().isPassable() || !to.clone().add(0, 1, 0).getBlock().isPassable()) {
            return;
        }
        world.spawnParticle(Particle.PORTAL, boss.getLocation().add(0, 1, 0), 60, 0.4, 1, 0.4, 0.5);
        boss.teleport(to.setDirection(target.getLocation().toVector().subtract(to.toVector())));
        world.spawnParticle(Particle.PORTAL, to.clone().add(0, 1, 0), 60, 0.4, 1, 0.4, 0.5);
        world.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
    }

    private void fireballBarrage(Mob boss, Player target) {
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.6f);
        for (int i = 0; i < 4; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!boss.isValid() || !target.isValid()) {
                    return;
                }
                SmallFireball ball = boss.launchProjectile(SmallFireball.class, aim(boss, target, 1.0));
                tag(ball, "molten_fireball");
            }, i * 5L);
        }
    }

    private void groundSlam(Mob boss) {
        World world = boss.getWorld();
        Location at = boss.getLocation();
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
        world.spawnParticle(Particle.EXPLOSION, at, 6, 1.5, 0.2, 1.5);
        world.spawnParticle(Particle.LAVA, at, 20, 2, 0.2, 2);
        for (Player p : nearbyPlayers(boss, 6)) {
            p.damage(6, boss);
            Vector away = p.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) {
                away = new Vector(1, 0, 0);
            }
            p.setVelocity(away.normalize().multiply(1.2).setY(0.8));
        }
    }

    private void flameRing(Mob boss) {
        World world = boss.getWorld();
        Location at = boss.getLocation();
        world.playSound(at, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.6f);
        for (double r = 1; r <= 8; r += 1.5) {
            for (int i = 0; i < 28; i++) {
                double a = Math.PI * 2 * i / 28;
                world.spawnParticle(Particle.FLAME, at.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 1, 0, 0.1, 0, 0.01);
            }
        }
        for (Player p : nearbyPlayers(boss, 8)) {
            p.setFireTicks(Math.max(p.getFireTicks(), 100));
            p.damage(3, boss);
        }
    }

    private void blizzard(Mob boss) {
        World world = boss.getWorld();
        Location at = boss.getLocation();
        world.playSound(at, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.5f, 0.6f);
        world.playSound(at, Sound.BLOCK_POWDER_SNOW_BREAK, 1.5f, 0.6f);
        world.spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 2, 0), 200, 6, 2, 6, 0.02);
        for (Player p : nearbyPlayers(boss, 10)) {
            p.damage(3, boss);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2));
            p.setFreezeTicks(Math.min(p.getMaxFreezeTicks() + 60, p.getFreezeTicks() + 140));
        }
    }

    private void iceShards(Mob boss, Player target) {
        boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f, 1.4f);
        Vector base = aim(boss, target, 1.6);
        for (int i = -2; i <= 2; i++) {
            Vector dir = base.clone().rotateAroundY(Math.toRadians(i * 8));
            Snowball shard = boss.launchProjectile(Snowball.class, dir);
            shard.setItem(items.create("frost_crystal", 1));
            tag(shard, "frost_shard");
        }
    }

    /** Roots burst from the ground under nearby players: damage, heavy slow and a yank toward the boss. */
    private void rootSnare(Mob boss) {
        World world = boss.getWorld();
        Location at = boss.getLocation();
        world.playSound(at, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.5f, 0.5f);
        world.playSound(at, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.8f);
        for (Player p : nearbyPlayers(boss, 12)) {
            Location feet = p.getLocation();
            world.spawnParticle(Particle.BLOCK, feet, 40, 0.6, 0.3, 0.6, 0.1, Material.ROOTED_DIRT.createBlockData());
            world.spawnParticle(Particle.BLOCK, feet.clone().add(0, 0.5, 0), 20, 0.4, 0.5, 0.4, 0.1,
                    Material.MANGROVE_ROOTS.createBlockData());
            p.damage(4, boss);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 3));
            Vector pull = at.toVector().subtract(feet.toVector()).setY(0);
            if (pull.lengthSquared() > 4) {
                p.setVelocity(pull.normalize().multiply(0.9).setY(0.35));
            }
        }
    }

    /** A cloud of choking spores around the boss. */
    private void sporeBurst(Mob boss) {
        World world = boss.getWorld();
        Location at = boss.getLocation().add(0, 1, 0);
        world.playSound(at, Sound.ENTITY_PUFFER_FISH_BLOW_UP, 1.5f, 0.5f);
        world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, at, 250, 5, 1.5, 5, 0);
        world.spawnParticle(Particle.FALLING_SPORE_BLOSSOM, at.clone().add(0, 2, 0), 120, 5, 1, 5, 0);
        for (Player p : nearbyPlayers(boss, 8)) {
            p.damage(2, boss);
            p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 80, 0));
        }
    }

    private static List<Player> nearbyPlayers(Mob boss, double radius) {
        List<Player> list = new ArrayList<>();
        for (Player p : boss.getWorld().getPlayers()) {
            if (validTarget(p, boss) && p.getLocation().distanceSquared(boss.getLocation()) <= radius * radius) {
                list.add(p);
            }
        }
        return list;
    }

    private static Vector aim(Mob from, Player to, double speed) {
        Vector dir = to.getEyeLocation().toVector().subtract(from.getEyeLocation().toVector());
        return dir.lengthSquared() < 0.01 ? from.getLocation().getDirection() : dir.normalize().multiply(speed);
    }

    private static void tag(Projectile projectile, String id) {
        projectile.getPersistentDataContainer().set(Keys.PROJECTILE, PersistentDataType.STRING, id);
    }

    private static String tagOf(Entity entity) {
        return entity == null ? null
                : entity.getPersistentDataContainer().get(Keys.PROJECTILE, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ //
    // Events
    // ------------------------------------------------------------------ //

    @EventHandler
    public void onShardHit(ProjectileHitEvent event) {
        if (!"frost_shard".equals(tagOf(event.getEntity()))) {
            return;
        }
        if (event.getHitEntity() instanceof LivingEntity hit && !isBossOrMinion(hit)) {
            Entity shooter = event.getEntity().getShooter() instanceof Entity s ? s : null;
            if (shooter != null) {
                hit.damage(4, shooter);
            } else {
                hit.damage(4);
            }
            hit.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            hit.getWorld().spawnParticle(Particle.SNOWFLAKE, hit.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
        }
    }

    /** Boss fireballs never set blocks alight. */
    @EventHandler
    public void onIgnite(BlockIgniteEvent event) {
        if ("molten_fireball".equals(tagOf(event.getIgnitingEntity()))) {
            event.setCancelled(true);
        }
    }

    /** Bosses and minions don't burn in daylight. */
    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        if (event instanceof EntityCombustByEntityEvent || event instanceof EntityCombustByBlockEvent) {
            return;
        }
        if (isBossOrMinion(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Bosses and their minions never fight each other. */
    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (isBossOrMinion(event.getEntity()) && event.getTarget() != null && isBossOrMinion(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        PersistentDataContainer pdc = dead.getPersistentDataContainer();

        String minionOf = pdc.get(Keys.MINION, PersistentDataType.STRING);
        if (minionOf != null) {
            event.getDrops().clear();
                        event.setDroppedExp(3);
            if (BossType.MOSSHEART_BEHEMOTH.id().equals(minionOf)) {
                if (ThreadLocalRandom.current().nextDouble() < 0.1) {
                    event.getDrops().add(items.create("health_potion", 1));
                }
            } else if (ThreadLocalRandom.current().nextDouble() < 0.4) {
                event.getDrops().add(items.create(
                        BossType.FROST_QUEEN.id().equals(minionOf) ? "frost_crystal" : "bone_shard", 1));
            }
            return;
        }

        BossType type = BossType.byId(String.valueOf(pdc.get(Keys.BOSS, PersistentDataType.STRING)));
        if (type == null) {
            return;
        }
        ActiveBoss boss = bosses.get(dead.getUniqueId());
        if (boss != null) {
            forget(boss);
        }
        event.getDrops().clear();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (BossType.Drop drop : type.drops()) {
            if (random.nextDouble() < drop.chance()) {
                event.getDrops().add(items.create(drop.item(), random.nextInt(drop.min(), drop.max() + 1)));
            }
        }
        event.setDroppedExp(500);

        Player killer = dead.getKiller();
        Component msg = Component.text(type.displayName(), type.color(), TextDecoration.BOLD)
                .append(Component.text(killer == null ? " has been defeated!"
                        : " has been defeated by " + killer.getName() + "!", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.BOLD, false));
        Bukkit.broadcast(msg);
        dead.getWorld().playSound(dead.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1f);
        dead.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, dead.getLocation().add(0, 1, 0), 120, 1, 1.5, 1, 0.4);
    }

    private static boolean isBossOrMinion(Entity e) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        return pdc.has(Keys.BOSS, PersistentDataType.STRING) || pdc.has(Keys.MINION, PersistentDataType.STRING);
    }
}
