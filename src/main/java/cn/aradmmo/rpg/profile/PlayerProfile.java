package cn.aradmmo.rpg.profile;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PlayerProfile {
    private final String name;
    private int level;
    private double experience;
    private double balance;
    private String vipTier;
    private String nameGradient;
    private String chatGradient;
    private String archetype;
    private String gender; // "male", "female", or "" (unset)
    private int statPoints;
    private int skillPoints;
    private final Map<String, Integer> attributes;
    private final Map<String, Integer> elementAttack;
    private final Map<String, Integer> elementResist;
    private final Map<String, Integer> skills;

    public PlayerProfile(String name, int level, double experience, double balance, String vipTier,
                         String nameGradient, String chatGradient,
                         String archetype, String gender, int statPoints, int skillPoints,
                         Map<String, Integer> attributes, Map<String, Integer> skills,
                         Map<String, Integer> elementAttack, Map<String, Integer> elementResist) {
        this.name = name;
        this.level = level;
        this.experience = experience;
        this.balance = balance;
        this.vipTier = vipTier;
        this.nameGradient = nameGradient == null ? "" : nameGradient;
        this.chatGradient = chatGradient == null ? "" : chatGradient;
        this.archetype = archetype;
        this.gender = gender == null ? "" : gender;
        this.statPoints = statPoints;
        this.skillPoints = skillPoints;
        this.attributes = new LinkedHashMap<>(attributes);
        this.skills = new HashMap<>(skills);
        this.elementAttack = new LinkedHashMap<>(elementAttack);
        this.elementResist = new LinkedHashMap<>(elementResist);
    }

    public String name() {
        return name;
    }

    public int level() {
        return level;
    }

    public void level(int level) {
        this.level = Math.max(1, level);
    }

    public double experience() {
        return experience;
    }

    public void experience(double experience) {
        this.experience = Math.max(0D, experience);
    }

    public double balance() {
        return balance;
    }

    public void balance(double balance) {
        this.balance = Math.max(0D, balance);
    }

    public String vipTier() {
        return vipTier;
    }

    public void vipTier(String vipTier) {
        this.vipTier = vipTier;
    }

    public String nameGradient() {
        return nameGradient;
    }

    public void nameGradient(String nameGradient) {
        this.nameGradient = nameGradient == null ? "" : nameGradient;
    }

    public String chatGradient() {
        return chatGradient;
    }

    public void chatGradient(String chatGradient) {
        this.chatGradient = chatGradient == null ? "" : chatGradient;
    }

    public String archetype() {
        return archetype;
    }

    public void archetype(String archetype) {
        this.archetype = archetype;
    }

    public String gender() {
        return gender;
    }

    public void gender(String gender) {
        this.gender = gender == null ? "" : gender.toLowerCase(java.util.Locale.ROOT);
    }

    public int statPoints() {
        return statPoints;
    }

    public void statPoints(int statPoints) {
        this.statPoints = Math.max(0, statPoints);
    }

    public int skillPoints() {
        return skillPoints;
    }

    public void skillPoints(int skillPoints) {
        this.skillPoints = Math.max(0, skillPoints);
    }

    public int attribute(String key) {
        return attributes.getOrDefault(key, 0);
    }

    public void attribute(String key, int value) {
        attributes.put(key, Math.max(0, value));
    }

    public Map<String, Integer> attributes() {
        return Map.copyOf(attributes);
    }

    public int skillLevel(String skillId) {
        return skills.getOrDefault(skillId, 0);
    }

    public void skillLevel(String skillId, int level) {
        skills.put(skillId, Math.max(0, level));
    }

    public Map<String, Integer> skills() {
        return Map.copyOf(skills);
    }

    // ── Element attack / resist ───────────────────────────────────────────────

    public int elementAttack(String key) {
        return elementAttack.getOrDefault(key, 0);
    }

    public void elementAttack(String key, int value) {
        elementAttack.put(key, value);
    }

    public int elementResist(String key) {
        return elementResist.getOrDefault(key, 0);
    }

    public void elementResist(String key, int value) {
        elementResist.put(key, value);
    }

    public Map<String, Integer> elementAttacks() {
        return Map.copyOf(elementAttack);
    }

    public Map<String, Integer> elementResists() {
        return Map.copyOf(elementResist);
    }
}
