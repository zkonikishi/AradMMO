package cn.aradmmo.item.equipment.classification;

import java.util.List;

/**
 * Immutable Arad classification tuple for one template item.
 */
public record AradItemClass(
        AradItemGroup group,
        AradEquipCategory category,
        AradEquipPart part,
        AradEquipmentSource source,
        List<String> tags
) {
    public AradItemClass {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
