package com.commitmate.backend.matching;

import java.util.Map;

final class Levels {
    static final int LCM = 12; // lcm(1,2,3,4): L/r 를 정수로 만들기 위한 공통 분모
    private Levels() {}
    static void check(Integer level) {
        if (level == null || level < 1 || level > 4) throw new IllegalArgumentException("level must be 1~4: " + level);
    }
    static void checkAll(Map<Long, Integer> skills) { skills.values().forEach(Levels::check); }
}
