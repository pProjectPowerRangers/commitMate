package com.commitmate.backend.matching;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 계산 엔진의 Spring 입구. 컨트롤러·다른 서비스는 계산기를 직접 만들지 말고 이 서비스를 주입받아 쓴다.
 *
 * <ul>
 *   <li>계산 자체는 {@link MatchingCalculator}, {@link CoverageCalculator} (순수 자바)가 한다. 여기는 묶어서 넘겨주기만</li>
 *   <li>입력은 엔티티가 아니라 아래 record들이다. 엔티티 → record 변환은 C-4(지원자 목록 API)에서 붙인다
 *       → B의 프로젝트 엔티티가 바뀌어도 계산 엔진은 안 바뀐다</li>
 *   <li>DB 접근 없음, 상태 없음 → 트랜잭션 불필요</li>
 * </ul>
 */
@Service
public class MatchingService {

    /**
     * 공고 쪽 입력.
     *
     * @param requirements 요구 기술 (1개 이상)
     * @param availability 1인당 기대 시간 H + 시간대 (1개 이상)
     * @param field        프로젝트 분야. TODO(ProjectField): 값 목록이 팀에서 정해지면 enum으로 바꾼다 (이슈 #3)
     */
    public record ProjectCriteria(List<SkillRequirement> requirements, Availability availability, String field) {
        public ProjectCriteria {
            requirements = List.copyOf(requirements);
            Objects.requireNonNull(availability, "availability");
        }
    }

    /**
     * 지원자(사용자) 쪽 입력.
     *
     * @param skills       기술 id → 레벨 1~4 (미보유 기술은 넣지 않음)
     * @param availability 주당 가능 시간 h + 시간대. 미입력이면 weeklyHours null
     * @param interests    관심 분야. TODO(ProjectField): enum 전환
     */
    public record Candidate(Map<Long, Integer> skills, Availability availability, Set<String> interests) {
        public Candidate {
            skills = Map.copyOf(skills);
            availability = availability == null ? new Availability(null, Set.of()) : availability;
            interests = interests == null ? Set.of() : Set.copyOf(interests);
        }
    }

    /** 매칭 점수와 세 부분 점수. 카드에는 total.display(), 상세 화면에는 세 부분을 보여줄 수 있다. */
    public record MatchBreakdown(Score skill, Score interest, Score availability, Score total) {}

    private final MatchingCalculator matching;
    private final CoverageCalculator coverage;

    public MatchingService(MatchingCalculator matching, CoverageCalculator coverage) {
        this.matching = matching;
        this.coverage = coverage;
    }

    /** matchScore = 0.6 skill + 0.2 interest + 0.2 availability (비율은 yml) */
    public MatchBreakdown match(ProjectCriteria project, Candidate user) {
        Score skill = matching.skillMatch(project.requirements(), user.skills());
        Score interest = matching.interestMatch(project.field(), user.interests());
        Score availability = matching.availabilityMatch(project.availability(), user.availability());
        return new MatchBreakdown(skill, interest, availability, matching.matchScore(skill, interest, availability));
    }

    /** 팀 커버리지. members = 승인된 팀원(팀장 포함)의 기술 맵 */
    public Score coverage(List<SkillRequirement> requirements, List<Map<Long, Integer>> members) {
        return coverage.coverage(requirements, members);
    }

    /** 기여도. 팀 커버리지가 이미 100이면 empty → 화면 "요구 기술을 모두 충족한 팀" */
    public Optional<Score> contribution(List<SkillRequirement> requirements, List<Map<Long, Integer>> members,
                                        Map<Long, Integer> applicantSkills) {
        return coverage.contribution(requirements, members, applicantSkills);
    }

    /** 기본 정렬(기여도 → 매칭 점수 → 지원일)로 정렬한 새 목록. 원본은 바꾸지 않는다. */
    public List<ApplicantOrder.Ranked> rank(Collection<ApplicantOrder.Ranked> applicants) {
        return applicants.stream().sorted(ApplicantOrder.DEFAULT).toList();
    }
}
