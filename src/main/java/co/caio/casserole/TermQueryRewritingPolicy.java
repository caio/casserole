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
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

public class TermQueryRewritingPolicy implements SearchPolicy {

  private final int maxMatchingDocs;

  private static final Query DEFAULT_QUERY = new MatchAllDocsQuery();

  public TermQueryRewritingPolicy(int maxMatchingDocs) {
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
      return new MagicQuery((BooleanQuery) query, maxMatchingDocs);
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

  static class MagicQuery extends Query {
    private final int maxDocFrequency;
    private final BooleanQuery delegate;

    MagicQuery(BooleanQuery delegate, int maxDocFrequency) {
      this.maxDocFrequency = maxDocFrequency;
      this.delegate = delegate;
    }

    @Override
    public Query rewrite(IndexReader ir) throws IOException {

      int clausesAdded = 0;

      var simplified = delegate.rewrite(ir);

      if (!(simplified instanceof BooleanQuery)) {
        return simplified;
      }

      List<BooleanClause> tbqs = new ArrayList<>();

      for (var clause : ((BooleanQuery) simplified).clauses()) {
        var q1 = clause.getQuery();

        if (q1 instanceof TermQuery) {
          tbqs.add(clause);
        } else if (q1 instanceof BooleanQuery) {
          var bq = (BooleanQuery) q1;

          // The only time this happens currently is with negated terms/phrases
          // as they get expanded to [not(term) AND matchAllDocs()]
          // XXX maybe verify that get(1).getQuery() == MatchAllDocsQuery
          if (bq.clauses().size() == 2 && bq.clauses().get(0).getQuery() instanceof TermQuery) {
            tbqs.add(bq.clauses().get(0));
          } else {
            // XXX maybe throw here or at least log if get(0).getQuery() != PhraseQuery
            clausesAdded++;
          }
        } else {
          clausesAdded++;
        }
      }

      // More clauses other than TermQuery, nothing to worry about
      if (clausesAdded > 0 || tbqs.size() < 2) {
        return simplified;
      }

      List<BooleanClause> expensiveClauses = null;

      var stats = collectTermStates(ir, tbqs);

      for (int i = 0; i < stats.length; i++) {
        var clause = tbqs.get(i);
        if (stats[i] != null && stats[i].docFreq() > maxDocFrequency) {
          if (expensiveClauses == null) {
            expensiveClauses = new ArrayList<>();
          }
          expensiveClauses.add(clause);
        }
      }

      // One or no expensive clauses: nothing to worry about either
      if (expensiveClauses == null || expensiveClauses.size() < 2) {
        return simplified;
      }

      // XXX Haven't thought about what to do when we mix occurs, like:
      //     `until -minut`, so just throw instead :x
      // TODO Log
      if (expensiveClauses.stream().map(BooleanClause::getOccur).distinct().count() != 1) {
        throw new PolicyException("Refusing to execute confusing expensive query");
      }

      // XXX Maybe just always throw here?
      // TODO Log

      if (expensiveClauses.get(0).getOccur() == Occur.MUST_NOT) {
        return new MatchNoDocsQuery();
      } else {
        return DEFAULT_QUERY;
      }
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
      return delegate.equals(obj);
    }

    @Override
    public int hashCode() {
      return delegate.hashCode();
    }
  }
}
