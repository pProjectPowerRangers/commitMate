package com.commitmate.backend.matching;

import java.util.*;
/**
 * 매칭 점수 계산기. 공식의 기호는 아래와 같다.
 *
 * <ul>
 *   <li>L = 사용자(또는 팀원)가 가진 기술 레벨 1~4, 미보유면 0</li>
 *   <li>r = 프로젝트가 요구하는 레벨 1~4 (팀장이 등록, 기본 2)</li>
 *   <li>w = 가중치. 기본 필수 3, 우대 1 ({@link ScoringWeights.SkillWeight}, application.yml)</li>
 *   <li>h = 사용자 주당 가능 시간, H = 프로젝트 기대 시간 (1~40)</li>
 * </ul>
 *
 * <p>L / r 은 요구 대비 충족 비율이고, 요구 초과분은 min(1, L/r)로 잘라 점수에 넣지 않는다.
 * <p>참고: docs/matching/scoring-rules.md "0. 기호"
 */

// 이 사람이 공고에 얼마나 맞나
public class MatchingCalculator {

    private final ScoringWeights weights;

    /** 확정 기본값(필수 3·우대 1, 60/20/20, 70/30)으로 만든다. 순수 단위 테스트용. */
    public MatchingCalculator() { this(ScoringWeights.DEFAULT); }

    /** yml에서 읽은 가중치로 만든다. Spring에서는 {@link MatchingConfig}가 이 생성자를 쓴다. */
    public MatchingCalculator(ScoringWeights weights) { this.weights = Objects.requireNonNull(weights); }

    /** skillMatch = 100 × Σ w·min(1, L/r) / Σ w */
    public Score skillMatch(List<SkillRequirement> reqs, Map<Long, Integer> userSkills) {
        Levels.checkAll(userSkills);
        return weightedFulfillment(reqs, weights.skillWeight(), s -> userSkills.getOrDefault(s, 0));
    }

    /** availability = 70 × min(1, h/H) + 30 × (시간대 겹침 ? 1 : 0). 70/30은 yml availability-points */
    public Score availabilityMatch(Availability project, Availability user) {
        Integer H = project.weeklyHours();
        if (H == null || H < 1) throw new IllegalArgumentException("project weeklyHours must be >= 1");
        if (project.slots().isEmpty()) throw new IllegalArgumentException("project slots required");
        int h = user.weeklyHours() == null ? 0 : Math.max(0, user.weeklyHours());
        boolean overlap = user.slots().stream().anyMatch(project.slots()::contains);
        var p = weights.availabilityPoints();
        return Score.of((long) p.hours() * Math.min(h, H) + (overlap ? (long) p.slot() * H : 0), H);
    }

    public Score interestMatch(String projectField, Set<String> userInterests) {
        return userInterests.contains(projectField) ? Score.HUNDRED : Score.ZERO;
    }

    /** matchScore = 0.6 skill + 0.2 interest + 0.2 availability. 60/20/20은 yml match-ratio */
    public Score matchScore(Score skill, Score interest, Score availability) {
        var r = weights.matchRatio();
        return skill.times(r.skill(), 100).plus(interest.times(r.interest(), 100)).plus(availability.times(r.availability(), 100));
    }

    static Score weightedFulfillment(List<SkillRequirement> reqs, ScoringWeights.SkillWeight w,
                                     java.util.function.ToIntFunction<Long> level) {
        if (reqs == null || reqs.isEmpty()) throw new IllegalArgumentException("at least one skill requirement");
        long n = 0, d = 0;
        for (SkillRequirement r : reqs) {
            int L = level.applyAsInt(r.skillId());
            n += (long) w.of(r) * Math.min(L, r.requiredLevel()) * (Levels.LCM / r.requiredLevel());
            d += (long) w.of(r) * Levels.LCM;
        }
        return Score.of(100 * n, d);
    }
}
