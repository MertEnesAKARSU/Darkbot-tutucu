package enes.darkbot;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.annotations.Dropdown;

@Configuration("Universal Enemy Attacker Settings")
public class UniversalAttackerConfig {

    @Option("Enable Plugin")
    public boolean enable = true;

    @Option("Attack Range")
    @Number(min = 500, max = 5000, step = 100)
    public int range = 1800;

    @Option("Priority")
    @Dropdown
    public Priority priority = Priority.CLOSEST;

    @Option("Ignore Cloaked Players")
    public boolean ignoreCloaked = false;

    @Option("Whitelist (Friends)")
    public java.util.Set<String> whitelist = new java.util.HashSet<>();

    @Option("Blacklist (Enemies)")
    public java.util.Set<String> blacklist = new java.util.HashSet<>();

    @Option("Use Whitelist")
    public boolean useWhitelist = false;

    @Option("Use Blacklist")
    public boolean useBlacklist = false;

    @Option("Lock Key (Press to Lock)")
    public Character lockKey = null;

    public enum Priority {
        CLOSEST, LOWEST_HEALTH
    }
}
