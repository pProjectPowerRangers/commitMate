package com.commitmate.backend.matching;

import java.util.*;

public class MatchingCalculator {

    /** skillMatch = 100 × Σ w·min(1, L/r) / Σ w */
    public Score skillMatch(List<SkillRequirement> reqs, Map<Long, Integer> userSkills) {
        Levels.checkAll(userSkills);
        return weightedFulfillment(reqs, s -> userSkills.getOrDefault(s, 0));
    }

    /** availability = 70 × min(1, h/H) + 30 × (시간대 겹침 ? 1 : 0) */
    public Score availabilityMatch(Availability project, Availability user) {
        Integer H = project.weeklyHours();
        if (H == null || H < 1) throw new IllegalArgumentException("project weeklyHours must be >= 1");
        if (project.slots().isEmpty()) throw new IllegalArgumentException("project slots required");
        int h = user.weeklyHours() == null ? 0 : Math.max(0, user.weeklyHours());
        boolean overlap = user.slots().stream().anyMatch(project.slots()::contains);
        return Score.of(70L * Math.min(h, H) + (overlap ? 30L * H : 0), H);
    }

    public Score interestMatch(String projectField, Set<String> userInterests) {
        return userInterests.contains(projectField) ? Score.HUNDRED : Score.ZERO;
    }

    /** matchScore = 0.6 skill + 0.2 interest + 0.2 availability */
    public Score matchScore(Score skill, Score interest, Score availability) {
        return skill.times(6, 10).plus(interest.times(2, 10)).plus(availability.times(2, 10));
    }

    static Score weightedFulfillment(List<SkillRequirement> reqs, java.util.function.ToIntFunction<Long> level) {
        if (reqs == null || reqs.isEmpty()) throw new IllegalArgumentException("at least one skill requirement");
        long n = 0, d = 0;
        for (SkillRequirement r : reqs) {
            int L = level.applyAsInt(r.skillId());
            n += (long) r.weight() * Math.min(L, r.requiredLevel()) * (Levels.LCM / r.requiredLevel());
            d += (long) r.weight() * Levels.LCM;
        }
        return Score.of(100 * n, d);
    }
}
