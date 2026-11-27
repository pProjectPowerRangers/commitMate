package com.commitmate.backend.matching;

import java.time.LocalDateTime;
import java.util.*;

public final class ApplicantOrder {
    private ApplicantOrder() {}

    public record Ranked(long applicationId, Optional<Score> contribution, Score matchScore, LocalDateTime appliedAt) {}

    /** 기본 정렬: 기여도 desc → 매칭 점수 desc → 지원일 asc. 기여도가 없으면(팀 100) 매칭 점수부터. */
    public static final Comparator<Ranked> DEFAULT =
            Comparator.<Ranked, Score>comparing(r -> r.contribution().orElse(Score.ZERO), Comparator.reverseOrder())
                    .thenComparing(Ranked::matchScore, Comparator.reverseOrder())
                    .thenComparing(Ranked::appliedAt);
}
