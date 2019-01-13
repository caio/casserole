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
          "search_title", "Search 950+k Recipes",
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

  @ExceptionHandler({
    IllegalStateException.class,
    ServerWebInputException.class,
    SearchParameterException.class
  })
  Rendering handleBadParameters(Exception ex) {
    // FIXME use a proper error view
    return Rendering.view("index")
        .model(baseModel)
        .modelAttribute("page_title", "Error: bad parameter")
        .status(HttpStatus.BAD_REQUEST)
        .build();
  }

  @ExceptionHandler
  Rendering handleTimeout(TimeoutException ex) {
    // FIXME use a proper error view
    return Rendering.view("index")
        .model(baseModel)
        .modelAttribute("page_title", "Timeout")
        .status(HttpStatus.REQUEST_TIMEOUT)
        .build();
  }

  @ExceptionHandler
  Rendering handleCircuitBreaker(CircuitBreakerOpenException ex) {
    // FIXME use a proper error view
    return Rendering.view("index")
        .model(baseModel)
        .modelAttribute("page_title", "Circuit Breaker")
        .status(HttpStatus.SERVICE_UNAVAILABLE)
        .build();
  }

  @ExceptionHandler
  Rendering handleUnknown(Exception ex) {
    // FIXME use a proper error view
    return Rendering.view("index")
        .model(baseModel)
        .modelAttribute("page_title", "Unknown Error")
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .build();
  }
}
