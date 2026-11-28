package com.commitmate.backend.matching;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.*;

import static com.commitmate.backend.matching.Fixtures.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("StudyMatch 계산 규칙 (S0-4 확정본)")
class ScoringRulesTest {

    final MatchingCalculator matching = new MatchingCalculator();
    final CoverageCalculator coverage = new CoverageCalculator();

    @Nested @DisplayName("① 반올림: 계산은 정확한 값, 표시만 정수 HALF_UP")
    class Rounding {
        @ParameterizedTest(name = "{0}/{1} → {2}")
        @CsvSource({
                "25, 2, 13",     // 12.5 → 13 (HALF_EVEN 이면 12 — 예시표 A 기여도)
                "75, 2, 38",     // 37.5 → 38
                "1249, 100, 12", // 12.49 → 12
                "100, 3, 33",    // 33.33…
                "200, 3, 67",    // 66.66…
                "0, 1, 0",
                "100, 1, 100"
        })
        void displayRoundsHalfUp(long num, long den, int expected) {
            assertThat(Score.of(num, den).display()).isEqualTo(expected);
        }

        @Test @DisplayName("정렬은 반올림 전 값으로: 12.5 < 12.6 (둘 다 표시는 13)")
        void compareUsesExactValue() {
            Score a = Score.of(125, 10), b = Score.of(126, 10);
            assertThat(a.display()).isEqualTo(b.display());
            assertThat(a).isLessThan(b);
        }
    }

    @Nested @DisplayName("② skillMatch = 100 × Σ w·min(1, L/r) / Σ w")
    class SkillMatch {
        @ParameterizedTest(name = "[{index}] {0} → {1} (표시 {2})")
        @CsvSource(delimiter = ';', value = {
                "JAVA:4 SPRING:4 DOCKER:1;          65; 65",   // 예시표 A
                "REACT:3 JAVA:1 DOCKER:2;           55; 55",   // 예시표 B
                "'';                                0;  0",    // 기술 없음 = 전부 레벨 0
                "JAVA:2 SPRING:2 REACT:2 DOCKER:2;  100; 100",
                "JAVA:4 SPRING:4 REACT:4 DOCKER:4;  100; 100", // 초과분 미반영 (상한 1)
                "JAVA:1 SPRING:1 REACT:1 DOCKER:1;  50; 50",   // 선형 부분 점수
                "DOCKER:2;                          10; 10",   // 우대만
                "JAVA:2 SPRING:2 REACT:2;           90; 90",   // 필수만 전부
                "JAVA:4 SPRING:4 REACT:4;           90; 90",
        })
        void exampleRequirements(String userSkills, String exact, int display) {
            Score s = matching.skillMatch(EXAMPLE_REQS, skills(userSkills));
            assertThat(s.toBigDecimal(4)).isEqualByComparingTo(new BigDecimal(exact));
            assertThat(s.display()).isEqualTo(display);
        }

        @Test @DisplayName("요구하지 않은 기술(예: Kotlin)은 점수에 영향 없음")
        void unrequestedSkillIgnored() {
            Map<Long, Integer> withExtra = new HashMap<>(APPLICANT_A);
            withExtra.put(99L, 4);
            assertThat(matching.skillMatch(EXAMPLE_REQS, withExtra))
                    .isEqualByComparingTo(matching.skillMatch(EXAMPLE_REQS, APPLICANT_A));
        }

        @ParameterizedTest(name = "요구 r=3, 레벨 {0} → 표시 {1}")
        @CsvSource({"1, 33", "2, 67", "3, 100", "4, 100"})
        void thirdsAreExact(int level, int display) {
            var reqs = List.of(new SkillRequirement(1, 3, true));
            assertThat(matching.skillMatch(reqs, Map.of(1L, level)).display()).isEqualTo(display);
        }
    }

    @Nested @DisplayName("③ 팀 커버리지: 기술마다 팀 최고 레벨 (합산 아님)")
    class Coverage {
        @ParameterizedTest(name = "[{index}] 팀 [{0}] → {1}")
        @CsvSource(delimiter = ';', value = {
                "JAVA:3 SPRING:3;                               60",  // 예시 현재 팀
                "JAVA:3 SPRING:3 | JAVA:4 SPRING:4 DOCKER:1;    65",  // + A
                "JAVA:3 SPRING:3 | REACT:3 JAVA:1 DOCKER:2;     100", // + B
                "JAVA:1 | JAVA:1 | JAVA:1;                      15",  // 레벨 1 세 명 ≠ 레벨 3 (합산 아님)
                "'';                                            0",   // 팀원 0명
        })
        void coverage(String team, String exact) {
            assertThat(coverage.coverage(EXAMPLE_REQS, team(team)).toBigDecimal(4))
                    .isEqualByComparingTo(new BigDecimal(exact));
        }
    }

