package com.commitmate.backend.matching;

import java.util.*;

/** 기획서 '계산 예시' 고정 데이터. 기술 ID: JAVA=1, SPRING=2, REACT=3, DOCKER=4 */
final class Fixtures {
    static final Map<String, Long> IDS = Map.of("JAVA", 1L, "SPRING", 2L, "REACT", 3L, "DOCKER", 4L);

    /** Java(필수 r2), Spring Boot(필수 r2), React(필수 r2), Docker(우대 r2) — 중요도 합 10 */
    static final List<SkillRequirement> EXAMPLE_REQS = List.of(
            new SkillRequirement(1, 2, true), new SkillRequirement(2, 2, true),
            new SkillRequirement(3, 2, true), new SkillRequirement(4, 2, false));

    static final Map<Long, Integer> LEADER = skills("JAVA:3 SPRING:3");
    static final Map<Long, Integer> APPLICANT_A = skills("JAVA:4 SPRING:4 DOCKER:1");
    static final Map<Long, Integer> APPLICANT_B = skills("REACT:3 JAVA:1 DOCKER:2");

    /** "JAVA:4 DOCKER:1" → {1=4, 4=1}. 빈 문자열/null → 빈 Map */
    static Map<Long, Integer> skills(String s) {
        Map<Long, Integer> m = new HashMap<>();
        if (s == null || s.isBlank()) return m;
        for (String tok : s.trim().split("\\s+")) {
            String[] kv = tok.split(":");
            m.put(IDS.get(kv[0]), Integer.parseInt(kv[1]));
        }
        return m;
    }

    /** "JAVA:3 SPRING:3 | JAVA:1" → 팀원 2명 */
    static List<Map<Long, Integer>> team(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split("\\|")).map(Fixtures::skills).toList();
    }

    static Set<TimeSlot> slots(String s) {
        if (s == null || s.isBlank()) return Set.of();
        EnumSet<TimeSlot> set = EnumSet.noneOf(TimeSlot.class);
        for (String t : s.split("\\|")) set.add(TimeSlot.valueOf(t.trim()));
        return set;
    }
    private Fixtures() {}
}
