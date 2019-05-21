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

  // This is a bit awkward in order to avoid occupying a parallel
  // worker (subscribeOn) when a search is already cached
  public Mono<SearchResult> search(SearchQuery query) {

    SearchResult cachedResult = cache.getIfPresent(query);
    if (cachedResult != null) {
      return Mono.just(cachedResult);
    }

    return Mono.fromCallable(
            () -> {
              var result = searcher.search(query);
              cache.put(query, result);
              return result;
            })
        .subscribeOn(Schedulers.parallel());
  }
}
