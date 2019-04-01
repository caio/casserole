package co.caio.casserole;

import co.caio.cerberus.db.RecipeMetadata;
import co.caio.cerberus.search.Searcher;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.URI;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class RequestHandler {
  private final Searcher searcher;
  private final Duration timeout;
  private final SearchParameterParser parser;
  private final CircuitBreaker breaker;
  private final ModelView modelView;
  private final RecipeMetadataService recipeMetadataService;

  public RequestHandler(
      Searcher searcher,
      Duration timeout,
      CircuitBreaker breaker,
      ModelView modelView,
      RecipeMetadataService recipeMetadataService,
      SearchParameterParser parameterParser) {
    this.searcher = searcher;
    this.timeout = timeout;
    this.parser = parameterParser;
    this.breaker = breaker;
    this.modelView = modelView;
    this.recipeMetadataService = recipeMetadataService;
  }

  Mono<ServerResponse> index(ServerRequest ignored) {
    return ServerResponse.ok()
        .contentType(MediaType.TEXT_HTML)
        .body(BodyInserters.fromObject(modelView.renderIndex()));
  }

  Mono<ServerResponse> search(ServerRequest request) {
    var query = parser.buildQuery(request.queryParams().toSingleValueMap());

    return Mono.fromCallable(() -> breaker.executeCallable(() -> searcher.search(query)))
        // run the search in the parallel scheduler
        .subscribeOn(Schedulers.parallel())
        // and render in the elastic one
        .publishOn(Schedulers.elastic())
        .timeout(timeout)
        .flatMap(
            result ->
                ServerResponse.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(
                        BodyInserters.fromObject(
                            modelView.renderSearch(
                                query,
                                result,
                                recipeMetadataService,
                                UriComponentsBuilder.fromUri(request.uri())))));
  }

  RecipeMetadata fromRequest(ServerRequest request) {
    var slug = request.pathVariable("slug");
    var recipeId = Long.parseLong(request.pathVariable("recipeId"));

    var recipe = recipeMetadataService.findById(recipeId).orElseThrow(RecipeNotFoundError::new);

    if (!slug.equals(recipe.getSlug())) {
      throw new RecipeNotFoundError();
    }

    return recipe;
  }

  Mono<ServerResponse> recipe(ServerRequest request) {
    var recipe = fromRequest(request);
    return ServerResponse.ok()
        .contentType(MediaType.TEXT_HTML)
        .body(
            BodyInserters.fromObject(
                modelView.renderSingleRecipe(recipe, UriComponentsBuilder.fromUri(request.uri()))));
  }

  Mono<ServerResponse> go(ServerRequest request) {
    var recipe = fromRequest(request);
    return ServerResponse.permanentRedirect(URI.create(recipe.getCrawlUrl())).build();
  }

  static class RecipeNotFoundError extends RuntimeException {
    RecipeNotFoundError() {
      super();
    }
  }
}
