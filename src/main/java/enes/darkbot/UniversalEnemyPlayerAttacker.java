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
    private UniversalAttackerConfig config;

    public UniversalEnemyPlayerAttacker(HeroAPI hero, AttackAPI attack, EntitiesAPI entities) {
        this.hero = hero;
        this.attack = attack;
        this.entities = entities;
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

        // Keybind check (Basic implementation, relying on DarkBot's internal handling
        // if possible, otherwise just config)
        // Note: Direct key checking might need specific API access not fully visible
        // here,
        // effectively relying on config.lockKey being set/handled by the bot's input
        // system if mapped.
        // For now, we proceed with automatic logic roughly detailed.

        Collection<? extends Player> players = entities.getPlayers();

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

        if (closestEnemy != null) {
            if (attack.getTarget() != closestEnemy) {
                attack.setTarget(closestEnemy);
                attack.tryLockAndAttack();
                hero.setLocalTarget(closestEnemy);
                logTarget(closestEnemy);
            } else if (!attack.isAttacking()) {
                attack.tryLockAndAttack();
            }
        } else {
            if (attack.hasTarget() || hero.getLocalTarget() != null) {
                attack.stopAttack();
                hero.setLocalTarget(null);
            }
        }
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
