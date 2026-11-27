package com.commitmate.backend.matching;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 0~100 점수를 정확한 분수(num / den)로 들고 다니는 값 객체.
 *
 * <p>num = 분자(numerator), den = 분모(denominator). 예: 기여도 12.5 = Score(25, 2)
 *
 * <ul>
 *   <li>계산·정렬·비교: 반올림하지 않은 정확한 분수로 한다 (compareTo, plus, times)</li>
 *   <li>화면 표시: display()에서 딱 한 번만 정수로 반올림(HALF_UP)한다. 버림 아님 (12.5 → 13)</li>
 *   <li>double을 쓰지 않는 이유: 1/3 같은 값이 오차로 12.5 → 12.4999가 되어 반올림이 틀어질 수 있음</li>
 *   <li>생성 시 최대공약수(g)로 약분해서 같은 값은 항상 같은 모양, 숫자가 커지는 것도 막는다 (50/4 → 25/2)</li>
 * </ul>
 *
 * <p>참고: docs/matching/scoring-rules.md 결정 1 (반올림)
 */
public record Score(long num, long den) implements Comparable<Score> {
    public static final Score ZERO = new Score(0, 1);
    public static final Score HUNDRED = new Score(100, 1);

    public Score {
        if (den <= 0) throw new IllegalArgumentException("den must be > 0");
        long g = gcd(Math.abs(num), den);       // g = 분자와 분모의 최대공약수(GCD, Greatest Common Divisor)
        if (g > 1) { num /= g; den /= g; }
    }
    public static Score of(long num, long den) { return new Score(num, den); }

    /** 화면 표시용 정수 (HALF_UP). */
    public int display() {
        return BigDecimal.valueOf(num).divide(BigDecimal.valueOf(den), 0, RoundingMode.HALF_UP).intValueExact();
    }
    public BigDecimal toBigDecimal(int scale) {
        return BigDecimal.valueOf(num).divide(BigDecimal.valueOf(den), scale, RoundingMode.HALF_UP);
    }
    Score plus(Score o) { return of(Math.addExact(Math.multiplyExact(num, o.den), Math.multiplyExact(o.num, den)), Math.multiplyExact(den, o.den)); }
    Score times(long n, long d) { return of(Math.multiplyExact(num, n), Math.multiplyExact(den, d)); }

    @Override public int compareTo(Score o) {
        return Long.compare(Math.multiplyExact(num, o.den), Math.multiplyExact(o.num, den));
    }
    private static long gcd(long a, long b) { return b == 0 ? Math.max(a, 1) : gcd(b, a % b); }
}
