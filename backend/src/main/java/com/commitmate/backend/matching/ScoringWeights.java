package com.commitmate.backend.matching;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 계산 규칙의 숫자(가중치·비율)를 application.yml 에서 읽어오는 설정 (계산 규칙 결정 5).
 *
 * <pre>
 * commitmate:
 *   scoring:
 *     skill-weight:        { mandatory: 3, preferred: 1 }               # 필수 3, 우대 1
 *     match-ratio:         { skill: 60, interest: 20, availability: 20 } # 합 100
 *     availability-points: { hours: 70, slot: 30 }                      # 합 100
 * </pre>
 *
 * <ul>
 *   <li>yml에 값이 없으면 @DefaultValue(현재 확정값)가 들어간다</li>
 *   <li>잘못된 값(음수, 합이 100이 아님)이면 서버가 뜰 때 바로 실패한다 → 잘못된 점수가 조용히 나가는 것보다 낫다</li>
 *   <li>Spring 없이 쓰는 순수 테스트에서는 {@link #DEFAULT}를 쓴다</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "commitmate.scoring")
public record ScoringWeights(
        @DefaultValue SkillWeight skillWeight,
        @DefaultValue MatchRatio matchRatio,
        @DefaultValue AvailabilityPoints availabilityPoints) {

    /** 현재 확정값 (scoring-rules.md 결정 5). yml 기본값과 같아야 한다. */
    public static final ScoringWeights DEFAULT = new ScoringWeights(
            new SkillWeight(3, 1), new MatchRatio(60, 20, 20), new AvailabilityPoints(70, 30));

    public ScoringWeights {
        if (skillWeight == null) skillWeight = DEFAULT.skillWeight;
        if (matchRatio == null) matchRatio = DEFAULT.matchRatio;
        if (availabilityPoints == null) availabilityPoints = DEFAULT.availabilityPoints;
    }

    /** 기술 중요도 w. 필수가 우대보다 작으면 "필수"의 의미가 없어지므로 막는다. */
    public record SkillWeight(@DefaultValue("3") int mandatory, @DefaultValue("1") int preferred) {
        public SkillWeight {
            if (preferred < 1) throw new IllegalArgumentException("skill-weight.preferred must be >= 1: " + preferred);
            if (mandatory < preferred)
                throw new IllegalArgumentException("skill-weight.mandatory(" + mandatory + ") must be >= preferred(" + preferred + ")");
        }
        int of(SkillRequirement r) { return r.mandatory() ? mandatory : preferred; }
    }

    /** matchScore 비율(%). skill + interest + availability = 100 */
    public record MatchRatio(@DefaultValue("60") int skill, @DefaultValue("20") int interest, @DefaultValue("20") int availability) {
        public MatchRatio {
            if (skill < 0 || interest < 0 || availability < 0 || skill + interest + availability != 100)
                throw new IllegalArgumentException("match-ratio must be >= 0 and sum to 100: " + skill + "/" + interest + "/" + availability);
        }
    }

    /** availability 점수 배분. hours(주당 시간) + slot(시간대 겹침) = 100 */
    public record AvailabilityPoints(@DefaultValue("70") int hours, @DefaultValue("30") int slot) {
        public AvailabilityPoints {
            if (hours < 0 || slot < 0 || hours + slot != 100)
                throw new IllegalArgumentException("availability-points must be >= 0 and sum to 100: " + hours + "/" + slot);
        }
    }
}
