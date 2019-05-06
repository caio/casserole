package co.caio.casserole;

import co.caio.casserole.ext.PerformanceInspectorQuery;
import co.caio.cerberus.search.SearchPolicy;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;

public class TermQueryRewritingPolicy implements SearchPolicy {

  private final int maxMatchingDocs;

  static final Query DEFAULT_QUERY = new MatchAllDocsQuery();

  TermQueryRewritingPolicy(int maxMatchingDocs) {
    if (maxMatchingDocs < 1) {
      throw new IllegalStateException("maxMatchingDocs must be > 0");
    }
    this.maxMatchingDocs = maxMatchingDocs;
  }

  @Override
  public Query rewriteParsedFulltextQuery(Query query) {
    if (query instanceof MatchNoDocsQuery) {
      return DEFAULT_QUERY;
    } else if (query instanceof BooleanQuery) {
      return new PerformanceInspectorQuery((BooleanQuery) query, maxMatchingDocs);
    } else {
      return query;
    }
  }

  @Override
  public Query rewriteParsedSimilarityQuery(Query query) {
    // FIXME tune when allowing arbitrary similarity queries
    return query;
  }

  @Override
  public boolean shouldComputeFacets(int totalHits) {
    // Collecting facets is relatively expensive, so we only enable
    // it when we have a reasonably small number of results
    return totalHits < 50_000;
  }

  public static class PolicyException extends RuntimeException {
    PolicyException(String message) {
      super(message);
    }
  }
}
