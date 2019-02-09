package co.caio.cerberus.boot;

import co.caio.cerberus.db.ChronicleRecipeMetadataDatabase;
import co.caio.cerberus.db.RecipeMetadataDatabase;
import co.caio.cerberus.search.IndexConfiguration;
import co.caio.cerberus.search.Searcher;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.micrometer.CircuitBreakerMetrics;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties
public class BootApplication {

  private final SearchConfigurationProperties searchConfiguration;

  public BootApplication(SearchConfigurationProperties conf) {
    searchConfiguration = conf;
  }

  public static void main(String[] args) {
    SpringApplication.run(BootApplication.class, args);
  }

  @Bean("metadataDb")
  RecipeMetadataDatabase getMetadataDb() {
    return ChronicleRecipeMetadataDatabase.open(searchConfiguration.chronicle.filename);
  }

  @Bean("searchPageSize")
  int pageSize() {
    return searchConfiguration.pageSize;
  }

  @Bean
  Searcher getSearcher() {
    return new Searcher.Builder()
        .analyzer(IndexConfiguration.DEFAULT_ANALYZER)
        .dataDirectory(searchConfiguration.lucene.directory)
        .build();
  }

  @Bean("searchTimeout")
  Duration timeout() {
    return searchConfiguration.timeout;
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
