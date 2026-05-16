package cn.aradmmo.rpg.profile;

public record ProfileMutationResult(PlayerProfile profile, boolean leveledUp, int levelsGained,
									int statPointsGained, int skillPointsGained) {
}
