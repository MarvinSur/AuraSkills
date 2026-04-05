package dev.aurelium.auraskills.bukkit.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import dev.aurelium.auraskills.api.skill.Skill;
import dev.aurelium.auraskills.bukkit.AuraSkills;
import dev.aurelium.auraskills.common.message.MessageBuilder;
import dev.aurelium.auraskills.common.message.type.CommandMessage;
import dev.aurelium.auraskills.common.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

@CommandAlias("%skills_alias")
@Subcommand("skill")
public class SkillCommand extends BaseCommand {

    private final AuraSkills plugin;

    public SkillCommand(AuraSkills plugin) {
        this.plugin = plugin;
    }

    /**
     * Resolves a player by name — works for both online and offline players.
     * Returns null and sends an error to sender if not found.
     */
    private OfflinePlayer resolveOfflinePlayer(CommandSender sender, String playerName) {
        // Try online first (fast path)
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) return online;

        // Fall back to offline lookup (may return a player who has never joined,
        // but their UUID will have no User in UserManager — caught downstream)
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        if (offline.hasPlayedBefore() || offline.isOnline()) return offline;

        Locale locale = plugin.getDefaultLanguage();
        sender.sendMessage(plugin.getPrefix(locale) + "§cPlayer \"" + playerName + "\" not found.");
        return null;
    }

    /**
     * Gets the in-memory User for an OfflinePlayer.
     * Sends an error if user data is not loaded (player never joined or not cached).
     */
    private User resolveUser(CommandSender sender, OfflinePlayer offlinePlayer) {
        User user = plugin.getUserManager().getUser(offlinePlayer.getUniqueId());
        if (user == null) {
            Locale locale = plugin.getDefaultLanguage();
            String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : offlinePlayer.getUniqueId().toString();
            sender.sendMessage(plugin.getPrefix(locale) + "§cNo data found for player \"" + name + "\". They must have joined the server at least once.");
        }
        return user;
    }

    // -------------------------------------------------------------------------
    // setlevel — now accepts a String player name so console can call it too
    // -------------------------------------------------------------------------

    @Subcommand("setlevel")
    @CommandCompletion("@players @skills")
    @CommandPermission("auraskills.command.skill.setlevel")
    @Description("%desc_skill_setlevel")
    public void onSkillSetlevel(CommandSender sender, String playerName, Skill skill, int level) {
        OfflinePlayer offlinePlayer = resolveOfflinePlayer(sender, playerName);
        if (offlinePlayer == null) return;

        User user = resolveUser(sender, offlinePlayer);
        if (user == null) return;

        Locale locale = user.getLocale();

        if (!skill.isEnabled()) {
            sender.sendMessage(plugin.getPrefix(locale) + plugin.getMsg(CommandMessage.UNKNOWN_SKILL, locale));
            return;
        }

        int startLevel = plugin.config().getStartLevel();
        if (level < startLevel) {
            sender.sendMessage(MessageBuilder.create(plugin).locale(locale)
                    .prefix()
                    .message(CommandMessage.SKILL_AT_LEAST, "level", String.valueOf(startLevel))
                    .toString());
            return;
        }

        int oldLevel = user.getSkillLevel(skill);
        user.setSkillLevel(skill, level);
        user.setSkillXp(skill, 0);
        plugin.getStatManager().recalculateStats(user);
        plugin.getRewardManager().updatePermissions(user);
        plugin.getRewardManager().applyRevertCommands(user, skill, oldLevel, level);
        plugin.getRewardManager().applyLevelUpCommands(user, skill, oldLevel, level);

        // Reload item/armor modifiers only if the player is actually online
        if (offlinePlayer.isOnline()) {
            plugin.getModifierManager().applyModifiers(offlinePlayer.getPlayer(), true);
        }

        String displayName = offlinePlayer.getName() != null ? offlinePlayer.getName() : offlinePlayer.getUniqueId().toString();
        sender.sendMessage(plugin.getPrefix(locale) + plugin.getMsg(CommandMessage.SKILL_SETLEVEL_SET, locale)
                .replace("{skill}", skill.getDisplayName(locale))
                .replace("{level}", String.valueOf(level))
                .replace("{player}", displayName));
    }

    // -------------------------------------------------------------------------
    // addlevel — delegates to setlevel, now also console-compatible
    // -------------------------------------------------------------------------

    @Subcommand("addlevel")
    @CommandCompletion("@players @skills")
    @CommandPermission("auraskills.command.skill.setlevel")
    @Description("%desc_skill_addlevel")
    public void onSkillAddlevel(CommandSender sender, String playerName, Skill skill, int level) {
        OfflinePlayer offlinePlayer = resolveOfflinePlayer(sender, playerName);
        if (offlinePlayer == null) return;

        User user = resolveUser(sender, offlinePlayer);
        if (user == null) return;

        Locale locale = user.getLocale();
        if (!skill.isEnabled()) {
            sender.sendMessage(plugin.getPrefix(locale) + plugin.getMsg(CommandMessage.UNKNOWN_SKILL, locale));
            return;
        }

        int newLevel = user.getSkillLevel(skill) + level;
        this.onSkillSetlevel(sender, playerName, skill, newLevel);
    }

    // -------------------------------------------------------------------------
    // setall — now accepts a String player name so console can call it too
    // -------------------------------------------------------------------------

    @Subcommand("setall")
    @CommandCompletion("@players")
    @CommandPermission("auraskills.command.skill.setlevel")
    @Description("%desc_skill_setall")
    public void onSkillSetall(CommandSender sender, String playerName, int level) {
        OfflinePlayer offlinePlayer = resolveOfflinePlayer(sender, playerName);
        if (offlinePlayer == null) return;

        User user = resolveUser(sender, offlinePlayer);
        if (user == null) return;

        Locale locale = user.getLocale();
        int startLevel = plugin.config().getStartLevel();

        if (level < startLevel) {
            sender.sendMessage(MessageBuilder.create(plugin).locale(locale)
                    .prefix()
                    .message(CommandMessage.SKILL_AT_LEAST, "level", String.valueOf(startLevel))
                    .toString());
            return;
        }

        for (Skill skill : plugin.getSkillRegistry().getValues()) {
            if (skill.isEnabled()) {
                int oldLevel = user.getSkillLevel(skill);
                user.setSkillLevel(skill, level);
                user.setSkillXp(skill, 0);
                plugin.getRewardManager().applyRevertCommands(user, skill, oldLevel, level);
                plugin.getRewardManager().applyLevelUpCommands(user, skill, oldLevel, level);
            }
        }
        plugin.getStatManager().recalculateStats(user);
        plugin.getRewardManager().updatePermissions(user);

        // Reload item/armor modifiers only if the player is actually online
        if (offlinePlayer.isOnline()) {
            plugin.getModifierManager().applyModifiers(offlinePlayer.getPlayer(), true);
        }

        String displayName = offlinePlayer.getName() != null ? offlinePlayer.getName() : offlinePlayer.getUniqueId().toString();
        sender.sendMessage(plugin.getPrefix(locale) + plugin.getMsg(CommandMessage.SKILL_SETALL_SET, locale)
                .replace("{level}", String.valueOf(level))
                .replace("{player}", displayName));
    }

    // -------------------------------------------------------------------------
    // reset — unchanged; keeps existing Player-only behaviour (menu interaction
    // not meaningful from console, and applyModifiers always needs online player)
    // -------------------------------------------------------------------------

    @Subcommand("reset")
    @CommandCompletion("@players @skills")
    @CommandPermission("auraskills.command.skill.reset")
    @Description("%desc_skill_reset")
    public void onSkillReset(CommandSender sender, @Flags("other") Player player, @Optional Skill skill) {
        User user = plugin.getUser(player);
        Locale locale = user.getLocale();
        if (skill != null) {
            if (skill.isEnabled()) {
                int level = user.resetSkill(skill);
                plugin.getModifierManager().applyModifiers(player, true);
                sender.sendMessage(plugin.getPrefix(locale) + plugin.getMsg(CommandMessage.SKILL_SETLEVEL_SET, locale)
                        .replace("{skill}", skill.getDisplayName(locale))
                        .replace("{level}", String.valueOf(level))
                        .replace("{player}", player.getName()));
            } else {
                sender.sendMessage(plugin.getPrefix(locale) + plugin.getMsg(CommandMessage.UNKNOWN_SKILL, locale));
            }
        } else {
            int level = plugin.config().getStartLevel();
            for (Skill s : plugin.getSkillRegistry().getValues()) {
                level = user.resetSkill(s);
            }
            plugin.getModifierManager().applyModifiers(player, true);
            sender.sendMessage(plugin.getPrefix(locale) + plugin.getMsg(CommandMessage.SKILL_SETALL_SET, locale)
                    .replace("{level}", String.valueOf(level))
                    .replace("{player}", player.getName()));
        }
    }

}
