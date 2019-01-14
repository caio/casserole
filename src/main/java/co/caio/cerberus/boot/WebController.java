package co.caio.cerberus.boot;

import co.caio.cerberus.boot.SearchParameterParser.SearchParameterException;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.search.Searcher;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerOpenException;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.annotation.Timed;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Controller
public class WebController {

  private final Searcher searcher;
  private final Duration timeout;
  private final SearchParameterParser parser;
  private final CircuitBreaker breaker;
  private final Renderer renderer;

  public WebController(
      Searcher searcher,
      @Qualifier("searchTimeout") Duration timeout,
      CircuitBreaker breaker,
      @Qualifier("searchPageSize") int pageSize) {
    this.searcher = searcher;
    this.timeout = timeout;
    this.parser = new SearchParameterParser(pageSize);
    this.breaker = breaker;
    this.renderer = new Renderer(pageSize);
  }

  @GetMapping("/")
  public Rendering index() {
    return renderer.renderIndex();
  }

  @Timed
  @GetMapping("/search")
  public Mono<Rendering> search(@RequestParam Map<String, String> params) {
    SearchQuery query = parser.buildQuery(params);

    return Mono.fromCallable(() -> searcher.search(query))
        // run the search in the parallel scheduler
        .subscribeOn(Schedulers.parallel())
        // and render in the elastic one
        .publishOn(Schedulers.elastic())
        .timeout(timeout)
        .transform(CircuitBreakerOperator.of(breaker))
        .map(result -> renderer.renderSearch(query, result));
  }

  // FIXME verify exception logging

  @ExceptionHandler({
    IllegalStateException.class,
    ServerWebInputException.class,
    SearchParameterException.class
  })
  Rendering handleBadParameters(Exception ex) {
    return renderer.renderError(
        "Invalid/Unknown Parameter", ex.getMessage(), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler
  Rendering handleTimeout(TimeoutException ex) {
    return renderer.renderError(
        "Timeout Error",
        "We're likely overloaded, please try again in a few minutes",
        HttpStatus.REQUEST_TIMEOUT);
  }

  @ExceptionHandler
  Rendering handleCircuitBreaker(CircuitBreakerOpenException ex) {
    return renderer.renderError(
        "Service Unavailable",
        "The site is experiencing an abnormal rate of errors, it might be a while before we're back at full speed",
        HttpStatus.SERVICE_UNAVAILABLE);
  }

  @ExceptionHandler
  Rendering handleUnknown(Exception ex) {
    return renderer.renderError(
        "Unknown Error",
        "An unexpected error has occurred and has been logged, please try again",
        HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
