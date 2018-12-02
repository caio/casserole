package co.caio.cerberus.boot;

import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchQuery.SortOrder;
import co.caio.cerberus.model.SearchResult;
import co.caio.cerberus.search.Searcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

  public SearchController(Searcher injectedSearcher) {
    searcher = injectedSearcher;
  }

  private final Searcher searcher;

  @GetMapping("search")
  public SearchResult search(
      @RequestParam("q") String fulltext,
      @RequestParam(value = "n", defaultValue = "10") int maxResults,
      @RequestParam(value = "sort", required = false) SortOrder order)
      throws Exception {
    var builder = new SearchQuery.Builder();

    if (fulltext != null) builder.fulltext(fulltext);
    if (order != null) builder.sort(order);

    builder.maxResults(maxResults);

    return searcher.search(builder.build());
  }
}