    @Nested @DisplayName("④ 팀 기여도 = 100 × (합류 후 − 현재) / (100 − 현재)")
    class Contribution {
        @ParameterizedTest(name = "[{index}] 팀 [{0}] + [{1}] → {2} (표시 {3})")
        @CsvSource(delimiter = ';', value = {
                "JAVA:3 SPRING:3; JAVA:4 SPRING:4 DOCKER:1; 12.5; 13",  // 예시표 A
                "JAVA:3 SPRING:3; REACT:3 JAVA:1 DOCKER:2;  100;  100", // 예시표 B
                "JAVA:3 SPRING:3; '';                       0;    0",
                "JAVA:3 SPRING:3; JAVA:4 SPRING:4;          0;    0",   // 팀에 이미 있는 기술만
                "JAVA:3 SPRING:3; REACT:1;                  37.5; 38",
                "JAVA:3 SPRING:3; DOCKER:1;                 12.5; 13",
                "'';              REACT:3 JAVA:1 DOCKER:2;  55;   55",  // 현재 0이면 = 합류 후 커버리지
        })
        void contribution(String team, String applicant, String exact, int display) {
            Score c = coverage.contribution(EXAMPLE_REQS, team(team), skills(applicant)).orElseThrow();
            assertThat(c.toBigDecimal(4)).isEqualByComparingTo(new BigDecimal(exact));
            assertThat(c.display()).isEqualTo(display);
        }

        @Test @DisplayName("팀 커버리지 100이면 기여도 없음(empty)")
        void fullyCoveredTeamHasNoContribution() {
            var fullTeam = List.of(LEADER, APPLICANT_B);
            assertThat(coverage.contribution(EXAMPLE_REQS, fullTeam, APPLICANT_A)).isEmpty();
        }

        @Test @DisplayName("승인되면 남은 지원자 기여도가 바뀐다 (A: 13 → 기여도 없음)")
        void approvalChangesOthers() {
            assertThat(coverage.contribution(EXAMPLE_REQS, List.of(LEADER), APPLICANT_A)).isPresent();
            assertThat(coverage.contribution(EXAMPLE_REQS, List.of(LEADER, APPLICANT_B), APPLICANT_A)).isEmpty();
        }

        static Stream<Arguments> randomTeams() {
            Random rnd = new Random(20261126);
            return IntStream.range(0, 300).mapToObj(i -> {
                List<Map<Long, Integer>> team = IntStream.range(0, rnd.nextInt(4))
                        .mapToObj(j -> randomSkills(rnd)).collect(Collectors.toList());
                return Arguments.of(team, randomSkills(rnd));
            });
        }
        static Map<Long, Integer> randomSkills(Random rnd) {
            Map<Long, Integer> m = new HashMap<>();
            for (long id = 1; id <= 4; id++) if (rnd.nextBoolean()) m.put(id, 1 + rnd.nextInt(4));
            return m;
        }

        @ParameterizedTest(name = "불변식 #{index}")
        @MethodSource("randomTeams")
        @DisplayName("기여도는 항상 0~100 (음수 없음)")
        void contributionIsBetween0And100(List<Map<Long, Integer>> team, Map<Long, Integer> applicant) {
            coverage.contribution(EXAMPLE_REQS, team, applicant).ifPresent(c ->
                    assertThat(c).isBetween(Score.ZERO, Score.HUNDRED));
        }
    }

    @Nested @DisplayName("⑤ availability = 70 × min(1, h/H) + 30 × 시간대 겹침")
    class AvailabilityMatch {
        @ParameterizedTest(name = "[{index}] 프로젝트 {0}h {1} / 사용자 {2}h {3} → 표시 {4}")
        @CsvSource(delimiter = ';', nullValues = "null", value = {
                "10; WEEKEND;         6;    WEEKEND;                 72",
                "10; WEEKEND;         15;   WEEKEND|WEEKDAY_EVENING; 100", // 시간 상한 70
                "10; WEEKEND;         6;    WEEKDAY_DAY;             42",  // 시간대 불일치
                "10; WEEKEND;         0;    WEEKEND;                 30",
                "10; WEEKEND;         null; '';                      0",   // 미입력 = 0점
                "3;  WEEKDAY_EVENING; 1;    WEEKDAY_EVENING;         53",  // 70/3 + 30 = 53.33
        })
        void availability(int projHours, String projSlots, Integer userHours, String userSlots, int display) {
            Score s = matching.availabilityMatch(
                    new Availability(projHours, slots(projSlots)), new Availability(userHours, slots(userSlots)));
            assertThat(s.display()).isEqualTo(display);
        }
    }

    @Nested @DisplayName("⑥ matchScore = 0.6 skill + 0.2 interest + 0.2 availability")
    class MatchScore {
        @Test @DisplayName("A: skill 65, 분야 일치, 시간 충족 → 79")
        void applicantA() {
            Score s = matching.matchScore(matching.skillMatch(EXAMPLE_REQS, APPLICANT_A),
                    matching.interestMatch("WEB", Set.of("WEB", "AI")),
                    matching.availabilityMatch(new Availability(10, slots("WEEKEND")), new Availability(10, slots("WEEKEND"))));
            assertThat(s.toBigDecimal(4)).isEqualByComparingTo("79");
        }

