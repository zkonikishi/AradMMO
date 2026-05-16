package cn.aradmmo.core.command;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.item.equipment.stat.AradArmorType;
import cn.aradmmo.item.equipment.stat.StatApplier;
import cn.aradmmo.rpg.profile.PlayerProfile;
import cn.aradmmo.rpg.profile.ProfileMutationResult;
import cn.aradmmo.rpg.skill.SkillCastResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class AmCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUB_COMMANDS = List.of(
            "help", "reload", "lang", "profile", "addxp", "money", "vip",
            "class", "stats", "stat", "resetstats", "skills", "skill", "cast",
            "menu", "equip", "reinforce", "item", "pet", "mount", "top", "gender", "element", "vipcolor", "chat");

    private final AradMmoPlugin plugin;

    public AmCommand(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "reload" -> {
                if (!sender.hasPermission("aradmmo.command.reload")) {
                    plugin.messages().send(sender, "error.no-permission");
                    return true;
                }
                plugin.reloadPlugin();
                plugin.messages().send(sender, "command.reload.done");
                return true;
            }
            case "lang" -> {
                String activeLocale = plugin.messages().resolveLocale(sender);
                plugin.messages().send(sender, "command.lang.current", "%locale%", activeLocale);
                return true;
            }
            case "profile" -> { return handleProfile(sender, args); }
            case "addxp"   -> { return handleAddXp(sender, args); }
            case "money"   -> { return handleMoney(sender, args); }
            case "vip"     -> { return handleVip(sender, args); }
            case "class"   -> { return handleClass(sender, args); }
            case "stats"      -> { return handleStats(sender, args); }
            case "stat"        -> { return handleStat(sender, args); }
            case "resetstats"  -> { return handleResetStats(sender, args); }
            case "skills"  -> { return handleSkills(sender, args); }
            case "skill"   -> { return handleSkill(sender, args); }
            case "cast"    -> { return handleCast(sender, args); }
            case "menu"    -> { return handleMenu(sender); }
            case "equip"   -> { return handleEquip(sender, args); }
            case "reinforce" -> { return handleReinforce(sender); }
            case "item"    -> { return handleItem(sender, args); }
            case "pet"     -> { return handlePet(sender, args); }
            case "mount"   -> { return handleMount(sender, args); }
            case "top"     -> { return handleTop(sender, args); }
            case "gender"  -> { return handleGender(sender, args); }
            case "element" -> { return handleElement(sender, args); }
            case "vipcolor" -> { return handleVipColor(sender, args); }
            case "chat" -> { return handleChat(sender, args); }
            default -> {
                plugin.messages().send(sender, "error.unknown-subcommand", "%value%", subCommand);
                sendHelp(sender);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUB_COMMANDS.stream()
                    .filter(value -> value.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("money")) {
            return List.of("set", "add", "take").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && List.of("profile", "addxp", "vip", "class", "stats", "skills")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            return onlinePlayerNames(args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("money")) {
            return onlinePlayerNames(args[2]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("resetstats")) {
            return onlinePlayerNames(args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("stat")) {
            return onlinePlayerNames(args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("stat")) {
            return plugin.attributeKeys().stream()
                    .filter(value -> value.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("skill")) {
            return onlinePlayerNames(args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            return List.of("level", "balance").stream()
                    .filter(v -> v.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("skill")) {
            return plugin.profiles().availableSkills().stream()
                    .filter(value -> value.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("class")) {
            return plugin.profiles().availableArchetypes().stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .filter(value -> value.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("element")) {
            return onlinePlayerNames(args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("element")) {
            return List.of("attack", "resist").stream()
                    .filter(v -> v.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("element")) {
            return plugin.elementKeys().stream()
                    .filter(v -> v.startsWith(args[3].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("gender")) {
            List<String> opts = new ArrayList<>();
            opts.addAll(List.of("male", "female"));
            opts.addAll(onlinePlayerNames(""));
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return opts.stream().filter(v -> v.startsWith(prefix)).collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("gender")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return List.of("male", "female").stream()
                    .filter(v -> v.startsWith(prefix)).collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("item")) {
            return List.of("give").stream()
                    .filter(v -> v.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("equip")) {
            return List.of("debug").stream()
                .filter(v -> v.startsWith(args[1].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("equip")
            && args[1].equalsIgnoreCase("debug")) {
            return List.of("armor").stream()
                .filter(v -> v.startsWith(args[2].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("equip")
            && args[1].equalsIgnoreCase("debug")
            && args[2].equalsIgnoreCase("armor")) {
            return onlinePlayerNames(args[3]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("chat")) {
            return List.of("on", "off", "reload", "route").stream()
                    .filter(v -> v.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("chat")
            && args[1].equalsIgnoreCase("route") && plugin.chat() != null) {
            List<String> options = new ArrayList<>();
            options.add("clear");
            options.add("list");
            options.add("save");
            options.addAll(plugin.chat().availableBridgeGroups());
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return options.stream().filter(v -> v.startsWith(prefix)).collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("vipcolor")) {
            return List.of("name", "chat", "random").stream()
                .filter(v -> v.startsWith(args[1].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("vipcolor")
            && args[1].equalsIgnoreCase("random")) {
            return List.of("name", "chat", "both").stream()
                .filter(v -> v.startsWith(args[2].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("vipcolor")
            && (args[1].equalsIgnoreCase("name") || args[1].equalsIgnoreCase("chat"))) {
            return List.of("#ff6a00:#ffd000", "#00d4ff:#6a5cff", "#ff3cac:#784ba0")
                .stream().filter(v -> v.startsWith(args[2].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("pet")) {
            return List.of("info", "skills", "learn", "mode", "give").stream()
                .filter(v -> v.startsWith(args[1].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("pet")
            && args[1].equalsIgnoreCase("give")) {
            return onlinePlayerNames(args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("pet")
            && args[1].equalsIgnoreCase("give")) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            return plugin.equipment().pets().petIds().stream()
                .filter(v -> v.startsWith(prefix)).collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("pet")
            && args[1].equalsIgnoreCase("learn")
            && sender instanceof Player player) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return plugin.equipment().pets().skills(player).stream()
                .map(cn.aradmmo.item.creatures.pet.PetSkillDef::id)
                .filter(v -> v.startsWith(prefix))
                .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("pet")
            && args[1].equalsIgnoreCase("skills")) {
            return List.of("list").stream()
                .filter(v -> v.startsWith(args[2].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("pet")
            && args[1].equalsIgnoreCase("mode")) {
            return List.of("follow", "stay", "combat").stream()
                .filter(v -> v.startsWith(args[2].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("mount")) {
            return List.of("give", "summon", "dismiss").stream()
                .filter(v -> v.startsWith(args[1].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("mount")
            && args[1].equalsIgnoreCase("give")) {
            return onlinePlayerNames(args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("mount")
            && args[1].equalsIgnoreCase("give")) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            return plugin.mounts().mountIds().stream()
                .filter(v -> v.startsWith(prefix)).collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("item")) {
            return onlinePlayerNames(args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("item")) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            return plugin.equipment().templateIds().stream()
                    .filter(v -> v.startsWith(prefix)).collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    private void sendHelp(CommandSender sender) {
        plugin.messages().send(sender, "command.help.header");
        plugin.messages().send(sender, "command.help.help");
        plugin.messages().send(sender, "command.help.reload");
        plugin.messages().send(sender, "command.help.lang");
        plugin.messages().send(sender, "command.help.profile");
        plugin.messages().send(sender, "command.help.addxp");
        plugin.messages().send(sender, "command.help.money");
        plugin.messages().send(sender, "command.help.vip");
        plugin.messages().send(sender, "command.help.class");
        plugin.messages().send(sender, "command.help.stats");
        plugin.messages().send(sender, "command.help.stat");
        plugin.messages().send(sender, "command.help.resetstats");
        plugin.messages().send(sender, "command.help.skills");
        plugin.messages().send(sender, "command.help.skill");
        plugin.messages().send(sender, "command.help.cast");
        plugin.messages().send(sender, "command.help.menu");
        plugin.messages().send(sender, "command.help.equip");
        plugin.messages().send(sender, "command.help.pet");
        plugin.messages().send(sender, "command.help.top");
        plugin.messages().send(sender, "command.help.gender");
        plugin.messages().send(sender, "command.help.element");
        plugin.messages().send(sender, "command.help.vipcolor");
        plugin.messages().send(sender, "command.help.chat");
    }

    private boolean handleChat(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "error.usage", "%value%", "/am chat <on|off|reload|route>");
            return true;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);
        if (mode.equals("on") || mode.equals("off")) {
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "error.player-required");
                return true;
            }
            if (!sender.hasPermission("aradmmo.chat.toggle")) {
                plugin.messages().send(sender, "error.no-permission");
                return true;
            }
            if (plugin.chat() != null) {
                plugin.chat().setChatEnabled(player, mode.equals("on"));
            }
            plugin.messages().send(sender, mode.equals("on") ? "command.chat.on" : "command.chat.off");
            return true;
        }

        if (mode.equals("route")) {
            if (!sender.hasPermission("aradmmo.command.chat.route")) {
                plugin.messages().send(sender, "error.no-permission");
                return true;
            }
            if (plugin.chat() == null) {
                plugin.messages().send(sender, "error.usage", "%value%", "/am chat route <group|clear>");
                return true;
            }
            if (args.length < 3) {
                String activeGroup = plugin.chat().activeBridgeGroup();
                String targets = String.join(", ", plugin.chat().resolveBridgeTargets());
                if (targets.isBlank()) {
                    targets = "none";
                }
                plugin.messages().send(sender, "command.chat.route-status",
                        "%group%", activeGroup.isBlank() ? "default" : activeGroup,
                        "%targets%", targets,
                        "%server%", plugin.chat().bridgeServerName().isBlank() ? "unset" : plugin.chat().bridgeServerName());
                return true;
            }
            String group = args[2].toLowerCase(Locale.ROOT);
            if (group.equals("list")) {
                List<String> groups = plugin.chat().availableBridgeGroups();
                if (groups.isEmpty()) {
                    plugin.messages().send(sender, "command.chat.route-list-empty");
                    return true;
                }
                plugin.messages().send(sender, "command.chat.route-list-header");
                for (String entry : groups) {
                    String targets = String.join(", ", plugin.chat().bridgeGroupTargets(entry));
                    List<String> flags = new ArrayList<>();
                    if (plugin.chat().isActiveBridgeGroup(entry)) {
                        flags.add("active");
                    }
                    if (plugin.chat().bridgeGroupContainsCurrentServer(entry)) {
                        flags.add("self");
                    }
                    plugin.messages().send(sender, "command.chat.route-list-entry",
                            "%group%", entry,
                            "%targets%", targets.isBlank() ? "none" : targets,
                            "%flags%", flags.isEmpty() ? "" : " <gray>[" + String.join(", ", flags) + "]</gray>");
                }
                return true;
            }
            if (group.equals("clear")) {
                plugin.chat().setRuntimeBridgeGroup("");
                plugin.messages().send(sender, "command.chat.route-cleared");
                return true;
            }
            if (group.equals("save")) {
                if (!plugin.chat().saveActiveBridgeGroupToConfig()) {
                    plugin.messages().send(sender, "error.chat-route-save-failed");
                    return true;
                }
                plugin.messages().send(sender, "command.chat.route-saved",
                        "%value%", plugin.chat().bridgeGroup().isBlank() ? "default" : plugin.chat().bridgeGroup());
                return true;
            }
            if (!plugin.chat().setRuntimeBridgeGroup(group)) {
                plugin.messages().send(sender, "error.invalid-chat-route", "%value%", args[2]);
                return true;
            }
            plugin.messages().send(sender, "command.chat.route-set", "%value%", group);
            return true;
        }

        if (!mode.equals("reload")) {
            plugin.messages().send(sender, "error.usage", "%value%", "/am chat <on|off|reload|route>");
            return true;
        }

        if (!sender.hasPermission("aradmmo.command.chat")) {
            plugin.messages().send(sender, "error.no-permission");
            return true;
        }
        if (plugin.chat() != null) {
            plugin.reloadChatSubsystem();
        }
        plugin.messages().send(sender, "command.chat.reloaded");
        return true;
    }

    // ------------------------------------------------------------------- element
    private boolean handleElement(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aradmmo.admin.element")) {
            plugin.messages().send(sender, "error.no-permission");
            return true;
        }
        // /am element <player> <attack|resist> <element> <value>
        if (args.length < 5) {
            plugin.messages().send(sender, "error.usage",
                    "%value%", "/am element <player> <attack|resist> <element> <value>");
            return true;
        }

        OfflinePlayer target = requireOnlinePlayer(sender, args[1]);
        if (target == null) return true;

        String mode = args[2].toLowerCase(Locale.ROOT);
        if (!mode.equals("attack") && !mode.equals("resist")) {
            plugin.messages().send(sender, "error.usage",
                    "%value%", "/am element <player> <attack|resist> <element> <value>");
            return true;
        }

        String elemKey = args[3].toLowerCase(Locale.ROOT);
        if (!plugin.elementKeys().contains(elemKey)) {
            plugin.messages().send(sender, "error.invalid-element", "%value%", args[3]);
            return true;
        }

        Integer value = parsePositiveInt(sender, args[4]);
        if (value == null) return true;

        cn.aradmmo.rpg.profile.PlayerProfile profile = mode.equals("attack")
                ? plugin.profiles().setElementAttack(target, elemKey, value)
                : plugin.profiles().setElementResist(target, elemKey, value);

        plugin.messages().send(sender, "command.element.done",
                "%player%", profile.name(),
                "%mode%", mode,
                "%element%", elemKey,
                "%value%", Integer.toString(value));
        return true;
    }

    // ------------------------------------------------------------------- gender
    private boolean handleGender(CommandSender sender, String[] args) {
        boolean isAdmin = sender.hasPermission("aradmmo.admin.gender");

        // /am gender <male|female>  self
        if (args.length == 2) {
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "error.player-required");
                return true;
            }
            String gender = args[1].toLowerCase(Locale.ROOT);
            if (!gender.equals("male") && !gender.equals("female")) {
                plugin.messages().send(sender, "error.invalid-gender", "%value%", args[1]);
                return true;
            }
            PlayerProfile profile = plugin.profiles().profile(player);
            if (!profile.gender().isEmpty() && !isAdmin) {
                plugin.messages().send(sender, "command.gender.already-set");
                return true;
            }
            plugin.profiles().setGender(player, gender);
            String display = gender.equals("male") ? "\u2642 \u7537\u6027" : "\u2640 \u5973\u6027";
            plugin.messages().send(sender, "command.gender.done", "%value%", display);
            return true;
        }

        // /am gender <player> <male|female>  admin
        if (args.length >= 3) {
            if (!isAdmin) {
                plugin.messages().send(sender, "error.no-permission");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                plugin.messages().send(sender, "error.player-not-found", "%value%", args[1]);
                return true;
            }
            String gender = args[2].toLowerCase(Locale.ROOT);
            if (!gender.equals("male") && !gender.equals("female")) {
                plugin.messages().send(sender, "error.invalid-gender", "%value%", args[2]);
                return true;
            }
            plugin.profiles().setGender(target, gender);
            String display = gender.equals("male") ? "\u2642 \u7537\u6027" : "\u2640 \u5973\u6027";
            plugin.messages().send(sender, "command.gender.done-admin",
                    "%player%", target.getName(), "%value%", display);
            return true;
        }

        plugin.messages().send(sender, "error.usage", "%value%", "/am gender [player] <male|female>");
        return true;
    }

    // ------------------------------------------------------------------- profile
    private boolean handleProfile(CommandSender sender, String[] args) {
        OfflinePlayer target;
        if (args.length >= 2) {
            target = requireOnlinePlayer(sender, args[1]);
            if (target == null) {
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plugin.messages().send(sender, "error.player-required");
            return true;
        }

        PlayerProfile profile = plugin.profiles().profile(target);
        double nextLevel = plugin.profiles().threshold(profile.level());
        plugin.messages().send(sender, "command.profile.header", "%player%", profile.name());
        plugin.messages().send(sender, "command.profile.level", "%value%", Integer.toString(profile.level()));
        plugin.messages().send(sender, "command.profile.exp",
                "%value%", format(profile.experience()),
                "%needed%", format(nextLevel));
        plugin.messages().send(sender, "command.profile.balance", "%value%", format(profile.balance()));
        plugin.messages().send(sender, "command.profile.vip", "%value%", profile.vipTier());
        plugin.messages().send(sender, "command.profile.class", "%value%", profile.archetype());
        plugin.messages().send(sender, "command.profile.points",
            "%stat%", Integer.toString(profile.statPoints()),
            "%skill%", Integer.toString(profile.skillPoints()));
        return true;
    }

    private boolean handleAddXp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aradmmo.command.addxp")) {
            plugin.messages().send(sender, "error.no-permission");
            return true;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "error.usage", "%value%", "/am addxp <player> <amount>");
            return true;
        }

        OfflinePlayer target = requireOnlinePlayer(sender, args[1]);
        if (target == null) {
            return true;
        }

        Double amount = parsePositiveDouble(sender, args[2]);
        if (amount == null) {
            return true;
        }

        ProfileMutationResult result = plugin.profiles().addExperience(target, amount);
        plugin.messages().send(sender, "command.addxp.done",
                "%player%", result.profile().name(),
                "%value%", format(amount),
                "%level%", Integer.toString(result.profile().level()));
        if (result.leveledUp()) {
            plugin.messages().send(sender, "command.addxp.level-up",
                    "%player%", result.profile().name(),
                "%value%", Integer.toString(result.levelsGained()),
                "%stat%", Integer.toString(result.statPointsGained()),
                "%skill%", Integer.toString(result.skillPointsGained()));
        }
        return true;
    }

    private boolean handleMoney(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aradmmo.command.money")) {
            plugin.messages().send(sender, "error.no-permission");
            return true;
        }
        if (args.length < 4) {
            plugin.messages().send(sender, "error.usage", "%value%", "/am money <set|add|take> <player> <amount>");
            return true;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);
        OfflinePlayer target = requireOnlinePlayer(sender, args[2]);
        if (target == null) {
            return true;
        }

        Double amount = parsePositiveDouble(sender, args[3]);
        if (amount == null) {
            return true;
        }

        PlayerProfile profile = switch (mode) {
            case "set" -> plugin.profiles().setBalance(target, amount);
            case "add" -> plugin.profiles().addBalance(target, amount);
            case "take" -> plugin.profiles().takeBalance(target, amount);
            default -> {
                plugin.messages().send(sender, "error.usage", "%value%", "/am money <set|add|take> <player> <amount>");
                yield null;
            }
        };

        if (profile == null) {
            return true;
        }

        plugin.messages().send(sender, "command.money.done",
                "%player%", profile.name(),
                "%value%", format(profile.balance()),
                "%mode%", mode);
        return true;
    }

    private boolean handleVip(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aradmmo.command.vip")) {
            plugin.messages().send(sender, "error.no-permission");
            return true;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "error.usage", "%value%", "/am vip <player> <tier>");
            return true;
        }

        OfflinePlayer target = requireOnlinePlayer(sender, args[1]);
        if (target == null) {
            return true;
        }

        PlayerProfile profile = plugin.profiles().setVipTier(target, args[2]);
        plugin.messages().send(sender, "command.vip.done",
                "%player%", profile.name(),
                "%value%", profile.vipTier());
        Player online = target.getPlayer();
        if (online != null) plugin.cosmeticColors().applyNameStyle(online);
        return true;
    }

    private boolean handleVipColor(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "error.player-required");
            return true;
        }

        PlayerProfile profile = plugin.profiles().profile(player);
        if (!plugin.cosmeticColors().hasVipPrivilege(profile)) {
            plugin.messages().send(sender, "error.no-permission");
            return true;
        }

        if (args.length < 2) {
            plugin.messages().send(sender, "error.usage",
                    "%value%", "/am vipcolor <name|chat|random> [#RRGGBB:#RRGGBB | name|chat|both]");
            return true;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);
        switch (mode) {
            case "name" -> {
                if (!player.hasPermission("aradmmo.vip.gradient.name")) {
                    plugin.messages().send(sender, "error.no-permission");
                    return true;
                }
                if (args.length < 3) {
                    plugin.messages().send(sender, "error.usage", "%value%", "/am vipcolor name <#RRGGBB:#RRGGBB>");
                    return true;
                }
                String spec = plugin.cosmeticColors().normalizeGradientSpec(args[2]);
                if (spec.isBlank()) {
                    plugin.messages().send(sender, "error.invalid-gradient", "%value%", args[2]);
                    return true;
                }
                plugin.profiles().setNameGradient(player, spec);
                plugin.cosmeticColors().applyNameStyle(player);
                plugin.messages().send(sender, "command.vipcolor.name.done", "%value%", spec);
                return true;
            }
            case "chat" -> {
                if (!player.hasPermission("aradmmo.vip.gradient.chat")) {
                    plugin.messages().send(sender, "error.no-permission");
                    return true;
                }
                if (args.length < 3) {
                    plugin.messages().send(sender, "error.usage", "%value%", "/am vipcolor chat <#RRGGBB:#RRGGBB>");
                    return true;
                }
                String spec = plugin.cosmeticColors().normalizeGradientSpec(args[2]);
                if (spec.isBlank()) {
                    plugin.messages().send(sender, "error.invalid-gradient", "%value%", args[2]);
                    return true;
                }
                plugin.profiles().setChatGradient(player, spec);
                plugin.messages().send(sender, "command.vipcolor.chat.done", "%value%", spec);
                return true;
            }
            case "random" -> {
                if (args.length < 3) {
                    plugin.messages().send(sender, "error.usage", "%value%", "/am vipcolor random <name|chat|both>");
                    return true;
                }
                String target = args[2].toLowerCase(Locale.ROOT);
                String spec = plugin.cosmeticColors().randomGradientSpec();
                switch (target) {
                    case "name" -> {
                        if (!player.hasPermission("aradmmo.vip.gradient.name")) {
                            plugin.messages().send(sender, "error.no-permission");
                            return true;
                        }
                        plugin.profiles().setNameGradient(player, spec);
                        plugin.cosmeticColors().applyNameStyle(player);
                        plugin.messages().send(sender, "command.vipcolor.name.done", "%value%", spec);
                    }
                    case "chat" -> {
                        if (!player.hasPermission("aradmmo.vip.gradient.chat")) {
                            plugin.messages().send(sender, "error.no-permission");
                            return true;
                        }
                        plugin.profiles().setChatGradient(player, spec);
                        plugin.messages().send(sender, "command.vipcolor.chat.done", "%value%", spec);
                    }
                    case "both" -> {
                        boolean canName = player.hasPermission("aradmmo.vip.gradient.name");
                        boolean canChat = player.hasPermission("aradmmo.vip.gradient.chat");
                        if (!canName && !canChat) {
                            plugin.messages().send(sender, "error.no-permission");
                            return true;
                        }
                        if (canName) {
                            plugin.profiles().setNameGradient(player, spec);
                            plugin.cosmeticColors().applyNameStyle(player);
                        }
                        if (canChat) {
                            plugin.profiles().setChatGradient(player, spec);
                        }
                        plugin.messages().send(sender, "command.vipcolor.both.done", "%value%", spec);
                    }
                    default -> {
                        plugin.messages().send(sender, "error.usage", "%value%", "/am vipcolor random <name|chat|both>");
                        return true;
                    }
                }
                return true;
            }
            default -> {
                plugin.messages().send(sender, "error.usage",
                        "%value%", "/am vipcolor <name|chat|random> [#RRGGBB:#RRGGBB | name|chat|both]");
                return true;
            }
        }
    }

    private boolean handleClass(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aradmmo.command.class")) {
            plugin.messages().send(sender, "error.no-permission");
            return true;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "error.usage", "%value%", "/am class <player> <class>");
            return true;
        }

        OfflinePlayer target = requireOnlinePlayer(sender, args[1]);
        if (target == null) {
            return true;
        }

        try {
            PlayerProfile profile = plugin.profiles().setArchetype(target, args[2]);
            plugin.messages().send(sender, "command.class.done",
                    "%player%", profile.name(),
                    "%value%", profile.archetype());
            // Sync max HP; class change resets base attributes which affects VIT
            Player onlineTarget = target.getPlayer();
            if (onlineTarget != null) {
                plugin.hp().applyMaxHealth(onlineTarget);
                plugin.skins().applyClassSkin(onlineTarget);
            }
        } catch (IllegalArgumentException exception) {
            plugin.messages().send(sender, "error.invalid-class", "%value%", args[2]);
        }
        return true;
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        OfflinePlayer target;
        if (args.length >= 2) {
            target = requireOnlinePlayer(sender, args[1]);
            if (target == null) {
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plugin.messages().send(sender, "error.player-required");
            return true;
        }

        PlayerProfile profile = plugin.profiles().profile(target);
        plugin.messages().send(sender, "command.stats.header", "%player%", profile.name());
        plugin.messages().send(sender, "command.stats.points", "%value%", Integer.toString(profile.statPoints()));
        for (String attrKey : plugin.attributeKeys()) {
            plugin.messages().send(sender, "command.stats.entry",
                    "%attribute%", attrKey,
                    "%value%", Integer.toString(profile.attribute(attrKey)));
        }
        return true;
    }

    private boolean handleStat(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aradmmo.command.stat")) {
            plugin.messages().send(sender, "error.no-permission");
            return true;
        }
        if (args.length < 4) {
            plugin.messages().send(sender, "error.usage", "%value%", "/am stat <player> <attribute> <amount>");
            return true;
        }

        OfflinePlayer target = requireOnlinePlayer(sender, args[1]);
        if (target == null) {
            return true;
        }
        String attrKey = args[2].toLowerCase(Locale.ROOT);
        if (!plugin.attributeKeys().contains(attrKey)) {
            plugin.messages().send(sender, "error.invalid-attribute", "%value%", args[2]);
            return true;
        }
        Integer amount = parsePositiveInt(sender, args[3]);
        if (amount == null) {
            return true;
        }

        try {
            PlayerProfile profile = plugin.profiles().allocateAttribute(target, attrKey, amount);
            plugin.messages().send(sender, "command.stat.done",
                    "%player%", profile.name(),
                    "%attribute%", attrKey,
                    "%value%", Integer.toString(profile.attribute(attrKey)),
                    "%points%", Integer.toString(profile.statPoints()));
            // Sync max HP whenever VIT changes
            Player onlineTarget = target.getPlayer();
            if (onlineTarget != null) plugin.hp().applyMaxHealth(onlineTarget);
        } catch (IllegalArgumentException exception) {
            plugin.messages().send(sender, "error.not-enough-stat-points");
        }
        return true;
    }

    private boolean handleResetStats(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aradmmo.command.resetstats")) {
            plugin.messages().send(sender, "error.no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "error.usage", "%value%", "/am resetstats <player>");
            return true;
        }
        OfflinePlayer target = requireOnlinePlayer(sender, args[1]);
        if (target == null) return true;

        PlayerProfile profile = plugin.profiles().resetStats(target);
        plugin.messages().send(sender, "command.resetstats.done",
                "%player%", profile.name(),
                "%points%", Integer.toString(profile.statPoints()));
        // Sync max HP after stat reset (VIT may have changed)
        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null) plugin.hp().applyMaxHealth(onlineTarget);
        return true;
    }

    private boolean handleSkills(CommandSender sender, String[] args) {
        OfflinePlayer target;
        if (args.length >= 2) {
            target = requireOnlinePlayer(sender, args[1]);
            if (target == null) {
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plugin.messages().send(sender, "error.player-required");
            return true;
        }

        PlayerProfile profile = plugin.profiles().profile(target);
        plugin.messages().send(sender, "command.skills.header", "%player%", profile.name());
        plugin.messages().send(sender, "command.skills.points", "%value%", Integer.toString(profile.skillPoints()));
        List<Map.Entry<String, Integer>> entries = profile.skills().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .toList();
        if (entries.isEmpty()) {
            plugin.messages().send(sender, "command.skills.empty");
            return true;
        }
        for (Map.Entry<String, Integer> entry : entries) {
            plugin.messages().send(sender, "command.skills.entry",
                    "%skill%", entry.getKey(),
                    "%value%", Integer.toString(entry.getValue()),
                    "%cap%", Integer.toString(plugin.profiles().skillCap(entry.getKey())));
        }
        return true;
    }

    private boolean handleSkill(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aradmmo.command.skill")) {
            plugin.messages().send(sender, "error.no-permission");
            return true;
        }
        if (args.length < 4) {
            plugin.messages().send(sender, "error.usage", "%value%", "/am skill <player> <skill> <amount>");
            return true;
        }

        OfflinePlayer target = requireOnlinePlayer(sender, args[1]);
        if (target == null) {
            return true;
        }
        Integer amount = parsePositiveInt(sender, args[3]);
        if (amount == null) {
            return true;
        }

        try {
            PlayerProfile profile = plugin.profiles().allocateSkill(target, args[2], amount);
            String skillId = args[2].toLowerCase(Locale.ROOT);
            plugin.messages().send(sender, "command.skill.done",
                    "%player%", profile.name(),
                    "%skill%", skillId,
                    "%value%", Integer.toString(profile.skillLevel(skillId)),
                    "%points%", Integer.toString(profile.skillPoints()));
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage() == null ? "" : exception.getMessage();
            if (message.startsWith("Unknown skill")) {
                plugin.messages().send(sender, "error.invalid-skill", "%value%", args[2]);
            } else if (message.startsWith("Skill cap")) {
                plugin.messages().send(sender, "error.skill-cap", "%value%", args[2]);
            } else {
                plugin.messages().send(sender, "error.not-enough-skill-points");
            }
        }
        return true;
    }

    private boolean handleCast(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "error.player-required");
            return true;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "error.usage", "%value%", "/am cast <skill>");
            return true;
        }

        String skillId = args[1].toLowerCase(Locale.ROOT);
        SkillCastResult result = plugin.skillActivations().cast(player, skillId);
        switch (result) {
            case SUCCESS -> {} // action bar sent by SkillActivationService
            case ON_COOLDOWN -> {
                long remaining = plugin.skillCooldowns().remainingMillis(player, skillId);
                long seconds = (remaining + 999L) / 1000L;
                plugin.messages().send(sender, "error.skill-cooldown",
                        "%skill%", skillId,
                        "%seconds%", Long.toString(seconds));
            }
            case NOT_LEARNED -> plugin.messages().send(sender, "error.skill-not-learned", "%skill%", skillId);
            case NO_MANA     -> plugin.messages().send(sender, "error.no-mana");
            case UNKNOWN     -> plugin.messages().send(sender, "error.invalid-skill", "%value%", skillId);
        }
        return true;
    }

    private boolean handleMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "error.player-required");
            return true;
        }
        plugin.gui().openProfile(player);
        return true;
    }

    private boolean handleReinforce(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "error.player-required");
            return true;
        }
        new cn.aradmmo.item.equipment.reinforce.ReinforceGui(plugin.reinforce(), player).open();
        return true;
    }

    private boolean handleEquip(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("debug")) {
            return handleEquipDebug(sender, args);
        }

        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "error.player-required");
            return true;
        }
        plugin.equipment().openEquipment(player);
        return true;
    }

    private boolean handleEquipDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aradmmo.command.equip.debug")) {
            plugin.messages().send(sender, "error.no-permission");
            return true;
        }

        if (args.length < 3 || !args[2].equalsIgnoreCase("armor")) {
            plugin.messages().send(sender, "error.usage", "%value%", "/am equip debug armor [player]");
            return true;
        }

        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                plugin.messages().send(sender, "error.player-not-found", "%value%", args[3]);
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plugin.messages().send(sender, "error.usage", "%value%", "/am equip debug armor <player>");
            return true;
        }

        StatApplier.ArmorDebugSnapshot snapshot = plugin.equipment().debugArmor(target);
        if (snapshot == null) {
            plugin.messages().send(sender, "error.equip-debug-unavailable", "%player%", target.getName());
            return true;
        }

        plugin.messages().send(sender, "command.equip.debug.header", "%player%", target.getName());
        plugin.messages().send(sender, "command.equip.debug.profile",
                "%archetype%", displayOrDefault(snapshot.archetype()),
                "%mastery%", displayArmorType(snapshot.masteredType()),
                "%enabled%", snapshot.masteryEnabled() ? "true" : "false",
                "%strict%", snapshot.strictTypeBonusByMastery() ? "true" : "false");
        plugin.messages().send(sender, "command.equip.debug.passive-skill",
            "%stage%", Integer.toString(snapshot.classStage()),
            "%level%", Integer.toString(snapshot.passiveSkillLevel()),
            "%multiplier%", String.format(Locale.US, "%.2f", snapshot.passiveSkillMultiplier()));
        plugin.messages().send(sender, "command.equip.debug.counts",
                "%total%", Integer.toString(snapshot.totalArmorCount()),
                "%mastered%", Integer.toString(snapshot.masteredCount()),
                "%mismatch%", Integer.toString(snapshot.mismatchCount()));

        String typeCounts = snapshot.armorTypeCounts().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey().name() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        plugin.messages().send(sender, "command.equip.debug.types", "%value%", displayOrDefault(typeCounts));

        String setCounts = snapshot.armorSetCounts().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        plugin.messages().send(sender, "command.equip.debug.sets", "%value%", displayOrDefault(setCounts));

        sendDebugList(sender, "command.equip.debug.type-bonuses", snapshot.armorTypeBonuses());
        sendDebugList(sender, "command.equip.debug.set-bonuses", snapshot.armorSetBonuses());
        sendDebugList(sender, "command.equip.debug.mastery-bonuses", snapshot.masteryBonuses());
        sendDebugList(sender, "command.equip.debug.mastery-type-bonuses", snapshot.masteryTypeBonuses());
        sendDebugList(sender, "command.equip.debug.mismatch-penalties", snapshot.mismatchPenalties());
        return true;
    }

    private void sendDebugList(CommandSender sender, String key, List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            plugin.messages().send(sender, key, "%value%", "none");
            return;
        }
        plugin.messages().send(sender, key, "%value%", String.join(" | ", entries));
    }

    private String displayArmorType(AradArmorType type) {
        if (type == null || type == AradArmorType.UNKNOWN) return "none";
        return type.name();
    }

    private String displayOrDefault(String text) {
        return (text == null || text.isBlank()) ? "none" : text;
    }

    /**
     * /am item give <玩家> <模板序列
     * stat-templates.yml 中定义的属性物品给予指定玩家
     */
    private boolean handleItem(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aradmmo.command.item")) {
            plugin.messages().send(sender, "error.no-permission");
            return true;
        }
        // /am item give <player> <templateId>
        if (args.length < 4 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage("§e用法: /am item give <玩家> <模板ID>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            plugin.messages().send(sender, "error.player-not-found", "%value%", args[2]);
            return true;
        }
        String templateId = args[3];
        org.bukkit.inventory.ItemStack item = plugin.equipment().buildTemplate(templateId);
        if (item == null) {
            sender.sendMessage("§c找不到模 §f" + templateId
                    + "§c，请检查 equipment/stat-templates.yml");
            return true;
        }
        target.getInventory().addItem(item);
        sender.sendMessage("§a已将 §f" + templateId + "§a 给予 §f" + target.getName() + "§a。");
        target.sendMessage("§a你收到了一件装备：§f" + templateId);
        return true;
    }

    private boolean handlePet(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e用法: /am pet <info|skills|learn|mode|give> ...");
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "info" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "error.player-required");
                    return true;
                }
                cn.aradmmo.item.creatures.pet.PetService.ActivePetView view =
                        plugin.equipment().pets().activePetView(player);
                if (view == null) {
                    plugin.messages().send(sender, "error.pet-no-active");
                    return true;
                }
                cn.aradmmo.item.creatures.pet.PetCompanionProfile profile = view.profile();
                sender.sendMessage("§6[Pet] §f" + view.def().name() + " §7(" + view.petId() + ")");
                sender.sendMessage("§7Level: §a" + profile.level()
                        + " §7EXP: §b" + format(profile.experience())
                        + "§7/§b" + format(profile.expToNext()));
                sender.sendMessage("§7Skill Points: §e" + profile.skillPoints());
                sender.sendMessage("§7Learned Skills: §f"
                        + String.join(", ", profile.learnedSkills()));
                return true;
            }
            case "skills" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "error.player-required");
                    return true;
                }
                cn.aradmmo.item.creatures.pet.PetService.ActivePetView view =
                        plugin.equipment().pets().activePetView(player);
                if (view == null) {
                    plugin.messages().send(sender, "error.pet-no-active");
                    return true;
                }
                if (args.length >= 3 && args[2].equalsIgnoreCase("list")) {
                    sender.sendMessage("§6[Pet] §f" + view.def().name() + " §7skills:");
                    for (cn.aradmmo.item.creatures.pet.PetSkillDef skill : plugin.equipment().pets().skills(player)) {
                        boolean learned = plugin.equipment().pets().learnedSkill(player, skill.id());
                        String mark = learned ? "§a[Learned]" : "§c[Unlearned]";
                        sender.sendMessage("§7- " + mark + " §f" + skill.id()
                                + " §8(" + skill.type().name() + ")"
                                + " §7CD:§b" + skill.cooldownTicks()
                                + " §7Power:§b" + format(skill.power()));
                    }
                    return true;
                }

                new cn.aradmmo.item.creatures.pet.PetSkillGui(plugin, player).open();
                return true;
            }
            case "learn" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "error.player-required");
                    return true;
                }
                if (args.length < 3) {
                    plugin.messages().send(sender, "error.usage", "%value%", "/am pet learn <skillId>");
                    return true;
                }
                String skillId = args[2].toLowerCase(Locale.ROOT);
                boolean ok = plugin.equipment().pets().learnSkill(player, skillId);
                if (!ok) {
                    plugin.messages().send(sender, "error.pet-learn-failed", "%value%", skillId);
                    return true;
                }
                plugin.messages().send(sender, "command.pet.learn-done", "%value%", skillId);
                return true;
            }
            case "mode" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "error.player-required");
                    return true;
                }
                if (plugin.equipment().pets().activePetView(player) == null) {
                    plugin.messages().send(sender, "error.pet-no-active");
                    return true;
                }

                cn.aradmmo.item.creatures.pet.PetBehaviorMode mode;
                if (args.length < 3) {
                    mode = plugin.equipment().pets().cycleBehaviorMode(player);
                } else {
                    mode = plugin.equipment().pets().setBehaviorMode(
                            player, cn.aradmmo.item.creatures.pet.PetBehaviorMode.parse(args[2]));
                }
                plugin.messages().send(sender, "command.pet.mode-done", "%value%", mode.name());
                return true;
            }
            case "give" -> {
                if (!sender.hasPermission("aradmmo.command.item")) {
                    plugin.messages().send(sender, "error.no-permission");
                    return true;
                }
                if (args.length < 4) {
                    plugin.messages().send(sender, "error.usage", "%value%", "/am pet give <player> <petId>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    plugin.messages().send(sender, "error.player-not-found", "%value%", args[2]);
                    return true;
                }
                String petId = args[3].toLowerCase(Locale.ROOT);
                org.bukkit.inventory.ItemStack item = plugin.equipment().pets().createPetItem(petId);
                if (item == null) {
                    plugin.messages().send(sender, "error.invalid-pet", "%value%", petId);
                    return true;
                }
                target.getInventory().addItem(item);
                plugin.messages().send(sender, "command.pet.give-done",
                        "%player%", target.getName(), "%value%", petId);
                return true;
            }
            default -> {
                sender.sendMessage("§e用法: /am pet <info|skills|learn|mode|give> ...");
                return true;
            }
        }
    }

    private boolean handleMount(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e用法: /am mount <give|summon|dismiss> ...");
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "dismiss" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "error.player-required");
                    return true;
                }
                plugin.mounts().despawn(player);
                sender.sendMessage("§a已收回当前坐骑。");
                return true;
            }
            case "summon" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "error.player-required");
                    return true;
                }
                org.bukkit.inventory.ItemStack inHand = player.getInventory().getItemInMainHand();
                if (!plugin.mounts().isMountItem(inHand)) {
                    sender.sendMessage("§c请手持坐骑道具后再执行 /am mount summon");
                    return true;
                }
                plugin.mounts().summon(player, inHand);
                sender.sendMessage("§a坐骑已召唤。");
                return true;
            }
            case "give" -> {
                if (!sender.hasPermission("aradmmo.command.item")) {
                    plugin.messages().send(sender, "error.no-permission");
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage("§e用法: /am mount give <玩家> <mountId>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    plugin.messages().send(sender, "error.player-not-found", "%value%", args[2]);
                    return true;
                }
                String mountId = args[3].toLowerCase(Locale.ROOT);
                org.bukkit.inventory.ItemStack mountItem = plugin.mounts().createMountItem(mountId);
                if (mountItem == null) {
                    sender.sendMessage("§c坐骑ID不存在: §f" + mountId);
                    return true;
                }
                target.getInventory().addItem(mountItem);
                sender.sendMessage("§a已给予坐骑道具 §f" + mountId + " §a给 §f" + target.getName());
                target.sendMessage("§a你获得了坐骑道具: §f" + mountId);
                return true;
            }
            default -> {
                sender.sendMessage("§e用法: /am mount <give|summon|dismiss> ...");
                return true;
            }
        }
    }

    private boolean handleTop(CommandSender sender, String[] args) {
        String mode = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "level";
        java.util.Comparator<cn.aradmmo.rpg.profile.PlayerProfile> order = switch (mode) {
            case "balance" -> java.util.Comparator.<cn.aradmmo.rpg.profile.PlayerProfile, Double>comparing(
                    p -> p.balance()).reversed();
            default -> java.util.Comparator.<cn.aradmmo.rpg.profile.PlayerProfile, Integer>comparing(
                    p -> p.level()).reversed();
        };

        java.util.List<cn.aradmmo.rpg.profile.PlayerProfile> top = plugin.profiles().leaderboard(order, 10);
        plugin.messages().send(sender, "command.top.header", "%mode%", mode);
        for (int i = 0; i < top.size(); i++) {
            cn.aradmmo.rpg.profile.PlayerProfile p = top.get(i);
            String value = mode.equals("balance")
                    ? format(p.balance())
                    : String.valueOf(p.level());
            plugin.messages().send(sender, "command.top.entry",
                    "%rank%", String.valueOf(i + 1),
                    "%player%", p.name(),
                    "%value%", value);
        }
        return true;
    }

    private OfflinePlayer requireOnlinePlayer(CommandSender sender, String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            plugin.messages().send(sender, "error.player-not-found", "%value%", name);
            return null;
        }
        return player;
    }

    private Double parsePositiveDouble(CommandSender sender, String value) {
        try {
            double parsed = Double.parseDouble(value);
            if (parsed < 0D) {
                plugin.messages().send(sender, "error.invalid-number", "%value%", value);
                return null;
            }
            return parsed;
        } catch (NumberFormatException exception) {
            plugin.messages().send(sender, "error.invalid-number", "%value%", value);
            return null;
        }
    }

    private Integer parsePositiveInt(CommandSender sender, String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                plugin.messages().send(sender, "error.invalid-number", "%value%", value);
                return null;
            }
            return parsed;
        } catch (NumberFormatException exception) {
            plugin.messages().send(sender, "error.invalid-number", "%value%", value);
            return null;
        }
    }

    private List<String> onlinePlayerNames(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
