package cn.aradmmo.rpg.classes;

import cn.aradmmo.core.AradMmoPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads and provides access to all registered {@link AradClass} instances.
 *
 * <p>Each class is defined by a YAML file inside:
 * <pre>plugins/Arad MMO/config/&lt;locale&gt;/classes/&lt;stage&gt;/&lt;class-id&gt;.yml</pre>
 * Stage folders are {@code stage-0}, {@code stage-1}, {@code stage-2}.
 *
 * <p>Example path: {@code config/zh_cn/classes/stage-1/slayer.yml}
 */
public final class ClassRegistry {

    private final AradMmoPlugin plugin;
    private final Map<String, ClassDefinition> classes = new LinkedHashMap<>();

    public ClassRegistry(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    // ── API ──────────────────────────────────────────────────────────────────

    /** Returns the class with the given id, or {@code null} if not found. */
    public ClassDefinition get(String classId) {
        return classes.get(classId.toLowerCase());
    }

    /** Returns all registered classes, in load order. */
    public Collection<ClassDefinition> all() {
        return Collections.unmodifiableCollection(classes.values());
    }

    /** Returns all registered class IDs. */
    public java.util.Set<String> ids() {
        return Collections.unmodifiableSet(classes.keySet());
    }

    // ── Loading ──────────────────────────────────────────────────────────────

    /**
     * Reads class YAML files from the active locale {@code classes/} directory and registers them.
     * Clears any previously loaded classes first.
     */
    public void reload() {
        classes.clear();
        File classesDir = new File(plugin.localizedConfigDirectory(), "classes");
        if (!classesDir.exists()) {
            plugin.getLogger().warning("[ClassRegistry] classes folder not found: " + classesDir.getPath());
            return;
        }
        int count = 0;
        for (File stageDir : sortedSubdirs(classesDir)) {
            if (!stageDir.getName().startsWith("stage-")) continue;
            for (File file : sortedFiles(stageDir)) {
                if (!file.getName().endsWith(".yml")) continue;
                try {
                    ClassDefinition cls = loadFile(file);
                    classes.put(cls.id(), cls);
                    count++;
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING,
                            "[ClassRegistry] Failed to load class file: " + file.getPath(), ex);
                }
            }
        }
        plugin.getLogger().info("[ClassRegistry] Loaded " + count + " classes.");
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private ClassDefinition loadFile(File file) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        // Class ID defaults to filename without extension
        String id = yml.getString("id", file.getName().replace(".yml", "")).toLowerCase();
        String display       = yml.getString("display", id);
        String gender        = yml.getString("gender", "any").toLowerCase();
        int    stage         = yml.getInt("stage", 0);
        int    reqLevel      = yml.getInt("requires-level", 1);
        String parent        = yml.getString("parent", "");
        String combatStyle   = yml.getString("combat-style", "balanced");
        String armorMastery  = yml.getString("armor-mastery", "").trim().toUpperCase();
        String defaultSkin   = yml.getString("default-skin", gender.equals("female") ? "alex" : "steve");
        boolean external     = yml.getBoolean("external", false);

        Map<String, Integer> attrs = new LinkedHashMap<>();
        if (yml.isConfigurationSection("base-attributes")) {
            var sec = yml.getConfigurationSection("base-attributes");
            for (String key : sec.getKeys(false)) {
                attrs.put(key.toLowerCase(), sec.getInt(key, 5));
            }
        }
        // Defaults for any missing attributes
        attrs.putIfAbsent("strength",  5);
        attrs.putIfAbsent("spirit",    5);
        attrs.putIfAbsent("intellect", 5);
        attrs.putIfAbsent("vitality",  5);

        // Base element attack (optional, defaults to 0)
        Map<String, Integer> elemAtk = new LinkedHashMap<>();
        if (yml.isConfigurationSection("base-elements.attack")) {
            var sec = yml.getConfigurationSection("base-elements.attack");
            for (String key : sec.getKeys(false)) {
                elemAtk.put(key.toLowerCase(), sec.getInt(key, 0));
            }
        }

        // Base element resistance (optional, defaults to 0)
        Map<String, Integer> elemRes = new LinkedHashMap<>();
        if (yml.isConfigurationSection("base-elements.resist")) {
            var sec = yml.getConfigurationSection("base-elements.resist");
            for (String key : sec.getKeys(false)) {
                elemRes.put(key.toLowerCase(), sec.getInt(key, 0));
            }
        }

        return new ClassDefinition(id, display, gender, stage, reqLevel, parent,
            combatStyle, armorMastery, attrs, elemAtk, elemRes, defaultSkin, external);
    }

    private File[] sortedSubdirs(File dir) {
        File[] subs = dir.listFiles(File::isDirectory);
        if (subs == null) return new File[0];
        java.util.Arrays.sort(subs, java.util.Comparator.comparing(File::getName));
        return subs;
    }

    private File[] sortedFiles(File dir) {
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".yml"));
        if (files == null) return new File[0];
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        return files;
    }
}

