package co.caio.cerberus.boot;

import co.caio.cerberus.boot.SearchParameterParser.SearchParameterException;
import co.caio.cerberus.search.SearchPolicy;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;

public class NoMatchAllDocsSearchPolicy implements SearchPolicy {

  @Override
  public void inspectParsedFulltextQuery(Query query) {
    if (query instanceof MatchAllDocsQuery) {
      throw new SearchParameterException("MatchAllDocs not allowed!");
    }
  }

  @Override
  public boolean shouldComputeFacets(TopDocs result) {
    // We're not exposing facets just yet
    return false;
  }
}
