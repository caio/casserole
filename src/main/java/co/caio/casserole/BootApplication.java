package co.caio.casserole;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import co.caio.cerberus.db.ChronicleRecipeMetadataDatabase;
import co.caio.cerberus.db.RecipeMetadataDatabase;
import co.caio.cerberus.search.Searcher;
import co.caio.cerberus.search.Searcher.Builder;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.micrometer.CircuitBreakerMetrics;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.scheduler.Schedulers;

@SpringBootApplication
@EnableConfigurationProperties
public class BootApplication {

  private final SearchConfigurationProperties searchConfiguration;

  public BootApplication(SearchConfigurationProperties conf) {
    searchConfiguration = conf;
  }

  public static void main(String[] args) {
    Schedulers.enableMetrics();
    SpringApplication.run(BootApplication.class, args);
  }

  @Bean
  public RouterFunction<ServerResponse> router(RequestHandler handler) {
    return route()
        .GET("/search", handler::search)
        .GET("/", handler::index)
        .GET("/recipe/{slug}/{recipeId}", handler::recipe)
        .GET("/go/{slug}/{recipeId}", handler::go)
        .HEAD("/", handler::headIndex)
        .HEAD("/search", handler::headSearch)
        .HEAD("/recipe/{slug}/{recipeId}", handler::headRecipe)
        .build();
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
    return new Builder()
        .dataDirectory(searchConfiguration.lucene.directory)
        .searchPolicy(new NoMatchAllDocsSearchPolicy())
        .build();
  }

  @Bean
  int numRecipes(Searcher searcher) {
    return searcher.numDocs();
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
