package co.caio.cerberus.boot;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("circuitbreaker")
class CircuitBreakerHealthIndicator implements HealthIndicator {
  private final CircuitBreaker breaker;

  CircuitBreakerHealthIndicator(CircuitBreaker breaker) {
    this.breaker = breaker;
  }

  @Override
  public Health health() {
    switch (breaker.getState()) {
      case CLOSED:
        return withMetadata(Health.up());
      case OPEN:
        return withMetadata(Health.down());
      default:
        return withMetadata(Health.unknown());
    }
  }

  private Health withMetadata(Health.Builder builder) {
    var config = breaker.getCircuitBreakerConfig();
    var metrics = breaker.getMetrics();

    return builder
        .withDetail("failureRate", metrics.getFailureRate())
        .withDetail("failureRateThreshold", config.getFailureRateThreshold())
        .withDetail("maxBufferedCalls", metrics.getMaxNumberOfBufferedCalls())
        .withDetail("bufferedCalls", metrics.getNumberOfBufferedCalls())
        .withDetail("failedCalls", metrics.getNumberOfFailedCalls())
        .withDetail("notPermittedCalls", metrics.getNumberOfNotPermittedCalls())
        .withDetail("openStateDurationSeconds", config.getWaitDurationInOpenState().toSeconds())
        .build();
  }
}
