package com.commitmate.backend.matching;

public record SkillRequirement(long skillId, int requiredLevel, boolean mandatory) {
    public SkillRequirement {
        Levels.check(requiredLevel);
    }
    int weight() { return mandatory ? 3 : 1; }
}
