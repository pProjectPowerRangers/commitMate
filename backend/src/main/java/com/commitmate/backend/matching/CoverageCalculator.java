package com.commitmate.backend.matching;

import java.util.*;

/**
 * 팀 커버리지와 지원자 기여도 계산기. "이 사람이 팀에 뭘 보태나"
 *
 * <ul>
 *   <li>coverage = 기술마다 팀원 중 최고 레벨로 계산한 충족도 (합산 아님. 레벨 1 세 명 ≠ 레벨 3 한 명)</li>
 *   <li>contribution = 100 × (합류 후 − 지금) / (100 − 지금) = 남은 빈칸 중 몇 %를 채우나</li>
 * </ul>
 *
 * <p>예: 지금 60(빈칸 40), 합류 후 65 → 전체로는 +5지만, 빈칸 40 중 5를 채웠으니 기여도 5/40 = 12.5 (표시 13)
 * <p>팀이 이미 100이면 기여도 없음(Optional.empty) → 화면 "요구 기술을 모두 충족한 팀"
 * <p>members = 승인된 팀원만(팀장 포함, 검토 중 지원자 제외). 원본 목록은 바꾸지 않는다.
 */
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
