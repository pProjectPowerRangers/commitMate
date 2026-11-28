package com.commitmate.backend.matching;
import java.util.Set;
/**
 * 주당 시간(얼마나) + 시간대(언제) 묶음. 프로젝트와 사용자가 같은 타입을 쓴다.
 *
 * <ul>
 *   <li>weeklyHours = 일주일(주말 포함) 동안 이 프로젝트에 쓸 수 있는 총 시간.
 *       사용자는 가능 시간 h("주 6시간 가능"), 프로젝트는 1인당 기대 시간 H("주 10시간 필요", 1~40)</li>
 *   <li>slots = 그 시간을 낼 수 있는 시간대(WEEKDAY_DAY, WEEKDAY_EVENING, WEEKEND), 여러 개 선택 가능</li>
 * </ul>
 *
 * <p>점수: 70 × min(1, h/H) + 30 × (시간대 하나라도 겹침 ? 1 : 0)
 * <br>예: H 10h·{평일 저녁, 주말}, h 6h·{주말} → 70 × 0.6 + 30 = 72
 *
 * <p>미입력 처리
 * <ul>
 *   <li>weeklyHours null = 미입력. 사용자는 시간 파트 0점, 프로젝트는 계산기에서 예외</li>
 *   <li>slots null이면 빈 집합으로 바꾸고, 복사본이라 밖에서 원본을 바꿔도 영향 없음</li>
 * </ul>
 */
public record Availability(Integer weeklyHours, Set<TimeSlot> slots) {
    public Availability { slots = slots == null ? Set.of() : Set.copyOf(slots); }
}