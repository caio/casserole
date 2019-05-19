package co.caio.casserole;

import co.caio.casserole.config.SearchConfigurationProperties;
import co.caio.cerberus.db.RecipeMetadataDatabase;
import co.caio.cerberus.db.SimpleRecipeMetadataDatabase;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchResult;
import co.caio.cerberus.search.Searcher;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Duration;
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
    return new SimpleRecipeMetadataDatabase(conf.getSdb().getDirectory());
  }

  @Bean
  Searcher getSearcher(SearchConfigurationProperties conf) {
    return new Searcher.Builder()
        .dataDirectory(conf.getLucene().getDirectory())
        .searchPolicy(new TermQueryRewritingPolicy(200_000))
        .build();
  }

  @Bean
  Duration searchTimeout(SearchConfigurationProperties conf) {
    return conf.getTimeout();
  }

  @Bean("searchPageSize")
  int pageSize(SearchConfigurationProperties conf) {
    return conf.getPageSize();
  }

  @Bean
  int numRecipes(Searcher searcher) {
    return searcher.numDocs();
  }

  @Bean("searchCircuitBreaker")
  CircuitBreaker getSearchCircuitBreaker(MeterRegistry registry) {
    var cbRegistry = CircuitBreakerRegistry.ofDefaults();
    var breaker = cbRegistry.circuitBreaker("searchCircuitBreaker");

    TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(cbRegistry).bindTo(registry);

    return breaker;
  }

  @Bean
  Cache<SearchQuery, SearchResult> searchResultCache(
      SearchConfigurationProperties conf, MeterRegistry registry) {
    Cache<SearchQuery, SearchResult> cache =
        Caffeine.newBuilder()
            .maximumSize(conf.getCacheSize())
            .initialCapacity(conf.getCacheSize())
            .build();

    return CaffeineCacheMetrics.monitor(registry, cache, "search");
  }
}
