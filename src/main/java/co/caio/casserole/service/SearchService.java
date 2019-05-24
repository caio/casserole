package co.caio.casserole.service;

import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchResult;
import co.caio.cerberus.search.Searcher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class SearchService {

  private final Searcher searcher;
  private final Integer numRecipes;
  private final Timer timer;

  public SearchService(Searcher searcher, MeterRegistry registry) {
    this.searcher = searcher;

    this.timer =
        Timer.builder("search_service_search_timer")
            .publishPercentiles(0.5, 0.95, 0.999)
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(1))
            .maximumExpectedValue(Duration.ofSeconds(2))
            .register(registry);

    // Meters use weak references, so we hold the state here
    // to keep it from getting collected
    // XXX Make this an AtomicInteger if we ever want to change it during runtime
    this.numRecipes = registry.gauge("search_service_num_recipes", searcher.numDocs());
  }

  public Mono<SearchResult> search(SearchQuery query) {
    return Mono.fromCallable(() -> timer.record(() -> searcher.search(query)))
        .subscribeOn(Schedulers.parallel());
  }
}
