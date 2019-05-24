package co.caio.casserole.service;

import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchResult;
import co.caio.cerberus.search.Searcher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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

    this.timer = registry.timer("search_service_search_timer");
    // Meters use weak references, so we hold the state here
    // to keep it from getting collected
    this.numRecipes = registry.gauge("search_service_num_recipes", searcher.numDocs());
  }

  public Mono<SearchResult> search(SearchQuery query) {
    return Mono.fromCallable(() -> timer.record(() -> searcher.search(query)))
        .subscribeOn(Schedulers.parallel());
  }
}
