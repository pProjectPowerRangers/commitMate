package com.commitmate.backend.matching;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 0~100 점수를 정확한 분수(num/den)로 들고 있다가 표시할 때만 반올림한다. */
public record Score(long num, long den) implements Comparable<Score> {
    public static final Score ZERO = new Score(0, 1);
    public static final Score HUNDRED = new Score(100, 1);

    public Score {
        if (den <= 0) throw new IllegalArgumentException("den must be > 0");
        long g = gcd(Math.abs(num), den);
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
