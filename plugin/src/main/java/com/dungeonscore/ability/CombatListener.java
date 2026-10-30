package com.dungeonscore.ability;

import com.dungeonscore.Keys;
import com.dungeonscore.item.ItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Zoglin;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.ZombieHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Melee weapon, bow, armor set and artifact abilities. Numbers must match the lore in ItemRegistry.
 */
public final class CombatListener implements Listener {

    private final Plugin plugin;
    private final ItemRegistry items;
    private final Set<AbstractArrow> trails = new HashSet<>();
    private final BukkitTask trailTask;
    private final BukkitTask armorTask;

    public CombatListener(Plugin plugin, ItemRegistry items) {
        this.plugin = plugin;
        this.items = items;
        trailTask = Bukkit.getScheduler().runTaskTimer(plugin, this::drawTrails, 2L, 2L);
        armorTask = Bukkit.getScheduler().runTaskTimer(plugin, this::armorTick, 20L, 20L);
    }

    public void stop() {
        trailTask.cancel();
        armorTask.cancel();
    }

    // ------------------------------------------------------------------ //
    // Melee weapons
    // ------------------------------------------------------------------ //

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        String id = items.idOf(player.getInventory().getItemInMainHand());
        if (id == null) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        World world = target.getWorld();
        Location mid = target.getLocation().add(0, target.getHeight() / 2, 0);
        switch (id) {
            case "crypt_cleaver" -> {
                if (isUndead(target)) {
                    event.setDamage(event.getDamage() * 1.3);
                    world.spawnParticle(Particle.SOUL, mid, 4, 0.2, 0.3, 0.2, 0.01);
                }
            }
            case "ember_blade" -> {
                target.setFireTicks(Math.max(target.getFireTicks(), 80));
                world.spawnParticle(Particle.FLAME, mid, 8, 0.2, 0.3, 0.2, 0.02);
            }
            case "frostbite_dagger" -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
                freeze(target, 60);
                world.spawnParticle(Particle.SNOWFLAKE, mid, 6, 0.2, 0.3, 0.2, 0.01);
            }
            case "thunder_hammer" -> {
                if (random.nextDouble() < 0.2) {
                    world.strikeLightningEffect(target.getLocation());
                    event.setDamage(event.getDamage() + 5);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, mid, 20, 0.4, 0.6, 0.4, 0.1);
                }
            }
            case "soulreaper_scythe" -> {
                StaffListener.heal(player, event.getDamage() * 0.2);
                world.spawnParticle(Particle.SOUL, mid, 5, 0.2, 0.3, 0.2, 0.02);
            }
            case "void_greatsword" -> {
                if (random.nextDouble() < 0.2) {
                    event.setDamage(event.getDamage() * 1.5);
                    world.spawnParticle(Particle.PORTAL, mid, 40, 0.3, 0.5, 0.3, 0.5);
                    world.playSound(mid, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
                }
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ //
    // Bows
    // ------------------------------------------------------------------ //

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        String id = items.idOf(event.getBow());
        if (id == null || !(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        switch (id) {
            case "bone_bow" -> {
                arrow.setPierceLevel(Math.min(127, arrow.getPierceLevel() + 1));
                arrow.setDamage(arrow.getDamage() + 1);
            }
            case "inferno_longbow" -> arrow.setFireTicks(2000);
            case "frostfang_bow" -> { }
            default -> {
                return;
            }
        }
        arrow.getPersistentDataContainer().set(Keys.PROJECTILE, PersistentDataType.STRING, id);
        trails.add(arrow);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof AbstractArrow arrow) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        String id = arrow.getPersistentDataContainer().get(Keys.PROJECTILE, PersistentDataType.STRING);
        if ("frostfang_bow".equals(id)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            freeze(target, 100);
            target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
        } else if ("inferno_longbow".equals(id)) {
            target.setFireTicks(Math.max(target.getFireTicks(), 100));
        }
    }

    @EventHandler
    public void onArrowLand(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        String id = arrow.getPersistentDataContainer().get(Keys.PROJECTILE, PersistentDataType.STRING);
        if (!"inferno_longbow".equals(id)) {
            return;
        }
        Location at = arrow.getLocation();
        World world = arrow.getWorld();
        world.spawnParticle(Particle.FLAME, at, 30, 0.8, 0.4, 0.8, 0.03);
        world.spawnParticle(Particle.LAVA, at, 6, 0.5, 0.2, 0.5);
        world.playSound(at, Sound.ENTITY_BLAZE_SHOOT, 0.7f, 0.8f);
        ProjectileSource shooter = arrow.getShooter();
        for (Entity e : world.getNearbyEntities(at, 2.5, 2, 2.5)) {
            if (e instanceof LivingEntity le && e != shooter && !(e instanceof Player && shooter instanceof Player)) {
                le.setFireTicks(Math.max(le.getFireTicks(), 60));
            }
        }
    }

    private void drawTrails() {
        Iterator<AbstractArrow> it = trails.iterator();
        while (it.hasNext()) {
            AbstractArrow arrow = it.next();
            if (!arrow.isValid() || arrow.isInBlock() || arrow.isOnGround()) {
                it.remove();
                continue;
            }
            String id = arrow.getPersistentDataContainer().get(Keys.PROJECTILE, PersistentDataType.STRING);
            Particle particle = switch (String.valueOf(id)) {
                case "frostfang_bow" -> Particle.SNOWFLAKE;
                case "inferno_longbow" -> Particle.FLAME;
                default -> Particle.CRIT;
            };
            arrow.getWorld().spawnParticle(particle, arrow.getLocation(), 2, 0.05, 0.05, 0.05, 0);
        }
    }

    // ------------------------------------------------------------------ //
    // Armor set bonuses
    // ------------------------------------------------------------------ //

    /** Returns the set prefix (e.g. "infernal") if all four armor pieces belong to it, else null. */
    private String fullSet(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return null;
        }
        PlayerInventory inv = player.getInventory();
        String helmet = items.idOf(inv.getHelmet());
        if (helmet == null || !helmet.endsWith("_helmet")) {
            return null;
        }
        String set = helmet.substring(0, helmet.length() - "_helmet".length());
        if ((set + "_chestplate").equals(items.idOf(inv.getChestplate()))
                && (set + "_leggings").equals(items.idOf(inv.getLeggings()))
                && (set + "_boots").equals(items.idOf(inv.getBoots()))) {
            return set;
        }
        return null;
    }

    private void armorTick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String set = fullSet(player);
            if ("infernal".equals(set)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 0, true, false, true));
            } else if ("frostforged".equals(set) && player.getFreezeTicks() > 0) {
                player.setFreezeTicks(0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWearerHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        String set = fullSet(player);
        if (set == null) {
            return;
        }
        if (set.equals("frostforged") && event.getCause() == EntityDamageEvent.DamageCause.FREEZE) {
            event.setCancelled(true);
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return;
        }
        Entity attacker = byEntity.getDamager();
        if (attacker instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            attacker = shooter;
        }
        switch (set) {
            case "crypt_warden" -> {
                if (attacker instanceof LivingEntity le && isUndead(le)) {
                    event.setDamage(event.getDamage() * 0.75);
                }
            }
            case "frostforged" -> {
                if (byEntity.getDamager() instanceof LivingEntity melee && melee != player) {
                    melee.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                }
            }
            case "infernal" -> {
                if (byEntity.getDamager() instanceof LivingEntity melee && melee != player) {
                    melee.setFireTicks(Math.max(melee.getFireTicks(), 60));
                }
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ //
    // Artifacts
    // ------------------------------------------------------------------ //

    @EventHandler
    public void onHorn(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!"wind_horn".equals(items.idOf(item)) || player.hasCooldown(Material.GOAT_HORN)) {
            return;
        }
        World world = player.getWorld();
        Location center = player.getLocation();
        world.spawnParticle(Particle.CLOUD, center.clone().add(0, 1, 0), 60, 2.5, 0.6, 2.5, 0.15);
        world.spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(0, 1, 0), 8, 2, 0.3, 2);
        for (Entity e : player.getNearbyEntities(8, 4, 8)) {
            if (e instanceof LivingEntity le && !(e instanceof Player)) {
                Vector away = le.getLocation().toVector().subtract(center.toVector()).setY(0);
                if (away.lengthSquared() < 0.01) {
                    away = new Vector(1, 0, 0);
                }
                le.setVelocity(away.normalize().multiply(1.8).setY(0.6));
            }
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotem(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getHand() == null) {
            return;
        }
        ItemStack totem = player.getInventory().getItem(event.getHand());
        if (!"totem_of_shielding".equals(items.idOf(totem))) {
            return;
        }
        // Vanilla clears effects when the totem fires, so apply ours a tick later.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.isDead()) {
                return;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 3));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 1));
            World world = player.getWorld();
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 80, 1.5, 1, 1.5, 0.3);
            world.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
            for (Entity e : player.getNearbyEntities(6, 3, 6)) {
                if (e instanceof LivingEntity le && !(e instanceof Player)) {
                    Vector away = le.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
                    if (away.lengthSquared() > 0.01) {
                        le.setVelocity(away.normalize().multiply(1.4).setY(0.5));
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------------ //
    // Helpers
    // ------------------------------------------------------------------ //

    static boolean isUndead(LivingEntity e) {
        return e instanceof Zombie || e instanceof AbstractSkeleton || e instanceof Phantom || e instanceof Wither
                || e instanceof Zoglin || e instanceof SkeletonHorse || e instanceof ZombieHorse;
    }

    private static void freeze(LivingEntity target, int ticks) {
        target.setFreezeTicks(Math.min(target.getMaxFreezeTicks() + 40, target.getFreezeTicks() + ticks));
    }

}
