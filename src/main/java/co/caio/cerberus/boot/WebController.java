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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
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

  public WebController(
      Searcher searcher, @Qualifier("searchTimeout") Duration timeout, CircuitBreaker breaker) {
    this.searcher = searcher;
    this.timeout = timeout;
    this.parser = new SearchParameterParser();
    this.breaker = breaker;
  }

  private Map<String, String> baseModel = Map.of(
      "site_title", "gula.recipes",

      "page_title", "something",
      "page_description", "",

      "search_title", "Search 950+k Recipes",
      "search_subtitle", "Find recipes, not ads",
      "search_placeholder", "Ingredients, diets, brands, etc.",
      "search_value", "",
      "search_text", "Search"
  );

  @GetMapping("/")
  public Rendering index() {
    return Rendering.view("index").model(baseModel).build();
  }

  @Timed
  @GetMapping("/search")
  @ResponseBody
  public Mono<String> search(@RequestParam Map<String, String> params) {
    SearchQuery query = parser.buildQuery(params);

    return Mono.fromCallable(
            () -> {
              // System.out.println(Thread.currentThread().getName());
              return views.search.template(searcher.search(query)).render().toString();
            })
        .publishOn(Schedulers.parallel())
        .timeout(timeout)
        .transform(CircuitBreakerOperator.of(breaker));
  }

  @ExceptionHandler({
    IllegalStateException.class,
    ServerWebInputException.class,
    SearchParameterException.class
  })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  ResponseEntity<String> handleBadParameters(Exception ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.TEXT_HTML)
        .body(views.index.template("Bad parameter: " + ex.getMessage()).render().toString());
  }

  @ExceptionHandler
  @ResponseBody
  ResponseEntity<String> handleTimeout(TimeoutException ex) {
    return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
        .contentType(MediaType.TEXT_HTML)
        .body(views.index.template("Timeout: " + ex.getMessage()).render().toString());
  }

  @ExceptionHandler
  @ResponseBody
  ResponseEntity<String> handleCircuitBreaker(CircuitBreakerOpenException ex) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .contentType(MediaType.TEXT_HTML)
        .body(views.index.template("Circuit Breaker: " + ex.getMessage()).render().toString());
  }

  @ExceptionHandler
  @ResponseBody
  ResponseEntity<String> handleUnknown(Exception ex) {
    var msg = ex.getMessage();
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.TEXT_HTML)
        .body(views.index.template(msg != null ? msg : ex.toString()).render().toString());
  }
}
