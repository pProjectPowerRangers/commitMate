package com.commitmate.backend.matching;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 순수 자바 계산기를 Spring 빈으로 등록한다.
 *
 * <p>계산기 클래스 자체에는 Spring 어노테이션을 붙이지 않는다 → new 로 만들어 JUnit만으로 테스트 가능 (C-1 완료 기준 "순수 자바 클래스").
 * 가중치는 application.yml 의 commitmate.scoring 에서 읽는다 ({@link ScoringWeights}).
 */
@Configuration
@EnableConfigurationProperties(ScoringWeights.class)
public class MatchingConfig {

    @Bean
    MatchingCalculator matchingCalculator(ScoringWeights weights) { return new MatchingCalculator(weights); }

    @Bean
    CoverageCalculator coverageCalculator(ScoringWeights weights) { return new CoverageCalculator(weights); }
}
