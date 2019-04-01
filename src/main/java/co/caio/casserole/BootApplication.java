package co.caio.casserole;


import co.caio.cerberus.db.ChronicleRecipeMetadataDatabase;
import co.caio.cerberus.db.RecipeMetadataDatabase;
import co.caio.cerberus.search.Searcher;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.micrometer.CircuitBreakerMetrics;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import reactor.core.scheduler.Schedulers;

@SpringBootApplication
@EnableConfigurationProperties
public class BootApplication {

  public BootApplication() {}

  public static void main(String[] args) {
    Schedulers.enableMetrics();
    SpringApplication.run(BootApplication.class, args);
  }

  @Bean
  RecipeMetadataDatabase getMetadataDb(SearchConfigurationProperties conf) {
    return ChronicleRecipeMetadataDatabase.open(conf.chronicle.filename);
  }

  @Bean
  Searcher getSearcher(SearchConfigurationProperties conf) {
    return new Searcher.Builder()
        .dataDirectory(conf.lucene.directory)
        .searchPolicy(new NoMatchAllDocsSearchPolicy())
        .build();
  }

  @Bean
  Duration searchTimeout(SearchConfigurationProperties conf) {
    return conf.timeout;
  }

  @Bean("searchPageSize")
  int pageSize(SearchConfigurationProperties conf) {
    return conf.pageSize;
  }

  @Bean
  int numRecipes(Searcher searcher) {
    return searcher.numDocs();
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
