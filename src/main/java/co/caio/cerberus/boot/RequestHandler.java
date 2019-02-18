package co.caio.cerberus.boot;

import co.caio.cerberus.search.Searcher;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
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

  public RequestHandler(
      Searcher searcher,
      Duration timeout,
      CircuitBreaker breaker,
      ModelView modelView,
      SearchParameterParser parameterParser) {
    this.searcher = searcher;
    this.timeout = timeout;
    this.parser = parameterParser;
    this.breaker = breaker;
    this.modelView = modelView;
  }

  public Mono<ServerResponse> index(ServerRequest ignored) {
    return ServerResponse.ok()
        .contentType(MediaType.TEXT_HTML)
        .body(BodyInserters.fromObject(modelView.renderIndex()));
  }

  public Mono<ServerResponse> search(ServerRequest request) {
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
                                query, result, UriComponentsBuilder.fromUri(request.uri())))));
  }

  public Mono<ServerResponse> recipe(ServerRequest request) {
    var slug = request.pathVariable("slug");
    var recipeId = Long.parseLong(request.pathVariable("recipeId"));
    return ServerResponse.ok()
        .contentType(MediaType.TEXT_HTML)
        .body(BodyInserters.fromObject(modelView.renderSingleRecipe(recipeId, slug)));
  }
}
