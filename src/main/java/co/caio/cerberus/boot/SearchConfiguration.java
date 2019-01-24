package co.caio.cerberus.boot;

import co.caio.cerberus.db.RecipeMetadataDatabase;
import co.caio.cerberus.search.Searcher;
import com.samskivert.mustache.Mustache;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.micrometer.CircuitBreakerMetrics;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.mustache.MustacheEnvironmentCollector;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "cerberus.search")
public class SearchConfiguration {

  @NotNull @NotBlank private String location;
  @NotNull private Duration timeout;
  @NotNull @Positive private int pageSize;

  public void setLocation(String loc) {
    location = loc;
  }

  public void setTimeout(Duration duration) {
    timeout = duration;
  }

  public void setPageSize(int size) {
    pageSize = size;
  }

  // FIXME
  @Bean
  Mustache.Compiler getMustache(Mustache.TemplateLoader templateLoader, Environment env) {
    var collector =
        new MustacheEnvironmentCollector() {
          @Override
          public Iterator<?> toIterator(Object value) {
            if (value instanceof OptionalInt) {
              return ((OptionalInt) value).isPresent()
                  ? Collections.singleton(((OptionalInt) value).getAsInt()).iterator()
                  : Collections.emptyIterator();
            }
            return super.toIterator(value);
          }
        };
    collector.setEnvironment(env);
    return Mustache.compiler().withCollector(collector).withLoader(templateLoader);
  }

  @Bean
  @Qualifier("metadataDb")
  RecipeMetadataDatabase getMetadataDb() {
    return RecipeMetadataDatabase.Builder.open(Path.of("tmp/lmdb-fancy-test"), 3_000, true);
  }

  @Bean
  @Qualifier("searchPageSize")
  int getPageSize() {
    return pageSize;
  }

  @Bean
  Searcher getSearcher() {
    return new Searcher.Builder().dataDirectory(Path.of(location)).build();
  }

  @Bean("searchTimeout")
  Duration getTimeout() {
    return timeout;
  }

  @Bean("searchCircuitBreaker")
  CircuitBreaker getSearchCircuitBreaker() {
    return CircuitBreaker.ofDefaults("searchCircuitBreaker");
  }

  @Bean
  CircuitBreakerMetrics registerMetrics(CircuitBreaker breaker) {
    return CircuitBreakerMetrics.ofIterable(List.of(breaker));
  }

  @Component("circuitbreaker")
  static class CircuitBreakerHealthIndicator implements HealthIndicator {
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
      builder.withDetail(
          "openStateDurationSeconds", config.getWaitDurationInOpenState().toSeconds());

      return builder.build();
    }
  }
}
