package co.caio.casserole;

import co.caio.cerberus.search.SearchPolicy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TermQueryRewritingPolicy implements SearchPolicy {

  private final int maxMatchingDocs;

  static final Query DEFAULT_QUERY = new MatchAllDocsQuery();

  private static final Logger logger = LoggerFactory.getLogger(TermQueryRewritingPolicy.class);

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

  static class PolicyException extends RuntimeException {
    PolicyException(String message) {
      super(message);
    }
  }

  static class PerformanceInspectorQuery extends Query {
    final int maxDocFrequency;
    final BooleanQuery delegate;

    PerformanceInspectorQuery(BooleanQuery delegate, int maxDocFrequency) {
      this.maxDocFrequency = maxDocFrequency;
      this.delegate = delegate;
    }

    @Override
    public Query rewrite(IndexReader ir) throws IOException {
      var simplified = delegate.rewrite(ir);

      if (!(simplified instanceof BooleanQuery)) {
        return simplified;
      }

      List<BooleanClause> termClauses = new ArrayList<>();

      int nonTermQueries = 0;
      for (var clause : ((BooleanQuery) simplified).clauses()) {
        var q1 = clause.getQuery();

        if (q1 instanceof TermQuery) {
          termClauses.add(clause);
        } else if (q1 instanceof BooleanQuery) {
          var bq = (BooleanQuery) q1;

          // The only time this happens currently is with negated terms/phrases
          // as they get expanded to [not(term) AND matchAllDocs()]
          // XXX maybe verify that get(1).getQuery() == MatchAllDocsQuery
          if (bq.clauses().size() == 2 && bq.clauses().get(0).getQuery() instanceof TermQuery) {
            termClauses.add(bq.clauses().get(0));
          } else {
            // XXX maybe throw here or at least log if get(0).getQuery() != PhraseQuery
            nonTermQueries++;
          }
        } else {
          nonTermQueries++;
        }
      }

      // More clauses other than TermQuery and/or just one
      // TermQuery: cheap to execute
      if (nonTermQueries > 0 || termClauses.size() < 2) {
        return simplified;
      }

      int numExpensiveTermQueries = 0;
      var stats = collectTermStates(ir, termClauses);

      for (TermStates stat : stats) {
        if (stat != null && stat.docFreq() > maxDocFrequency) {
          numExpensiveTermQueries++;
        }
      }

      // More than one expensive term query, this may be slow
      if (numExpensiveTermQueries > 1) {
        logger.warn("Executing expensive lucene query:" + simplified);
      }

      return simplified;
    }

    private TermStates[] collectTermStates(IndexReader ir, List<BooleanClause> termClauses)
        throws IOException {
      var result = new TermStates[termClauses.size()];

      TermsEnum termsEnum;
      for (LeafReaderContext context : ir.leaves()) {

        for (int i = 0; i < termClauses.size(); i++) {

          var term = ((TermQuery) termClauses.get(i).getQuery()).getTerm();
          var termStates = result[i];

          final Terms terms = context.reader().terms(term.field());

          // field does not exist
          if (terms == null) {
            continue;
          }
          termsEnum = terms.iterator();
          assert termsEnum != null;

          if (termsEnum == TermsEnum.EMPTY) {
            continue;
          }

          if (termsEnum.seekExact(term.bytes())) {
            if (termStates == null) {
              result[i] =
                  new TermStates(
                      ir.getContext(),
                      termsEnum.termState(),
                      context.ord,
                      termsEnum.docFreq(),
                      termsEnum.totalTermFreq());
            } else {
              termStates.register(
                  termsEnum.termState(),
                  context.ord,
                  termsEnum.docFreq(),
                  termsEnum.totalTermFreq());
            }
          }
        }
      }

      return result;
    }

    @Override
    public String toString(String field) {
      return "<MagicQuery[" + delegate.toString(field) + "]>";
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof PerformanceInspectorQuery) {
        return ((PerformanceInspectorQuery) obj).maxDocFrequency == maxDocFrequency
            && delegate.equals(obj);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return delegate.hashCode();
    }
  }
}
