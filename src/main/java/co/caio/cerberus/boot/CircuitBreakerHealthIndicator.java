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

    builder.withDetail("failureRate", metrics.getFailureRate());
    builder.withDetail("failureRateThreshold", config.getFailureRateThreshold());
    builder.withDetail("maxBufferedCalls", metrics.getMaxNumberOfBufferedCalls());
    builder.withDetail("bufferedCalls", metrics.getNumberOfBufferedCalls());
    builder.withDetail("failedCalls", metrics.getNumberOfFailedCalls());
    builder.withDetail("notPermittedCalls", metrics.getNumberOfNotPermittedCalls());
    builder.withDetail("openStateDurationSeconds", config.getWaitDurationInOpenState().toSeconds());

    return builder.build();
  }
}
