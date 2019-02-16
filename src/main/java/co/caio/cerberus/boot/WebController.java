package co.caio.cerberus.boot;

import co.caio.cerberus.boot.ModelView.OverPaginationError;
import co.caio.cerberus.boot.ModelView.RecipeNotFoundError;
import co.caio.cerberus.boot.SearchParameterParser.SearchParameterException;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.search.Searcher;
import com.fizzed.rocker.RockerModel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerOpenException;
import io.micrometer.core.annotation.Timed;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Controller
public class WebController {
  private static final Logger logger = LoggerFactory.getLogger(WebController.class);

  private final Searcher searcher;
  private final Duration timeout;
  private final SearchParameterParser parser;
  private final CircuitBreaker breaker;
  private final ModelView modelView;

  public WebController(
      Searcher searcher,
      @Qualifier("searchTimeout") Duration timeout,
      CircuitBreaker breaker,
      ModelView modelView,
      SearchParameterParser parameterParser) {
    this.searcher = searcher;
    this.timeout = timeout;
    this.parser = parameterParser;
    this.breaker = breaker;
    this.modelView = modelView;
  }

  @GetMapping("/")
  @ResponseBody
  public RockerModel index() {
    if (breaker.isCallPermitted()) {
      return modelView.renderIndex();
    } else {
      return modelView.renderUnstableIndex();
    }
  }

  @Timed
  @GetMapping("/recipe/{slug}/{recipeId}")
  @ResponseBody
  public RockerModel recipe(@PathVariable String slug, @PathVariable long recipeId) {
    return modelView.renderSingleRecipe(recipeId, slug);
  }

  @Timed
  @GetMapping("/search")
  @ResponseBody
  public Mono<RockerModel> search(
      @RequestParam Map<String, String> params, ServerHttpRequest request) {
    SearchQuery query = parser.buildQuery(params);

    return Mono.fromCallable(() -> breaker.executeCallable(() -> searcher.search(query)))
        // run the search in the parallel scheduler
        .subscribeOn(Schedulers.parallel())
        // and render in the elastic one
        .publishOn(Schedulers.elastic())
        .timeout(timeout)
        .map(
            result ->
                modelView.renderSearch(
                    query, result, UriComponentsBuilder.fromHttpRequest(request)));
  }

  @ExceptionHandler({
    IllegalStateException.class,
    ServerWebInputException.class,
    SearchParameterException.class
  })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  RockerModel handleBadParameters(Exception ex) {
    logger.debug("Handled bad parameter", ex);
    return modelView.renderError("Invalid/Unknown Parameter", ex.getMessage());
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  RockerModel handleOverPagination(OverPaginationError ex) {
    logger.debug("Handled over pagination", ex);
    return modelView.renderError("Invalid Page Number", ex.getMessage());
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.REQUEST_TIMEOUT)
  @ResponseBody
  RockerModel handleTimeout(TimeoutException ex) {
    logger.debug("Handled timeout", ex);
    return modelView.renderError(
        "Timeout Error", "We're likely overloaded, please try again in a few minutes");
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  @ResponseBody
  RockerModel handleCircuitBreaker(CircuitBreakerOpenException ex) {
    logger.debug("Handled open circuit", ex);
    return modelView.renderError(
        "Service Unavailable",
        "The site is experiencing an abnormal rate of errors, it might be a while before we're back at full speed");
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ResponseBody
  RockerModel handleRecipeNotFound(RecipeNotFoundError ex) {
    return modelView.renderError("Recipe Not Found", "Looks like this URL is invalid");
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ResponseBody
  RockerModel handleUnknown(Exception ex) {
    logger.error("Handled unknown error", ex);
    return modelView.renderError(
        "Unknown Error", "An unexpected error has occurred and has been logged, please try again");
  }
}