        @Test @DisplayName("B: skill 55, 분야 불일치, availability 72 → 47.4 (표시 47)")
        void applicantB() {
            Score s = matching.matchScore(matching.skillMatch(EXAMPLE_REQS, APPLICANT_B),
                    matching.interestMatch("WEB", Set.of("GAME")),
                    matching.availabilityMatch(new Availability(10, slots("WEEKEND")), new Availability(6, slots("WEEKEND"))));
            assertThat(s.toBigDecimal(4)).isEqualByComparingTo("47.4");
            assertThat(s.display()).isEqualTo(47);
        }
    }

    @Nested @DisplayName("⑦ 정렬: 기여도 → 매칭 점수 → 지원일")
    class Ordering {
        final LocalDateTime t0 = LocalDateTime.of(2026, 12, 1, 9, 0);

        @Test void defaultOrder() {
            var list = new ArrayList<>(List.of(
                    new ApplicantOrder.Ranked(1, Optional.of(Score.of(125, 10)), Score.of(79, 1), t0),
                    new ApplicantOrder.Ranked(2, Optional.of(Score.of(126, 10)), Score.of(50, 1), t0.plusHours(1)), // 표시 13 동점이지만 실제 더 큼
                    new ApplicantOrder.Ranked(3, Optional.of(Score.of(125, 10)), Score.of(79, 1), t0.minusHours(1)), // 완전 동점 → 먼저 지원
                    new ApplicantOrder.Ranked(4, Optional.of(Score.HUNDRED), Score.of(47, 1), t0.plusDays(1))));
            list.sort(ApplicantOrder.DEFAULT);
            assertThat(list).extracting(ApplicantOrder.Ranked::applicationId).containsExactly(4L, 2L, 3L, 1L);
        }

        @Test @DisplayName("팀 커버리지 100(기여도 없음)이면 매칭 점수순")
        void fallbackToMatchScore() {
            var list = new ArrayList<>(List.of(
                    new ApplicantOrder.Ranked(1, Optional.empty(), Score.of(47, 1), t0),
                    new ApplicantOrder.Ranked(2, Optional.empty(), Score.of(79, 1), t0.plusHours(1))));
            list.sort(ApplicantOrder.DEFAULT);
            assertThat(list).extracting(ApplicantOrder.Ranked::applicationId).containsExactly(2L, 1L);
        }
    }

    @Nested @DisplayName("⑧ 잘못된 입력은 예외")
    class InvalidInput {
        @ParameterizedTest(name = "사용자 레벨 {0}")
        @ValueSource(ints = {0, 5, -1})
        void userLevelOutOfRange(int level) {
            assertThatIllegalArgumentException().isThrownBy(() -> matching.skillMatch(EXAMPLE_REQS, Map.of(1L, level)));
        }

        @ParameterizedTest(name = "요구 레벨 {0}")
        @ValueSource(ints = {0, 5})
        void requiredLevelOutOfRange(int level) {
            assertThatIllegalArgumentException().isThrownBy(() -> new SkillRequirement(1, level, true));
        }

        @Test void nullLevel() {
            Map<Long, Integer> m = new HashMap<>(); m.put(1L, null);
            assertThatIllegalArgumentException().isThrownBy(() -> matching.skillMatch(EXAMPLE_REQS, m));
        }

        @Test void noRequirements() {
            assertThatIllegalArgumentException().isThrownBy(() -> matching.skillMatch(List.of(), APPLICANT_A));
        }

        @Test @DisplayName("요구 기술 skillId 중복 (Java 필수 ×2 + React 필수)")
        void duplicateSkillId() {
            List<SkillRequirement> dup = List.of(
                    new SkillRequirement(1, 2, true), new SkillRequirement(1, 2, true), new SkillRequirement(3, 2, true));
            assertThatIllegalArgumentException().isThrownBy(() -> matching.skillMatch(dup, skills("REACT:2")));
            assertThatIllegalArgumentException().isThrownBy(() -> coverage.coverage(dup, team("REACT:2")));
        }

        @ParameterizedTest(name = "프로젝트 기대 시간 {0}")
        @NullSource @ValueSource(ints = {0, -3})
        void projectHoursInvalid(Integer hours) {
            assertThatIllegalArgumentException().isThrownBy(() -> matching.availabilityMatch(
                    new Availability(hours, slots("WEEKEND")), new Availability(5, slots("WEEKEND"))));
        }

        @Test void projectSlotsRequired() {
            assertThatIllegalArgumentException().isThrownBy(() -> matching.availabilityMatch(
                    new Availability(10, Set.of()), new Availability(5, slots("WEEKEND"))));
        }
    }
}
