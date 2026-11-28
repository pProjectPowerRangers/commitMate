package com.commitmate.backend.matching;

/** 활동 가능 시간대. 시간대 점수는 겹치는 게 하나라도 있으면 30점 (겹친 개수는 안 셈). */

public enum TimeSlot { WEEKDAY_DAY, WEEKDAY_EVENING, WEEKEND }
