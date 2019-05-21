package co.caio.casserole.component;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import co.caio.casserole.service.MetadataService;
import co.caio.casserole.service.SearchService;
import co.caio.cerberus.db.RecipeMetadata;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchResult;
import com.fizzed.rocker.RockerModel;
import com.github.benmanes.caffeine.cache.Cache;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class RequestHandler {
  private final SearchParameterParser parser;
  private final ModelView modelView;
  private final MetadataService metadataService;
  private final SearchService searchService;
  private final CircuitBreaker breaker;
  private final Duration searchTimeout;
  private final Cache<SearchQuery, SearchResult> cache;

  public RequestHandler(
      SearchService searchService,
      Duration searchTimeout,
      CircuitBreaker breaker,
      ModelView modelView,
      MetadataService metadataService,
      SearchParameterParser parameterParser,
      Cache<SearchQuery, SearchResult> cache) {
    this.searchService = searchService;
    this.breaker = breaker;
    this.searchTimeout = searchTimeout;
    this.parser = parameterParser;
    this.modelView = modelView;
    this.metadataService = metadataService;
    this.cache = cache;
  }

  @Bean
  public RouterFunction<ServerResponse> router(RequestHandler handler) {
    return route()
        .GET("/search", handler::search)
        .GET("/", handler::index)
        .GET("/recipe/{slug}/{recipeId}", handler::recipe)
        .HEAD("/", handler::index)
        .HEAD("/search", handler::search)
        .HEAD("/recipe/{slug}/{recipeId}", handler::recipe)
        .build();
  }

  Mono<ServerResponse> index(ServerRequest ignored) {
    return ServerResponse.ok()
        .contentType(MediaType.TEXT_HTML)
        .body(BodyInserters.fromObject(modelView.renderIndex()));
  }

  Mono<SearchResult> fetchResult(SearchQuery query) {
    var cached = cache.getIfPresent(query);

    if (cached != null) {
      return Mono.just(cached);
    }

    return searchService
        .search(query)
        .timeout(searchTimeout)
        .doOnNext(result -> cache.put(query, result))
        .transform(CircuitBreakerOperator.of(breaker));
  }

  Mono<ServerResponse> search(ServerRequest request) {
    var query = parser.buildQuery(request.queryParams().toSingleValueMap());

    return ServerResponse.ok()
        .contentType(MediaType.TEXT_HTML)
        .body(
            fetchResult(query)
                .publishOn(Schedulers.elastic())
                .map(
                    result ->
                        modelView.renderSearch(
                            query,
                            result,
                            metadataService,
                            UriComponentsBuilder.fromUri(request.uri()))),
            RockerModel.class);
  }

  private RecipeMetadata fromRequest(ServerRequest request) {
    var slug = request.pathVariable("slug");
    var recipeId = Long.parseLong(request.pathVariable("recipeId"));

    var recipe = metadataService.findById(recipeId).orElseThrow(RecipeNotFoundError::new);

    if (!slug.equals(recipe.getSlug())) {
      throw new RecipeNotFoundError();
    }

    return recipe;
  }

  Mono<ServerResponse> recipe(ServerRequest request) {
    var recipe = fromRequest(request);
    return ServerResponse.ok()
        .contentType(MediaType.TEXT_HTML)
        .body(BodyInserters.fromObject(modelView.renderSingleRecipe(recipe, metadataService)));
  }

  static class RecipeNotFoundError extends RuntimeException {
    RecipeNotFoundError() {
      super();
    }
  }
}
