package cn.aradmmo.item.equipment.classification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Converts template config to Arad classification metadata.
 */
public final class AradItemClassifier {

    private AradItemClassifier() {
    }

    public static AradItemClass classify(String templateId, ConfigurationSection sec) {
        Material material = Material.matchMaterial(sec.getString("material", "STONE"));

        AradItemGroup group = AradItemGroup.parse(sec.getString("arad-group"), defaultGroup(material));
        AradEquipCategory category = AradEquipCategory.parse(sec.getString("arad-category"), defaultCategory(group, material));
        AradEquipPart part = AradEquipPart.parse(sec.getString("arad-part"), defaultPart(templateId, material));
        AradEquipmentSource source = AradEquipmentSource.parse(sec.getString("arad-source"), defaultSource(category, part));

        List<String> tags = new ArrayList<>();
        for (String raw : sec.getStringList("arad-tags")) {
            if (raw == null || raw.isBlank()) continue;
            tags.add(raw.trim().toLowerCase(Locale.ROOT));
        }

        return new AradItemClass(group, category, part, source, tags);
    }

    private static AradItemGroup defaultGroup(Material material) {
        if (material == null) return AradItemGroup.OTHER;
        if (isEquipmentMaterial(material)) return AradItemGroup.EQUIPMENT;
        if (isConsumableMaterial(material)) return AradItemGroup.CONSUMABLE;
        if (isMaterialMaterial(material)) return AradItemGroup.MATERIAL;
        return AradItemGroup.OTHER;
    }

    private static AradEquipCategory defaultCategory(AradItemGroup group, Material material) {
        if (group != AradItemGroup.EQUIPMENT) return AradEquipCategory.NONE;
        if (material == null) return AradEquipCategory.NONE;

        String name = material.name();
        if (name.endsWith("SWORD") || name.endsWith("AXE") || name.endsWith("BOW")
                || name.endsWith("CROSSBOW") || name.endsWith("TRIDENT")
                || name.endsWith("MACE")) {
            return AradEquipCategory.WEAPON;
        }
        if (name.endsWith("HELMET") || name.endsWith("CHESTPLATE")
                || name.endsWith("LEGGINGS") || name.endsWith("BOOTS")) {
            return AradEquipCategory.ARMOR;
        }
        if (name.contains("NUGGET") || name.endsWith("INGOT")) {
            return AradEquipCategory.ACCESSORY;
        }
        if (name.contains("AMETHYST") || name.contains("STAR") || name.contains("FEATHER")) {
            return AradEquipCategory.SPECIAL;
        }
        return AradEquipCategory.NONE;
    }

    private static AradEquipPart defaultPart(String templateId, Material material) {
        String id = templateId == null ? "" : templateId.toLowerCase(Locale.ROOT);
        if (id.contains("helmet")) return AradEquipPart.HELMET;
        if (id.contains("chestplate") || id.contains("top")) return AradEquipPart.CHESTPLATE;
        if (id.contains("leggings") || id.contains("bottom")) return AradEquipPart.LEGGINGS;
        if (id.contains("boots") || id.contains("shoes")) return AradEquipPart.BOOTS;
        if (id.contains("belt")) return AradEquipPart.BELT;
        if (id.contains("ring")) return AradEquipPart.RING;
        if (id.contains("necklace")) return AradEquipPart.NECKLACE;
        if (id.contains("bracelet")) return AradEquipPart.BRACELET;
        if (id.contains("earring")) return AradEquipPart.EARRING;
        if (id.contains("magic") || id.contains("gem") || id.contains("stone")) return AradEquipPart.MAGIC_STONE;
        if (id.contains("title")) return AradEquipPart.TITLE;
        if (id.contains("pet")) return AradEquipPart.PET;
        if (id.contains("mount")) return AradEquipPart.MOUNT;

        if (material == null) return AradEquipPart.OTHER;
        String name = material.name();
        if (name.endsWith("SWORD") || name.endsWith("AXE") || name.endsWith("BOW")
                || name.endsWith("CROSSBOW") || name.endsWith("TRIDENT")
                || name.endsWith("MACE")) {
            return AradEquipPart.WEAPON;
        }
        if (name.endsWith("HELMET")) return AradEquipPart.HELMET;
        if (name.endsWith("CHESTPLATE")) return AradEquipPart.CHESTPLATE;
        if (name.endsWith("LEGGINGS")) return AradEquipPart.LEGGINGS;
        if (name.endsWith("BOOTS")) return AradEquipPart.BOOTS;

        return AradEquipPart.OTHER;
    }

    private static AradEquipmentSource defaultSource(AradEquipCategory category, AradEquipPart part) {
        if (part == AradEquipPart.TITLE) return AradEquipmentSource.TITLES;
        return switch (category) {
            case WEAPON -> AradEquipmentSource.WEAPONS;
            case ARMOR -> AradEquipmentSource.ARMOR;
            case ACCESSORY -> AradEquipmentSource.ACCESSORIES;
            case SPECIAL -> AradEquipmentSource.SPECIAL_EQUIPMENT;
            case NONE -> AradEquipmentSource.OTHER;
        };
    }

    private static boolean isEquipmentMaterial(Material material) {
        String name = material.name();
        return name.endsWith("HELMET")
                || name.endsWith("CHESTPLATE")
                || name.endsWith("LEGGINGS")
                || name.endsWith("BOOTS")
                || name.endsWith("SWORD")
                || name.endsWith("AXE")
                || name.endsWith("BOW")
                || name.endsWith("CROSSBOW")
                || name.endsWith("TRIDENT")
                || name.endsWith("MACE")
                || name.contains("ELYTRA");
    }

    private static boolean isConsumableMaterial(Material material) {
        return material.isEdible()
                || material == Material.POTION
                || material == Material.SPLASH_POTION
                || material == Material.LINGERING_POTION;
    }

    private static boolean isMaterialMaterial(Material material) {
        String name = material.name();
        return name.endsWith("_INGOT")
                || name.endsWith("_NUGGET")
                || name.endsWith("_SHARD")
                || name.endsWith("_GEM")
                || name.endsWith("_DUST")
                || name.endsWith("_FRAGMENT")
                || name.endsWith("_ESSENCE")
                || name.endsWith("_SCRAP");
    }
}
