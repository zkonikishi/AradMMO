package cn.aradmmo.item.equipment.classification;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * In-memory index of template IDs by Arad classification dimensions.
 */
public final class AradTemplateCatalog {

    private final Map<String, AradItemClass> byTemplateId;
    private final Map<AradItemGroup, List<String>> byGroup;
    private final Map<AradEquipCategory, List<String>> byCategory;
    private final Map<AradEquipPart, List<String>> byPart;
    private final Map<AradEquipmentSource, List<String>> bySource;

    private AradTemplateCatalog(Map<String, AradItemClass> byTemplateId,
                                Map<AradItemGroup, List<String>> byGroup,
                                Map<AradEquipCategory, List<String>> byCategory,
                                Map<AradEquipPart, List<String>> byPart,
                                Map<AradEquipmentSource, List<String>> bySource) {
        this.byTemplateId = Map.copyOf(byTemplateId);
        this.byGroup = copyEnumListMap(byGroup);
        this.byCategory = copyEnumListMap(byCategory);
        this.byPart = copyEnumListMap(byPart);
        this.bySource = copyEnumListMap(bySource);
    }

    public static AradTemplateCatalog fromTemplates(YamlConfiguration templatesConfig) {
        Map<String, AradItemClass> byTemplate = new HashMap<>();
        Map<AradItemGroup, List<String>> group = new EnumMap<>(AradItemGroup.class);
        Map<AradEquipCategory, List<String>> category = new EnumMap<>(AradEquipCategory.class);
        Map<AradEquipPart, List<String>> part = new EnumMap<>(AradEquipPart.class);
        Map<AradEquipmentSource, List<String>> source = new EnumMap<>(AradEquipmentSource.class);

        if (templatesConfig != null) {
            for (String id : templatesConfig.getKeys(false)) {
                ConfigurationSection sec = templatesConfig.getConfigurationSection(id);
                if (sec == null) continue;
                AradItemClass cls = AradItemClassifier.classify(id, sec);
                byTemplate.put(id, cls);
                group.computeIfAbsent(cls.group(), ignored -> new ArrayList<>()).add(id);
                category.computeIfAbsent(cls.category(), ignored -> new ArrayList<>()).add(id);
                part.computeIfAbsent(cls.part(), ignored -> new ArrayList<>()).add(id);
                source.computeIfAbsent(cls.source(), ignored -> new ArrayList<>()).add(id);
            }
        }

        group.values().forEach(v -> v.sort(String.CASE_INSENSITIVE_ORDER));
        category.values().forEach(v -> v.sort(String.CASE_INSENSITIVE_ORDER));
        part.values().forEach(v -> v.sort(String.CASE_INSENSITIVE_ORDER));
        source.values().forEach(v -> v.sort(String.CASE_INSENSITIVE_ORDER));
        return new AradTemplateCatalog(byTemplate, group, category, part, source);
    }

    public AradItemClass classify(String templateId) {
        return byTemplateId.get(templateId);
    }

    public List<String> templateIds(AradItemGroup g) {
        return byGroup.getOrDefault(g, List.of());
    }

    public List<String> templateIds(AradEquipCategory c) {
        return byCategory.getOrDefault(c, List.of());
    }

    public List<String> templateIds(AradEquipPart p) {
        return byPart.getOrDefault(p, List.of());
    }

    public List<String> templateIds(AradEquipmentSource s) {
        return bySource.getOrDefault(s, List.of());
    }

    private static <E extends Enum<E>> Map<E, List<String>> copyEnumListMap(Map<E, List<String>> src) {
        Map<E, List<String>> out = new HashMap<>();
        for (Map.Entry<E, List<String>> e : src.entrySet()) {
            out.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return Map.copyOf(out);
    }
}
