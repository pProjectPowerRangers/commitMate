package com.commitmate.backend.matching;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.LocalDateTime;
import java.util.*;

import static com.commitmate.backend.matching.Fixtures.*;
import static org.assertj.core.api.Assertions.*;

/**
 * C-1: 표시 최대 99 규칙, 가중치 yml 분리, MatchingService.
 * DB 없이 돈다 (Spring 전체를 띄우지 않고 매칭 설정 클래스만 올림).
 */
@DisplayName("C-1 계산 엔진: 표시 규칙·가중치 설정·서비스")
class MatchingEngineTest {

    @Nested @DisplayName("표시값: 정확한 값이 100 미만이면 최대 99")
    class DisplayCap {
        @ParameterizedTest(name = "{0}/{1} → {2}")
        @CsvSource({
                "9972, 100, 99",  // 99.72 → 반올림하면 100이지만 99
                "995, 10,   99",  // 99.5  → 99
                "199, 2,    99",  // 99.5 (분수 그대로)
                "9949, 100, 99",  // 99.49 → 원래도 99
                "100, 1,   100",  // 진짜 100만 100
                "985, 10,   99",  // 98.5 → 99 (원래 반올림)
                "125, 10,   13"   // 12.5 → 13 (기존 규칙 그대로)
        })
        void capsBelowHundred(long num, long den, int expected) {
            assertThat(Score.of(num, den).display()).isEqualTo(expected);
        }

        @Test @DisplayName("반올림하면 100이 되는 분수(3589/36 = 99.69…)도 화면에선 99")
        void exactBelowHundredNeverShows100() {
            Score s = Score.of(3589, 36);
            assertThat(s).isLessThan(Score.HUNDRED);
            assertThat(s.display()).isEqualTo(99);
        }
    }

    @Nested @DisplayName("가중치 바꿔 끼우기 (순수 자바, new 로 생성)")
    class CustomWeights {
        @Test @DisplayName("기본값은 확정 규칙과 같다: 예시표 A skillMatch 65, matchScore 79")
        void defaultsMatchConfirmedRules() {
            var m = new MatchingCalculator(ScoringWeights.DEFAULT);
            Score skill = m.skillMatch(EXAMPLE_REQS, APPLICANT_A);
            assertThat(skill).isEqualByComparingTo(Score.of(65, 1));
            assertThat(m.matchScore(skill, Score.HUNDRED, Score.HUNDRED).display()).isEqualTo(79);
        }

        @Test @DisplayName("필수=우대=1이면 예시 A skillMatch = (1+1+0+0.5)/4 = 62.5")
        void equalSkillWeights() {
            var w = new ScoringWeights(new ScoringWeights.SkillWeight(1, 1), null, null);
            assertThat(new MatchingCalculator(w).skillMatch(EXAMPLE_REQS, APPLICANT_A))
                    .isEqualByComparingTo(Score.of(125, 2));
        }

        @Test @DisplayName("비율 80/10/10이면 A = 0.8×65 + 10 + 10 = 72")
        void customMatchRatio() {
            var w = new ScoringWeights(null, new ScoringWeights.MatchRatio(80, 10, 10), null);
            assertThat(new MatchingCalculator(w).matchScore(Score.of(65, 1), Score.HUNDRED, Score.HUNDRED))
                    .isEqualByComparingTo(Score.of(72, 1));
        }

        @Test @DisplayName("시간 50/50이면 H 10h·주말, h 6h·주말 → 50×0.6 + 50 = 80")
        void customAvailabilityPoints() {
            var w = new ScoringWeights(null, null, new ScoringWeights.AvailabilityPoints(50, 50));
            var project = new Availability(10, slots("WEEKEND"));
            var user = new Availability(6, slots("WEEKEND"));
            assertThat(new MatchingCalculator(w).availabilityMatch(project, user)).isEqualByComparingTo(Score.of(80, 1));
        }

        @Test @DisplayName("커버리지도 같은 가중치를 쓴다: 필수=우대=1이면 팀장(Java3·Spring3) 커버리지 50")
        void coverageUsesSameWeights() {
            var w = new ScoringWeights(new ScoringWeights.SkillWeight(1, 1), null, null);
            assertThat(new CoverageCalculator(w).coverage(EXAMPLE_REQS, List.of(LEADER)))
                    .isEqualByComparingTo(Score.of(50, 1));
        }

        @Test @DisplayName("잘못된 값은 만들 때 바로 예외")
        void rejectsInvalidValues() {
            assertThatIllegalArgumentException().isThrownBy(() -> new ScoringWeights.MatchRatio(60, 20, 10)); // 합 90
            assertThatIllegalArgumentException().isThrownBy(() -> new ScoringWeights.AvailabilityPoints(80, 30));
            assertThatIllegalArgumentException().isThrownBy(() -> new ScoringWeights.SkillWeight(1, 3));  // 필수 < 우대
            assertThatIllegalArgumentException().isThrownBy(() -> new ScoringWeights.SkillWeight(3, 0));
        }
    }

