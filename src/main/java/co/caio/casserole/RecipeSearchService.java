package co.caio.casserole;

import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchResult;
import co.caio.cerberus.search.Searcher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class RecipeSearchService {

  private final Searcher searcher;

  public RecipeSearchService(Searcher searcher) {
    this.searcher = searcher;
  }

  public Mono<SearchResult> search(SearchQuery query) {
    return Mono.fromCallable(() -> searcher.search(query)).subscribeOn(Schedulers.parallel());
  }
}
