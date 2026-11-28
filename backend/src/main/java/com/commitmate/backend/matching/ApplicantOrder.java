package com.commitmate.backend.matching;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 지원자 정렬 규칙 (계산 규칙 결정 6).
 *
 * <p>DEFAULT: 기여도 높은 순 → 매칭 점수 높은 순 → 먼저 지원한 순
 * <ul>
 *   <li>팀 커버리지 100이면 기여도가 전부 empty → 0으로 취급해 동점 → 자연히 매칭 점수순</li>
 *   <li>반올림 전 정확한 값으로 비교 (12.5와 12.6은 둘 다 13으로 보여도 12.6이 위)</li>
 * </ul>
 * <p>TODO(C-4): sort=match, sort=date 용 MATCH, DATE Comparator 추가
 */

public final class ApplicantOrder {
    private ApplicantOrder() {}

    public record Ranked(long applicationId, Optional<Score> contribution, Score matchScore, LocalDateTime appliedAt) {}

    /** 기본 정렬: 기여도 desc → 매칭 점수 desc → 지원일 asc. 기여도가 없으면(팀 100) 매칭 점수부터. */
    public static final Comparator<Ranked> DEFAULT =
            Comparator.<Ranked, Score>comparing(r -> r.contribution().orElse(Score.ZERO), Comparator.reverseOrder())
                    .thenComparing(Ranked::matchScore, Comparator.reverseOrder())
                    .thenComparing(Ranked::appliedAt);
}