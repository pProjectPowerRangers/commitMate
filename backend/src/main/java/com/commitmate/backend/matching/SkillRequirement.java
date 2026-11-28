package com.commitmate.backend.matching;

/**
 * 프로젝트가 요구하는 기술 1개. 팀장이 프로젝트 등록 때 정한다.
 *
 * <ul>
 *   <li>skillId = 기술 번호 (DB skill.id)</li>
 *   <li>requiredLevel = 요구 레벨 r (1~4, 만들 때 검사)</li>
 *   <li>mandatory = 필수(true) / 우대(false)</li>
 * </ul>
 *
 * <p>가중치 숫자(필수 3, 우대 1)는 여기 없다. 팀장은 필수·우대만 고르고, 숫자는 서비스 규칙이라
 * application.yml 의 commitmate.scoring.skill-weight 에서 읽는다 ({@link ScoringWeights.SkillWeight}).
 */

public record SkillRequirement(long skillId, int requiredLevel, boolean mandatory) {
    public SkillRequirement {
        Levels.check(requiredLevel);
    }
}
