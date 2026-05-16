package cn.aradmmo.item.creatures.pet;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Runtime + persisted progression data for one owner's one pet definition.
 */
public final class PetCompanionProfile {

    private int level;
    private double experience;
    private int skillPoints;
    private PetBehaviorMode behaviorMode;
    private final Map<String, Integer> attributes;
    private final Set<String> learnedSkills;

    public PetCompanionProfile(int level, double experience, int skillPoints,
                               Map<String, Integer> attributes, Set<String> learnedSkills,
                               PetBehaviorMode behaviorMode) {
        this.level = Math.max(1, level);
        this.experience = Math.max(0.0, experience);
        this.skillPoints = Math.max(0, skillPoints);
        this.behaviorMode = behaviorMode == null ? PetBehaviorMode.FOLLOW : behaviorMode;
        this.attributes = new LinkedHashMap<>();
        attributes.forEach((k, v) -> this.attributes.put(k.toLowerCase(Locale.ROOT), Math.max(0, v)));
        this.learnedSkills = new LinkedHashSet<>();
        learnedSkills.forEach(s -> this.learnedSkills.add(s.toLowerCase(Locale.ROOT)));
    }

    public int level() { return level; }
    public double experience() { return experience; }
    public int skillPoints() { return skillPoints; }
    public PetBehaviorMode behaviorMode() { return behaviorMode; }
    public Map<String, Integer> attributes() { return Map.copyOf(attributes); }
    public Set<String> learnedSkills() { return Set.copyOf(learnedSkills); }

    public PetBehaviorMode cycleBehaviorMode() {
        behaviorMode = behaviorMode.next();
        return behaviorMode;
    }

    public void setBehaviorMode(PetBehaviorMode mode) {
        this.behaviorMode = mode == null ? PetBehaviorMode.FOLLOW : mode;
    }

    public int attribute(String key) {
        if (key == null) return 0;
        return attributes.getOrDefault(key.toLowerCase(Locale.ROOT), 0);
    }

    public double expToNext() {
        return 25.0 + (level - 1) * 15.0;
    }

    public boolean gainExperience(double amount) {
        if (amount <= 0.0) return false;
        experience += amount;
        boolean leveled = false;
        while (experience >= expToNext()) {
            experience -= expToNext();
            level++;
            skillPoints += 1;
            // Automatic pet growth on level up.
            attributes.merge("strength", 1, Integer::sum);
            attributes.merge("vitality", 1, Integer::sum);
            if (level % 2 == 0) {
                attributes.merge("spirit", 1, Integer::sum);
            }
            if (level % 3 == 0) {
                attributes.merge("intellect", 1, Integer::sum);
            }
            leveled = true;
        }
        return leveled;
    }

    public boolean learnSkill(String skillId) {
        if (skillId == null || skillId.isBlank() || skillPoints <= 0) return false;
        String id = skillId.toLowerCase(Locale.ROOT);
        if (learnedSkills.contains(id)) return false;
        learnedSkills.add(id);
        skillPoints -= 1;
        return true;
    }
}
