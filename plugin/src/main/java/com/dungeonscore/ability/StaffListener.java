package com.dungeonscore.ability;

import com.dungeonscore.Keys;
import com.dungeonscore.item.ItemRegistry;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Right-click spells for wands and staves. Numbers must match the lore in ItemRegistry. */
public final class StaffListener implements Listener {

    private final ItemRegistry items;
    private final ManaManager mana;
    /** player -> item id -> time (ms) the spell is ready again */
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public StaffListener(ItemRegistry items, ManaManager mana) {
        this.items = items;
        this.mana = mana;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        String id = items.idOf(player.getInventory().getItemInMainHand());
        if (id == null) {
            return;
        }
        switch (id) {
            case "apprentice_wand" -> cast(event, player, id, 8, 500, () -> arcaneBolt(player));
            case "necro_staff" -> cast(event, player, id, 18, 1000, () -> soulDrain(player));
            case "frost_staff" -> cast(event, player, id, 25, 4000, () -> frostNova(player));
            case "staff_of_renewal" -> cast(event, player, id, 30, 8000, () -> renewal(player));
            case "lich_scepter" -> cast(event, player, id, 30, 2000, () -> deathSkull(player));
            default -> { }
        }
    }

    private void cast(PlayerInteractEvent event, Player player, String id, double cost, long cooldownMs,
                      Runnable spell) {
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        Map<String, Long> mine = cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        if (mine.getOrDefault(id, 0L) > now) {
            return;
        }
        if (!mana.tryConsume(player, cost)) {
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.4f, 1.6f);
            return;
        }
        mine.put(id, now + cooldownMs);
        spell.run();
    }

    // ------------------------------------------------------------------ //
    // Spells
    // ------------------------------------------------------------------ //

    private void arcaneBolt(Player player) {
        LivingEntity target = bolt(player, 24, new Particle.DustOptions(Color.fromRGB(0x7ab8ff), 1.0f));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f, 1.6f);
        if (target != null) {
            magicDamage(target, player, 5);
            target.getWorld().spawnParticle(Particle.ENCHANT, target.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3);
        }
    }

    private void soulDrain(Player player) {
        LivingEntity target = bolt(player, 20, new Particle.DustOptions(Color.fromRGB(0x62ff9a), 1.2f));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.8f, 0.8f);
        if (target != null) {
            magicDamage(target, player, 6);
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
            target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.02);
            heal(player, 3);
        }
    }

    private void frostNova(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation();
        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
        world.playSound(center, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1f);
        for (double r = 1; r <= 6; r += 1) {
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2 * i / 24;
                world.spawnParticle(Particle.SNOWFLAKE, center.clone().add(Math.cos(a) * r, 0.3, Math.sin(a) * r),
                        1, 0, 0.1, 0, 0);
            }
        }
        for (Entity e : player.getNearbyEntities(6, 3, 6)) {
            if (e instanceof LivingEntity target && isEnemy(player, target)) {
                magicDamage(target, player, 4);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                target.setFreezeTicks(Math.min(target.getMaxFreezeTicks() + 60, target.getFreezeTicks() + 160));
            }
        }
    }

    private void renewal(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(player.getLocation()) <= 8 * 8) {
                heal(p, 6);
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0));
                world.spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 5, 0.4, 0.3, 0.4);
                world.spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 15, 0.5, 0.6, 0.5);
            }
        }
    }

    private void deathSkull(Player player) {
        WitherSkull skull = player.launchProjectile(WitherSkull.class,
                player.getEyeLocation().getDirection().multiply(1.2));
        skull.getPersistentDataContainer().set(Keys.PROJECTILE, PersistentDataType.STRING, "lich_scepter");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.7f, 1.2f);
    }

    /** Spell projectiles hurt mobs but never break blocks. */
    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(Keys.PROJECTILE, PersistentDataType.STRING)) {
            event.blockList().clear();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ //
    // Helpers
    // ------------------------------------------------------------------ //

    /** Instant hitscan with a particle trail. Returns the entity hit, if any. */
    private LivingEntity bolt(Player player, double range, Particle.DustOptions dust) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult hit = player.getWorld().rayTrace(eye, dir, range, FluidCollisionMode.NEVER, true, 0.4,
                e -> e instanceof LivingEntity le && e != player && isEnemy(player, le));
        double length = hit == null ? range : hit.getHitPosition().distance(eye.toVector());
        for (double d = 0.5; d < length; d += 0.35) {
            player.getWorld().spawnParticle(Particle.DUST, eye.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0, dust);
        }
        return hit != null && hit.getHitEntity() instanceof LivingEntity le ? le : null;
    }

    static boolean isEnemy(Player caster, LivingEntity target) {
        if (target instanceof Player || target instanceof ArmorStand || target.isDead()) {
            return false;
        }
        return !(target instanceof Tameable pet && caster.equals(pet.getOwner()));
    }

    static void magicDamage(LivingEntity target, Player source, double amount) {
        target.damage(amount, DamageSource.builder(DamageType.INDIRECT_MAGIC)
                .withCausingEntity(source)
                .withDirectEntity(source)
                .build());
    }

    static void heal(LivingEntity entity, double amount) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        entity.setHealth(Math.min(cap, entity.getHealth() + amount));
    }
}
