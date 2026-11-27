package com.commitmate.backend.matching;

import java.util.*;

public class CoverageCalculator {

    /** Coverage(T): 기술마다 팀 최고 레벨 기준 (합산 아님). */
    public Score coverage(List<SkillRequirement> reqs, List<Map<Long, Integer>> members) {
        members.forEach(Levels::checkAll);
        return MatchingCalculator.weightedFulfillment(reqs,
                s -> members.stream().mapToInt(m -> m.getOrDefault(s, 0)).max().orElse(0));
    }

    /** 기여도. 팀 커버리지가 이미 100이면 empty (화면: "요구 기술을 모두 충족한 팀"). */
    public Optional<Score> contribution(List<SkillRequirement> reqs, List<Map<Long, Integer>> members, Map<Long, Integer> applicant) {
        Score before = coverage(reqs, members);
        if (before.compareTo(Score.HUNDRED) == 0) return Optional.empty();
        List<Map<Long, Integer>> after = new ArrayList<>(members);
        after.add(applicant);
        Score now = coverage(reqs, after);
        // 100 × (now − before) / (100 − before)
        Score gain = now.plus(before.times(-1, 1));
        Score room = Score.HUNDRED.plus(before.times(-1, 1));
        return Optional.of(gain.times(100L * room.den(), room.num()));
    }
}
