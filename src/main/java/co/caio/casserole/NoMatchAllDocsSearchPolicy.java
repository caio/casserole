package co.caio.casserole;

import co.caio.casserole.SearchParameterParser.SearchParameterException;
import co.caio.cerberus.search.SearchPolicy;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;

public class NoMatchAllDocsSearchPolicy implements SearchPolicy {

  @Override
  public void inspectParsedFulltextQuery(Query query) {
    if (query instanceof MatchAllDocsQuery) {
      throw new SearchParameterException("MatchAllDocs not allowed!");
    }
  }

  @Override
  public boolean shouldComputeFacets(int totalHits) {
    // Collecting facets is relatively expensive, so we only enable
    // it when we have a reasonably small number of results
    return totalHits < 50_000;
  }
}
