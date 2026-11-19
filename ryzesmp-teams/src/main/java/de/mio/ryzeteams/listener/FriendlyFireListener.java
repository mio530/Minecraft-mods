package de.mio.ryzeteams.listener;

import de.mio.ryzeteams.service.TeamManager;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.potion.PotionEffect;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side friendly-fire protection between team mates and allied teams.
 * Covers melee, projectiles (arrows, tridents), TNT, fire and harmful potions
 * by resolving the responsible player. Players outside a team are never affected.
 */
public final class FriendlyFireListener implements Listener {

    private static final Set<String> HARMFUL_EFFECTS = Set.of(
            "instant_damage", "poison", "wither", "slowness", "weakness",
            "mining_fatigue", "nausea", "blindness", "hunger", "darkness",
            "levitation", "unluck", "infested", "oozing", "wind_charged", "weaving");

    private final TeamManager teams;

    public FriendlyFireListener(TeamManager teams) {
        this.teams = teams;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(causingEntity(e), e.getDamager());
        if (attacker != null && protect(attacker.getUniqueId(), victim.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(e.getCombuster(), e.getCombuster());
        if (attacker != null && protect(attacker.getUniqueId(), victim.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent e) {
        if (!isHarmful(e.getPotion())) {
            return;
        }
        ProjectileSource src = e.getPotion().getShooter();
        if (!(src instanceof Player shooter)) {
            return;
        }
        for (Entity affected : e.getAffectedEntities()) {
            if (affected instanceof Player p && protect(shooter.getUniqueId(), p.getUniqueId())) {
                e.setIntensity(p, 0.0);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent e) {
        if (!(e.getEntity() instanceof AreaEffectCloud cloud)) {
            return;
        }
        ProjectileSource src = cloud.getSource();
        if (!(src instanceof Player shooter)) {
            return;
        }
        e.getAffectedEntities().removeIf(entity ->
                entity instanceof Player p && protect(shooter.getUniqueId(), p.getUniqueId()));
    }

    // ---- helpers ------------------------------------------------------------

    private boolean protect(UUID attacker, UUID victim) {
        return teams.isProtected(attacker, victim);
    }

    private Entity causingEntity(EntityDamageByEntityEvent e) {
        try {
            return e.getDamageSource().getCausingEntity();
        } catch (Throwable ignored) {
            return e.getDamager();
        }
    }

    private Player resolveAttacker(Entity causing, Entity direct) {
        Entity e = causing != null ? causing : direct;
        if (e instanceof Player p) {
            return p;
        }
        if (e instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        if (e instanceof Tameable t && t.getOwner() instanceof Player p) {
            return p;
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private boolean isHarmful(ThrownPotion potion) {
        for (PotionEffect effect : potion.getEffects()) {
            String key = effect.getType().getKey().getKey().toLowerCase(Locale.ROOT);
            if (HARMFUL_EFFECTS.contains(key)) {
                return true;
            }
        }
        return false;
    }
}
