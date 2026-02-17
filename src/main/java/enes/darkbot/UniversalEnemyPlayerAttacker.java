package enes.darkbot;

import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.Module;
import eu.darkbot.api.game.entities.Player;
import eu.darkbot.api.game.other.EntityInfo;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.config.ConfigSetting;

import java.util.Collection;
import java.util.Comparator;

@Feature(name = "Universal Enemy Player Attacker", description = "Bütün haritalarda en yakın düşman oyuncuya (cloaked/görünmez dahil) kilitlenir ve saldırır")
public class UniversalEnemyPlayerAttacker implements Module, Configurable<UniversalAttackerConfig> {

    private final HeroAPI hero;
    private final AttackAPI attack;
    private final EntitiesAPI entities;
    private final eu.darkbot.api.managers.HeroItemsAPI items;
    private UniversalAttackerConfig config;

    public UniversalEnemyPlayerAttacker(HeroAPI hero, AttackAPI attack, EntitiesAPI entities,
            eu.darkbot.api.managers.HeroItemsAPI items) {
        this.hero = hero;
        this.attack = attack;
        this.entities = entities;
        this.items = items;
    }

    @Override
    public void setConfig(ConfigSetting<UniversalAttackerConfig> arg0) {
        this.config = arg0.getValue();
    }

    @Override
    public void onTickModule() {
        if (config == null || !config.enable) {
            return;
        }

        // Target Selection
        eu.darkbot.api.game.other.Lockable target = getBestTarget();

        if (target != null) {
            if (attack.getTarget() != target) {
                attack.setTarget(target);
                attack.tryLockAndAttack();
                hero.setLocalTarget(target);
                // Log only if it's a Player to avoid spamming console for every NPC
                if (target instanceof Player) {
                    logTarget((Player) target);
                }
            } else if (!attack.isAttacking()) {
                attack.tryLockAndAttack();
            }

            // Ammo Logic
            handleAmmoLogic(target);

        } else {
            if (attack.hasTarget() || hero.getLocalTarget() != null) {
                attack.stopAttack();
                hero.setLocalTarget(null);
            }
        }
    }

    private eu.darkbot.api.game.other.Lockable getBestTarget() {
        Collection<? extends Player> players = entities.getPlayers();

        // 1. Check for Enemy Players
        Player closestEnemy = players.stream()
                .filter(p -> p.isValid())
                .filter(p -> p.getHealth().getHp() > 0)
                .filter(p -> p.getLocationInfo().distanceTo(hero) <= config.range)
                .filter(p -> p.getEntityInfo().getClanDiplomacy() != EntityInfo.Diplomacy.ALLIED)
                .filter(p -> !config.ignoreCloaked || !p.isInvisible())
                .filter(p -> !config.useWhitelist || !config.whitelist.contains(p.getEntityInfo().getUsername()))
                .filter(p -> !config.useBlacklist || config.blacklist.contains(p.getEntityInfo().getUsername()))
                .min(getPriorityComparator())
                .orElse(null);

        if (closestEnemy != null)
            return closestEnemy;

        // 2. Check for NPCs (if enabled and no enemy player found)
        if (config.attackNpcs) {
            return entities.getNpcs().stream()
                    .filter(n -> n.isValid())
                    .filter(n -> n.getHealth().getHp() > 0)
                    .filter(n -> n.getLocationInfo().distanceTo(hero) <= config.range)
                    .min(Comparator.comparingDouble(n -> n.getLocationInfo().distanceTo(hero)))
                    .orElse(null);
        }

        return null;
    }

    private void handleAmmoLogic(eu.darkbot.api.game.other.Lockable target) {
        if (target == null || config.initialAmmo == null || config.secondAmmo == null)
            return;

        double shieldPercent = target.getHealth().getShield() / (double) target.getHealth().getMaxShield() * 100;

        eu.darkbot.api.game.items.SelectableItem.Laser desiredAmmo;
        if (shieldPercent > config.ammoSwitchAmount) {
            desiredAmmo = config.initialAmmo;
        } else {
            desiredAmmo = config.secondAmmo;
        }

        items.useItem(desiredAmmo, eu.darkbot.api.game.items.ItemFlag.USABLE, eu.darkbot.api.game.items.ItemFlag.READY);
    }

    private Comparator<Player> getPriorityComparator() {
        if (config.priority == UniversalAttackerConfig.Priority.LOWEST_HEALTH) {
            return Comparator.comparingDouble(p -> p.getHealth().getHp());
        }
        return Comparator.comparingDouble(p -> p.getLocationInfo().distanceTo(hero));
    }

    private void logTarget(Player target) {
        String mapId = hero.getMap() != null ? String.valueOf(hero.getMap().getId()) : "Unknown";
        String name = target.getEntityInfo().getUsername();
        boolean isCloaked = target.isInvisible();
        double distance = target.getLocationInfo().distanceTo(hero);
        String clanTag = target.getEntityInfo().getClanTag();

        System.out.println("Locked -> Map: " + mapId +
                ", Name: " + name +
                ", Cloaked: " + isCloaked +
                ", Dist: " + String.format("%.1f", distance) +
                ", Clan: " + (clanTag != null ? clanTag : "None"));
    }

    @Override
    public boolean canRefresh() {
        return true;
    }

    @Override
    public String getStatus() {
        if (config != null && !config.enable) {
            return "Disabled via settings";
        }
        return "Scanning for enemies...";
    }
}
