package co.caio.casserole.service;

import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchResult;
import co.caio.cerberus.search.Searcher;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class SearchService {

  private final Searcher searcher;
  private final Cache<SearchQuery, SearchResult> cache;

  public SearchService(Searcher searcher, Cache<SearchQuery, SearchResult> cache) {
    this.searcher = searcher;
    this.cache = cache;
  }

  public Mono<SearchResult> search(SearchQuery query) {
    return Mono.fromCallable(() -> cache.get(query, searcher::search))
        .subscribeOn(Schedulers.parallel());
  }
}