    @Nested @DisplayName("Spring 설정 (application.yml → ScoringWeights → MatchingService)")
    class SpringWiring {
        final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(MatchingConfig.class, MatchingService.class);

        @Test @DisplayName("실제 application.yml 값이 확정 기본값과 같다")
        void applicationYmlEqualsDefaults() {
            runner.withInitializer(new ConfigDataApplicationContextInitializer())
                    .run(ctx -> assertThat(ctx.getBean(ScoringWeights.class)).isEqualTo(ScoringWeights.DEFAULT));
        }

        @Test @DisplayName("yml에 값이 없으면 @DefaultValue 기본값")
        void missingPropertiesFallBackToDefaults() {
            runner.run(ctx -> assertThat(ctx.getBean(ScoringWeights.class)).isEqualTo(ScoringWeights.DEFAULT));
        }

        @Test @DisplayName("프로퍼티로 바꾸면 서비스 계산에 반영된다")
        void overriddenPropertiesReachService() {
            runner.withPropertyValues("commitmate.scoring.skill-weight.mandatory=1",
                            "commitmate.scoring.skill-weight.preferred=1")
                    .run(ctx -> assertThat(ctx.getBean(MatchingService.class).coverage(EXAMPLE_REQS, List.of(LEADER)))
                            .isEqualByComparingTo(Score.of(50, 1)));
        }

        @Test @DisplayName("합이 100이 아니면 서버가 뜨지 않는다")
        void invalidPropertiesFailStartup() {
            runner.withPropertyValues("commitmate.scoring.match-ratio.skill=70")
                    .run(ctx -> assertThat(ctx).hasFailed()
                            .getFailure().hasRootCauseInstanceOf(IllegalArgumentException.class));
        }
    }

    @Nested @DisplayName("MatchingService: 기획서 예시표")
    class Service {
        final MatchingService service = new MatchingService(new MatchingCalculator(), new CoverageCalculator());
        final MatchingService.ProjectCriteria project =
                new MatchingService.ProjectCriteria(EXAMPLE_REQS, new Availability(10, slots("WEEKEND")), "WEB");

        @Test @DisplayName("A: skill 65, 분야 일치, 10h·주말 → 79")
        void applicantA() {
            var a = new MatchingService.Candidate(APPLICANT_A, new Availability(10, slots("WEEKEND")), Set.of("WEB"));
            var r = service.match(project, a);
            assertThat(r.skill().display()).isEqualTo(65);
            assertThat(r.interest()).isEqualByComparingTo(Score.HUNDRED);
            assertThat(r.availability()).isEqualByComparingTo(Score.HUNDRED);
            assertThat(r.total().display()).isEqualTo(79);
        }

        @Test @DisplayName("B: skill 55, 분야 불일치, 6h·주말(72) → 47.4 → 47")
        void applicantB() {
            var b = new MatchingService.Candidate(APPLICANT_B, new Availability(6, slots("WEEKEND")), Set.of("AI"));
            var r = service.match(project, b);
            assertThat(r.availability()).isEqualByComparingTo(Score.of(72, 1));
            assertThat(r.total()).isEqualByComparingTo(Score.of(474, 10));
            assertThat(r.total().display()).isEqualTo(47);
        }

        @Test @DisplayName("가능 시간·관심 분야 미입력이면 해당 파트 0점 (null 허용)")
        void missingProfileFields() {
            var c = new MatchingService.Candidate(APPLICANT_A, null, null);
            var r = service.match(project, c);
            assertThat(r.interest()).isEqualByComparingTo(Score.ZERO);
            assertThat(r.availability()).isEqualByComparingTo(Score.ZERO);
        }

        @Test @DisplayName("기여도: A 12.5 → 13, B 100, 팀이 이미 100이면 없음")
        void contribution() {
            List<Map<Long, Integer>> team = List.of(LEADER);
            assertThat(service.coverage(EXAMPLE_REQS, team).display()).isEqualTo(60);
            assertThat(service.contribution(EXAMPLE_REQS, team, APPLICANT_A)).get()
                    .satisfies(s -> assertThat(s.display()).isEqualTo(13));
            assertThat(service.contribution(EXAMPLE_REQS, team, APPLICANT_B)).get()
                    .isEqualTo(Score.HUNDRED);
            assertThat(service.contribution(EXAMPLE_REQS, List.of(LEADER, APPLICANT_B), APPLICANT_A)).isEmpty();
        }

        @Test @DisplayName("rank: 기여도 → 매칭 점수 → 지원일, 원본 목록은 그대로")
        void rank() {
            var t = LocalDateTime.of(2026, 12, 1, 9, 0);
            var low = new ApplicantOrder.Ranked(1, Optional.of(Score.of(125, 10)), Score.of(79, 1), t);
            var high = new ApplicantOrder.Ranked(2, Optional.of(Score.HUNDRED), Score.of(47, 1), t.plusHours(1));
            var input = List.of(low, high);
            assertThat(service.rank(input)).extracting(ApplicantOrder.Ranked::applicationId).containsExactly(2L, 1L);
            assertThat(input).containsExactly(low, high);
        }
    }
}
