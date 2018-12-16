package co.caio.cerberus.boot;

import static org.junit.jupiter.api.Assertions.*;

import co.caio.cerberus.boot.SearchConfiguration.CircuitBreakerHealthIndicator;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class SearchConfigurationTest {

  @Test
  void circuitBreakerHealthEndpoint() {
    var breaker = CircuitBreaker.ofDefaults("name");
    var indicator = new CircuitBreakerHealthIndicator(breaker);

    // Default status is closed -> healthy
    assertEquals(Status.UP, indicator.health().getStatus());

    breaker.transitionToOpenState();
    assertEquals(Status.DOWN, indicator.health().getStatus());

    breaker.transitionToDisabledState();
    assertEquals(Status.UNKNOWN, indicator.health().getStatus());
    breaker.transitionToForcedOpenState();
    assertEquals(Status.UNKNOWN, indicator.health().getStatus());
    breaker.transitionToHalfOpenState();
    assertEquals(Status.UNKNOWN, indicator.health().getStatus());

    breaker.transitionToClosedState();
    assertEquals(Status.UP, indicator.health().getStatus());
  }
}
