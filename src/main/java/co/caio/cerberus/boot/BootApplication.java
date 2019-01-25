package co.caio.cerberus.boot;

import co.caio.cerberus.db.RecipeMetadataDatabase;
import co.caio.cerberus.search.Searcher;
import com.samskivert.mustache.DefaultCollector;
import com.samskivert.mustache.Mustache;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.micrometer.CircuitBreakerMetrics;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(SearchConfigurationProperties.class)
public class BootApplication {

  private final SearchConfigurationProperties searchConfiguration;

  public BootApplication(SearchConfigurationProperties conf) {
    searchConfiguration = conf;
  }

  public static void main(String[] args) {
    SpringApplication.run(BootApplication.class, args);
  }

  @Bean
  Mustache.Compiler mustacheWithOptionalSupport(Mustache.TemplateLoader templateLoader) {
    var collector =
        new DefaultCollector() {
          @Override
          public Iterator<?> toIterator(Object value) {
            if (value instanceof OptionalInt) {
              var optIntValue = (OptionalInt) value;
              return optIntValue.isPresent()
                  ? Collections.singleton(optIntValue.getAsInt()).iterator()
                  : Collections.emptyIterator();
            }
            return super.toIterator(value);
          }
        };
    return Mustache.compiler().withCollector(collector).withLoader(templateLoader);
  }

  @Bean("metadataDb")
  RecipeMetadataDatabase getMetadataDb() {
    return RecipeMetadataDatabase.Builder.open(Path.of("tmp/lmdb-fancy-test"), 3_000, true);
  }

  @Bean("searchPageSize")
  int pageSize() {
    return searchConfiguration.getPageSize();
  }

  @Bean
  Searcher getSearcher() {
    return new Searcher.Builder().dataDirectory(Path.of(searchConfiguration.getLocation())).build();
  }

  @Bean("searchTimeout")
  Duration timeout() {
    return searchConfiguration.getTimeout();
  }

  @Bean("searchCircuitBreaker")
  CircuitBreaker getSearchCircuitBreaker() {
    return CircuitBreaker.ofDefaults("searchCircuitBreaker");
  }

  @Bean
  CircuitBreakerMetrics registerMetrics(CircuitBreaker breaker) {
    return CircuitBreakerMetrics.ofIterable(List.of(breaker));
  }
}
