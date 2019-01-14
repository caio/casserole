package co.caio.cerberus.boot;

import static co.caio.cerberus.boot.SearchParameterParser.PAGE_SIZE;

import co.caio.cerberus.boot.SearchParameterParser.SearchParameterException;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchResult;
import co.caio.cerberus.model.SearchResultRecipe;
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
          "search_title", "Search for Recipes",
          "search_subtitle", "Over a million delicious recipes, zero ads",
          "search_placeholder", "Ingredients, diets, brands, etc.",
          "search_value", "",
          "search_text", "Search");

  @GetMapping("/")
  public Rendering index() {
    return Rendering.view("index").model(baseModel).build();
  }

  private List<Map<String, Object>> renderRecipes(List<SearchResultRecipe> recipes) {
    return recipes
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
  }

  private Rendering renderSearch(SearchQuery query, SearchResult result) {
    var model = new HashMap<String, Object>(baseModel);
    model.put("page_title", "Search Results");
    model.put("search_value", query.fulltext().orElse(""));
    model.put("search_text", "Search again");

    // FIXME test each result state
    if (result.totalHits() == 0) {
      return Rendering.view("zero_results").model(model).build();
    } else if (query.offset() >= result.totalHits()) {
      // over pagination
      return renderError(
          "Invalid Page Number", "No more results to show for this search", HttpStatus.BAD_REQUEST);
    } else {
      // normal results
      boolean isLastPage = query.offset() + PAGE_SIZE >= result.totalHits();
      int currentPage = (query.offset() / PAGE_SIZE) + 1;

      // FIXME proper pagination hrefs
      model.put("pagination_next_href", isLastPage ? null : "next_page");
      model.put("pagination_prev_href", currentPage == 1 ? null : "prev_page");

      var startIdx = query.offset() + 1;
      model.put("pagination_start", query.offset() + 1);
      model.put("pagination_end", result.recipes().size());
      model.put("pagination_max", result.totalHits());

      model.put("recipes", renderRecipes(result.recipes()));

      return Rendering.view("search").model(model).build();
    }
  }

  @Timed
  @GetMapping("/search")
  public Mono<Rendering> search(@RequestParam Map<String, String> params) {
    SearchQuery query = parser.buildQuery(params);

    return Mono.fromCallable(() -> searcher.search(query))
        .subscribeOn(Schedulers.parallel())
        .publishOn(Schedulers.elastic())
        .timeout(timeout)
        .transform(CircuitBreakerOperator.of(breaker))
        .map(result -> renderSearch(query, result));
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
