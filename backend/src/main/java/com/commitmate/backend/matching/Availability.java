package com.commitmate.backend.matching;
import java.util.Set;
/** weeklyHours 는 null(미입력) 허용, slots 는 빈 집합(미입력) 허용. */
public record Availability(Integer weeklyHours, Set<TimeSlot> slots) {
    public Availability { slots = slots == null ? Set.of() : Set.copyOf(slots); }
}
