package co.caio.cerberus.boot;

import co.caio.cerberus.boot.SearchParameterParser.SearchParameterException;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.search.Searcher;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerOpenException;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.annotation.Timed;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
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

  public WebController(
      Searcher searcher, @Qualifier("searchTimeout") Duration timeout, CircuitBreaker breaker) {
    this.searcher = searcher;
    this.timeout = timeout;
    this.parser = new SearchParameterParser();
    this.breaker = breaker;
  }

  private Map<String, String> baseModel =
      Map.of(
          "site_title", "gula.recipes",
          "page_description", "",
          "search_title", "Search Over a Million Recipes",
          "search_subtitle", "Find recipes, not ads",
          "search_placeholder", "Ingredients, diets, brands, etc.",
          "search_value", "",
          "search_text", "Search");

  @GetMapping("/")
  public Rendering index() {
    return Rendering.view("index").model(baseModel).build();
  }

  @Timed
  @GetMapping("/search")
  public Mono<Rendering> search(@RequestParam Map<String, String> params) {
    SearchQuery query = parser.buildQuery(params);

    return Mono.fromCallable(
            () -> {
              // System.out.println(Thread.currentThread().getName());
              var results = searcher.search(query);

              var model = new HashMap<String, Object>(baseModel);
              var startIdx = query.offset() + 1;
              model.put("page_title", "Search Results");
              model.put("pagination_start", startIdx);
              model.put("pagination_end", results.recipes().size() + startIdx);
              model.put("pagination_max", results.totalHits());
              // FIXME ;page=Number gets lost after building query
              // FIXME need original query for the next/prev urls too
              model.put("pagination_next_href", "");
              model.put("pagination_prev_href", startIdx == 1 ? null : "/search?");

              var recipes =
                  results
                      .recipes()
                      .stream()
                      .map(
                          srr -> {
                            List<Map<String, Object>> meta = List.of(); // FIXME
                            return Map.of(
                                "name",
                                srr.name(),
                                "href",
                                srr.crawlUrl(),
                                "site",
                                "nowhere.local", // FIXME
                                "description",
                                "", // FIXME
                                "meta",
                                meta);
                          })
                      .collect(Collectors.toList());
              model.put("recipes", recipes);

              return Rendering.view("search").model(model).build();
            })
        .publishOn(Schedulers.parallel())
        .timeout(timeout)
        .transform(CircuitBreakerOperator.of(breaker));
  }

  private Rendering renderError(String errorTitle, String errorSubtitle, HttpStatus status) {
    return Rendering.view("error")
        .model(baseModel)
        .modelAttribute("page_title", "An Error Has Occurred")
        .modelAttribute("error_title", errorTitle)
        .modelAttribute("error_subtitle", errorSubtitle)
        .status(status)
        .build();
  }

  // FIXME verify exception logging

  @ExceptionHandler({
    IllegalStateException.class,
    ServerWebInputException.class,
    SearchParameterException.class
  })
  Rendering handleBadParameters(Exception ex) {
    return renderError("Invalid/Unknown Parameter", ex.getMessage(), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler
  Rendering handleTimeout(TimeoutException ex) {
    return renderError(
        "Timeout Error",
        "We're likely overloaded, please try again in a few minutes",
        HttpStatus.REQUEST_TIMEOUT);
  }

  @ExceptionHandler
  Rendering handleCircuitBreaker(CircuitBreakerOpenException ex) {
    return renderError(
        "Service Unavailable",
        "The site is experiencing an abnormal rate of errors, it might be a while before we're back at full speed",
        HttpStatus.SERVICE_UNAVAILABLE);
  }

  @ExceptionHandler
  Rendering handleUnknown(Exception ex) {
    return renderError(
        "Unknown Error",
        "An unexpected error has occurred and has been logged, please try again",
        HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
