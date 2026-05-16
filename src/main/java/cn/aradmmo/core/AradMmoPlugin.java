package cn.aradmmo.core;

import cn.aradmmo.rpg.classes.ClassRegistry;
import cn.aradmmo.core.chat.ChatBungeeBridge;
import cn.aradmmo.rpg.combat.CombatListener;
import cn.aradmmo.rpg.combat.CombatService;
import cn.aradmmo.core.chat.ChatService;
import cn.aradmmo.core.chat.CoreChatListener;
import cn.aradmmo.core.command.AmCommand;
import cn.aradmmo.core.listener.PlayerSessionListener;
import cn.aradmmo.core.gui.GuiListener;
import cn.aradmmo.core.gui.GuiService;
import cn.aradmmo.core.gui.framework.GuiDef;
import cn.aradmmo.core.i18n.MessageService;
import cn.aradmmo.core.text.PlayerCosmeticColorService;
import cn.aradmmo.rpg.listener.DeathListener;
import cn.aradmmo.rpg.listener.MobKillListener;
import cn.aradmmo.rpg.hp.HpService;
import cn.aradmmo.rpg.mana.ManaService;
import cn.aradmmo.rpg.stamina.StaminaListener;
import cn.aradmmo.rpg.stamina.StaminaService;
import cn.aradmmo.rpg.profile.ProfileService;
import cn.aradmmo.rpg.skill.SkillActivationService;
import cn.aradmmo.rpg.skill.SkillBuffService;
import cn.aradmmo.rpg.skill.SkillCooldownService;
import cn.aradmmo.core.skin.SkinService;
import cn.aradmmo.item.equipment.EquipmentService;
import cn.aradmmo.item.equipment.EquipmentListener;
import cn.aradmmo.item.equipment.reinforce.ReinforceService;
import cn.aradmmo.item.equipment.reinforce.ReinforceListener;
import cn.aradmmo.item.equipment.ElytraListener;
import cn.aradmmo.item.equipment.backpack.BackpackListener;
import cn.aradmmo.item.creatures.mount.MountListener;
import cn.aradmmo.item.creatures.mount.MountService;
import cn.aradmmo.item.creatures.pet.PetListener;
import cn.aradmmo.item.creatures.pet.PetSkillGuiListener;
import cn.aradmmo.rpg.status.StatusEffectService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class AradMmoPlugin extends JavaPlugin {
    private static final String DEFAULT_LOCALE = "zh_cn";

    private ClassRegistry classRegistry;
    private FileConfiguration mainConfig;
    private SkinService skinService;
    private FileConfiguration skinsConfig;
    private FileConfiguration attributesConfig;
    private FileConfiguration statusConfig;
    private FileConfiguration itemWeightsConfig;
    private final Map<String, GuiDef> guiDefs = new HashMap<>();
    private CombatService combatService;
    private MessageService messageService;
    private ProfileService profileService;
    private SkillCooldownService skillCooldownService;
    private SkillBuffService skillBuffService;
    private SkillActivationService skillActivationService;
    private GuiService guiService;
    private StatusEffectService statusEffectService;
    private HpService hpService;
    private ManaService manaService;
    private StaminaService staminaService;
    private EquipmentService equipmentService;
    private MountService mountService;
    private ReinforceService reinforceService;
    private PlayerCosmeticColorService cosmeticColorService;
    private ChatService chatService;
    private ChatBungeeBridge chatBungeeBridge;
    private ScheduledTask nameStyleTask;

    @Override
    public void onEnable() {
        bootstrapConfigTree();
        reloadConfig();
        reloadSkinsConfig();
        reloadAttributesConfig();
        reloadStatusConfig();
        reloadItemWeightsConfig();
        loadGuiDefs();

        this.classRegistry = new ClassRegistry(this);
        this.classRegistry.reload();

        this.skinService = new SkinService(this);
        this.skinService.reload();

        this.messageService = new MessageService(this);
        this.messageService.reload();

        this.profileService = new ProfileService(this);
        this.profileService.reload();
        this.cosmeticColorService = new PlayerCosmeticColorService(this);
        this.chatService = new ChatService(this);
        this.chatService.reload();
        this.chatBungeeBridge = new ChatBungeeBridge(this);
        this.chatBungeeBridge.registerChannels();

        this.skillCooldownService = new SkillCooldownService();
        this.skillBuffService = new SkillBuffService();
        this.skillActivationService = new SkillActivationService(this, skillCooldownService, skillBuffService);
        this.combatService = new CombatService(this, skillBuffService);
        this.guiService = new GuiService(this);

        this.statusEffectService = new StatusEffectService(this);
        this.statusEffectService.reload();
        this.statusEffectService.startTicker();

        this.hpService = new HpService(this);
        this.hpService.startTicker();

        this.manaService = new ManaService(this);
        this.manaService.startTicker();

        this.staminaService = new StaminaService(this);
        this.staminaService.startTicker();

        this.equipmentService = new EquipmentService(this);
        this.equipmentService.reload();
        this.equipmentService.startInfoUpdater();

        this.mountService = new MountService(this);
        this.mountService.reload();

        this.reinforceService = new ReinforceService(this);
        startNameStyleUpdater();

        registerCommands();
        registerListeners();

        getLogger().info("Arad MMO enabled.");
    }

    @Override
    public void onDisable() {
        if (this.statusEffectService != null) this.statusEffectService.shutdown();
        if (this.hpService            != null) this.hpService.shutdown();
        if (this.manaService          != null) this.manaService.shutdown();
        if (this.staminaService       != null) this.staminaService.shutdown();
        if (this.equipmentService      != null) this.equipmentService.shutdown();
        if (this.mountService          != null) this.mountService.shutdown();
        if (this.nameStyleTask         != null) this.nameStyleTask.cancel();
        if (this.chatBungeeBridge      != null) this.chatBungeeBridge.unregisterChannels();
        getLogger().info("Arad MMO disabled.");
    }

    public MessageService messages() { return this.messageService; }
    public ProfileService profiles() { return this.profileService; }
    public CombatService combat() { return this.combatService; }
    public SkillCooldownService skillCooldowns() { return this.skillCooldownService; }
    public SkillBuffService skillBuffs() { return this.skillBuffService; }
    public SkillActivationService skillActivations() { return this.skillActivationService; }
    public GuiService gui() { return this.guiService; }

    public ClassRegistry classes()  { return this.classRegistry; }
    public SkinService skins()       { return this.skinService; }
    public FileConfiguration skinsConfig()        { return this.skinsConfig; }
    public FileConfiguration attributesConfig()   { return this.attributesConfig; }
    public FileConfiguration statusConfig()       { return this.statusConfig; }
    public FileConfiguration itemWeightsConfig()  { return this.itemWeightsConfig; }
    public StatusEffectService statusEffects()  { return this.statusEffectService; }
    public HpService hp()                        { return this.hpService; }
    public ManaService mana()                    { return this.manaService; }
    public StaminaService stamina()              { return this.staminaService; }
    public EquipmentService equipment()          { return this.equipmentService; }
    public MountService mounts()                 { return this.mountService; }
    public ReinforceService reinforce()           { return this.reinforceService; }
    public PlayerCosmeticColorService cosmeticColors() { return this.cosmeticColorService; }
    public ChatService chat()                      { return this.chatService; }
    public ChatBungeeBridge chatBridge()          { return this.chatBungeeBridge; }

    @Override
    public FileConfiguration getConfig() {
        if (this.mainConfig == null) {
            reloadConfig();
        }
        return this.mainConfig;
    }

    @Override
    public void reloadConfig() {
        File file = configBootstrapFile();
        this.mainConfig = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public void saveConfig() {
        if (this.mainConfig == null) {
            return;
        }
        File file = configBootstrapFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try {
            this.mainConfig.save(file);
        } catch (IOException exception) {
            getLogger().warning("Failed to save config: " + exception.getMessage());
        }
    }

    /**
     * Returns the {@link GuiDef} loaded from {@code gui/<name>.yml}, or a minimal
     * empty def if the file has not been loaded.
     */
    public GuiDef guiDef(String name) {
        return guiDefs.getOrDefault(name, new GuiDef(
                new org.bukkit.configuration.file.YamlConfiguration()));
    }

    /** Returns the ordered list of attribute keys defined in attributes.yml (excludes the 'elements' section). */
    public List<String> attributeKeys() {
        if (attributesConfig == null) return List.of();
        return attributesConfig.getKeys(false).stream()
                .filter(k -> !k.equals("elements"))
                .toList();
    }

    /** Returns the ordered list of element keys from attributes.yml#elements (excludes control keys). */
    public List<String> elementKeys() {
        if (attributesConfig == null) return List.of();
        ConfigurationSection sec = attributesConfig.getConfigurationSection("elements");
        if (sec == null) return List.of();
        return sec.getKeys(false).stream()
                .filter(k -> !k.equals("minimum-bonus") && !k.equals("maximum-bonus"))
                .toList();
    }

    public void reloadAttributesConfig() {
        File file = localizedConfigFile("attributes.yml");
        this.attributesConfig = YamlConfiguration.loadConfiguration(file);
    }

    public void reloadStatusConfig() {
        File file = localizedConfigFile("status.yml");
        this.statusConfig = YamlConfiguration.loadConfiguration(file);
    }

    public void reloadItemWeightsConfig() {
        File file = localizedConfigFile("item-weights.yml");
        this.itemWeightsConfig = YamlConfiguration.loadConfiguration(file);
    }

    public void reloadSkinsConfig() {
        File file = localizedConfigFile("skins.yml");
        this.skinsConfig = YamlConfiguration.loadConfiguration(file);
    }

    public void reloadPlugin() {
        reloadConfig();
        reloadSkinsConfig();
        reloadAttributesConfig();
        reloadStatusConfig();
        reloadItemWeightsConfig();
        loadGuiDefs();
        this.classRegistry.reload();
        this.skinService.reload();
        this.messageService.reload();
        this.profileService.reload();
        reloadChatSubsystem();
        if (this.cosmeticColorService != null) {
            for (org.bukkit.entity.Player online : getServer().getOnlinePlayers()) {
                this.cosmeticColorService.applyNameStyle(online);
            }
        }
        startNameStyleUpdater();
        this.statusEffectService.reload();
        this.hpService.startTicker();
        this.manaService.startTicker();
        this.staminaService.startTicker();
        if (this.equipmentService != null) this.equipmentService.reload();
        if (this.mountService != null) this.mountService.reload();
    }

    public void reloadChatSubsystem() {
        if (this.chatBungeeBridge != null) {
            this.chatBungeeBridge.unregisterChannels();
        }
        if (this.chatService != null) {
            this.chatService.reload();
        }
        if (this.chatBungeeBridge != null) {
            this.chatBungeeBridge.registerChannels();
        }
    }

    private void loadGuiDefs() {
        guiDefs.clear();
        String[] names2 = {"profile", "attributes", "skills", "class", "vip-cosmetics"};
        for (String name : names2) {
            File file = localizedConfigFile("gui/" + name + ".yml");
            if (file.exists()) {
                guiDefs.put(name, new GuiDef(YamlConfiguration.loadConfiguration(file)));
            } else {
                getLogger().warning("GUI config not found: " + file.getPath());
            }
        }
    }

    public File configRootDirectory() {
        return new File(getDataFolder(), "config");
    }

    public File configBootstrapFile() {
        return new File(configRootDirectory(), "config.yml");
    }

    public File localizedConfigDirectory() {
        return localizedConfigDirectory(activeConfigLocale());
    }

    public File localizedConfigDirectory(String locale) {
        return new File(configRootDirectory(), normalizeLocale(locale));
    }

    public File localizedConfigFile(String relativePath) {
        File localizedFile = new File(localizedConfigDirectory(), relativePath);
        if (localizedFile.exists()) {
            return localizedFile;
        }

        File fallback = new File(localizedConfigDirectory(DEFAULT_LOCALE), relativePath);
        if (fallback.exists()) {
            return fallback;
        }

        return new File(getDataFolder(), relativePath);
    }

    public File localizedConfigFile(String locale, String relativePath) {
        return new File(localizedConfigDirectory(locale), relativePath);
    }

    public File itemRootDirectory() {
        return new File(getDataFolder(), "item");
    }

    public File itemFile(String relativePath) {
        return new File(itemRootDirectory(), relativePath);
    }

    public List<String> availableConfigLocales() {
        File[] directories = configRootDirectory().listFiles(File::isDirectory);
        if (directories == null || directories.length == 0) {
            return List.of(DEFAULT_LOCALE, "en_us");
        }
        return java.util.Arrays.stream(directories)
                .map(File::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private void bootstrapConfigTree() {
        ensureBootstrapConfig();
        migrateLegacyConfigTree();
        migrateLegacyItemTree();
        extractBundledResourceTrees();
    }

    private void migrateLegacyItemTree() {
        migrateLegacyFolderToItem("equipment", "equipment");
        migrateLegacyFolderToItem("creatures", "creatures");
        migrateLegacyFolderToItem("pets", "pets");
    }

    private void migrateLegacyFolderToItem(String legacyRelative, String itemRelative) {
        File legacyDir = new File(getDataFolder(), legacyRelative);
        if (!legacyDir.exists()) {
            return;
        }
        File itemDir = itemFile(itemRelative);
        copyMissingTree(legacyDir, itemDir);
    }

    private void ensureBootstrapConfig() {
        File bootstrap = configBootstrapFile();
        if (bootstrap.exists()) {
            return;
        }

        File parent = bootstrap.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        File legacyRoot = new File(getDataFolder(), "config.yml");
        if (legacyRoot.exists()) {
            copyFile(legacyRoot, bootstrap);
            return;
        }

        if (!copyBundledResource("config/config.yml", bootstrap)) {
            getLogger().warning("Bundled bootstrap config not found: config/config.yml");
        }
    }

    private void migrateLegacyConfigTree() {
        String targetLocale = readBootstrapLocale();
        migrateLegacyFile("attributes.yml", targetLocale);
        migrateLegacyFile("skins.yml", targetLocale);
        migrateLegacyFile("status.yml", targetLocale);
        migrateLegacyFile("item-weights.yml", targetLocale);
        migrateLegacyDirectory("gui", targetLocale);
        migrateLegacyDirectory("classes", targetLocale);

        File legacyLangDir = new File(getDataFolder(), "lang");
        File[] langFiles = legacyLangDir.listFiles(file -> file.isFile() && file.getName().endsWith(".yml"));
        if (langFiles == null) {
            return;
        }
        for (File langFile : langFiles) {
            String locale = langFile.getName().replace(".yml", "");
            File localized = localizedConfigFile(locale, "lang.yml");
            if (!localized.exists()) {
                copyFile(langFile, localized);
            }
        }
    }

    private void migrateLegacyFile(String relativePath, String locale) {
        File legacyFile = new File(getDataFolder(), relativePath);
        if (!legacyFile.exists()) {
            return;
        }
        File localizedFile = localizedConfigFile(locale, relativePath);
        if (!localizedFile.exists()) {
            copyFile(legacyFile, localizedFile);
        }
    }

    private void migrateLegacyDirectory(String relativePath, String locale) {
        File legacyDir = new File(getDataFolder(), relativePath);
        if (!legacyDir.exists()) {
            return;
        }
        File localizedDir = localizedConfigFile(locale, relativePath);
        copyMissingTree(legacyDir, localizedDir);
    }

    private void extractBundledResourceTrees() {
        try (var jar = new java.util.jar.JarFile(getFile())) {
            jar.entries().asIterator().forEachRemaining(entry -> {
                String name = entry.getName();
                if (entry.isDirectory()) {
                    return;
                }
                boolean isConfig = name.startsWith("config/");
                boolean isItem = name.startsWith("item/");
                if (!isConfig && !isItem) {
                    return;
                }
                File out = new File(getDataFolder(), name);
                if (out.exists()) {
                    return;
                }
                File parent = out.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try (InputStream in = getResource(name)) {
                    if (in != null) {
                        Files.copy(in, out.toPath());
                    }
                } catch (Exception exception) {
                    getLogger().warning("Failed to extract " + name + ": " + exception.getMessage());
                }
            });
        } catch (Exception exception) {
            getLogger().warning("Failed to extract bundled resource trees: " + exception.getMessage());
        }
    }

    private String activeConfigLocale() {
        return normalizeLocale(getConfig().getString("default-locale", DEFAULT_LOCALE));
    }

    private String readBootstrapLocale() {
        return normalizeLocale(YamlConfiguration.loadConfiguration(configBootstrapFile())
                .getString("default-locale", DEFAULT_LOCALE));
    }

    private String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return DEFAULT_LOCALE;
        }
        return locale.toLowerCase(Locale.ROOT);
    }

    private boolean copyBundledResource(String resourcePath, File destination) {
        try (InputStream in = getResource(resourcePath)) {
            if (in == null) {
                return false;
            }
            Files.copy(in, destination.toPath());
            return true;
        } catch (Exception exception) {
            getLogger().warning("Failed to extract " + resourcePath + ": " + exception.getMessage());
            return false;
        }
    }

    private void copyFile(File source, File destination) {
        try {
            File parent = destination.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.copy(source.toPath(), destination.toPath());
        } catch (Exception exception) {
            getLogger().warning("Failed to copy " + source.getPath() + " to " + destination.getPath()
                    + ": " + exception.getMessage());
        }
    }

    private void copyMissingTree(File source, File destination) {
        if (source.isDirectory()) {
            if (!destination.exists()) {
                destination.mkdirs();
            }
            File[] children = source.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                copyMissingTree(child, new File(destination, child.getName()));
            }
            return;
        }

        if (!destination.exists()) {
            copyFile(source, destination);
        }
    }

    private void registerCommands() {
        AmCommand executor = new AmCommand(this);
        registerCommand("am", "Arad MMO command root", List.of("aradmmo"), new BasicCommand() {
            @Override
            public void execute(CommandSourceStack stack, String[] args) {
                CommandSender sender = stack.getSender();
                executor.onCommand(sender, null, "am", args);
            }

            @Override
            public java.util.Collection<String> suggest(CommandSourceStack stack, String[] args) {
                CommandSender sender = stack.getSender();
                List<String> suggestions = executor.onTabComplete(sender, null, "am", args);
                return suggestions == null ? List.of() : suggestions;
            }
        });
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new CombatListener(this, this.combatService), this);
        getServer().getPluginManager().registerEvents(new PlayerSessionListener(this), this);
        getServer().getPluginManager().registerEvents(new CoreChatListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new StaminaListener(this), this);
        getServer().getPluginManager().registerEvents(new MobKillListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        // 装备系统监听
        getServer().getPluginManager().registerEvents(new EquipmentListener(this), this);
        getServer().getPluginManager().registerEvents(new ElytraListener(this), this);
        getServer().getPluginManager().registerEvents(new BackpackListener(this), this);
        getServer().getPluginManager().registerEvents(new PetListener(this), this);
        getServer().getPluginManager().registerEvents(new PetSkillGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new MountListener(this), this);
        getServer().getPluginManager().registerEvents(new ReinforceListener(), this);
    }

    private void startNameStyleUpdater() {
        if (this.nameStyleTask != null) {
            this.nameStyleTask.cancel();
        }
        this.nameStyleTask = getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            if (this.cosmeticColorService == null) return;
            for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                this.cosmeticColorService.applyNameStyle(online);
            }
        }, 40L, 100L);
    }
}
