package co.caio.casserole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.caio.casserole.ext.PerformanceInspectorQuery;
import co.caio.cerberus.model.Recipe;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.search.CategoryExtractor;
import co.caio.cerberus.search.Indexer;
import co.caio.cerberus.search.Searcher;
import java.io.IOException;
import java.nio.file.Files;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.junit.jupiter.api.Test;

class TermQueryRewritingPolicyTest {

  private static final Searcher searcher;

  private static final Searcher noPolicySearcher;

  static {
    try {
      var dataDir = Files.createTempDirectory("policy-");

      var indexer = Indexer.Factory.open(dataDir, CategoryExtractor.NOOP);

      // Index with 4 recipes. Heavy hitters are "until" and "cup" when
      // maxTermFrequency is 3.
      indexer.addRecipe(withWords(0, "until", "cup", "peanut butter"));
      indexer.addRecipe(withWords(1, "until", "cup", "peanut butter"));
      indexer.addRecipe(withWords(2, "until", "cup", "low_frequency"));
      indexer.addRecipe(withWords(3, "until", "cup"));
      indexer.commit();

      noPolicySearcher = Searcher.Factory.open(dataDir);
      searcher = Searcher.Factory.open(dataDir, new TermQueryRewritingPolicy(3));
    } catch (IOException wrapped) {
      throw new RuntimeException(wrapped);
    }
  }

  static Recipe withWords(long id, String... args) {
    String name = "recipe#" + id;
    return new Recipe.Builder()
        .recipeId(id)
        .name(name)
        .siteName(name)
        .crawlUrl(name)
        .slug(name)
        .addInstructions("...")
        .addIngredients(args)
        .build();
  }

  @Test
  void validation() {
    assertThrows(IllegalStateException.class, () -> new TermQueryRewritingPolicy(0));
    assertThrows(IllegalStateException.class, () -> new TermQueryRewritingPolicy(-1));
  }

  @Test
  void expensiveQueriesAreNotRewritten() {
    checkResultSameAsWithoutPolicy("until cup minut");
    checkResultSameAsWithoutPolicy("until cup -minut");
    checkResultSameAsWithoutPolicy("until -cup minut");
    checkResultSameAsWithoutPolicy("-until cup minut");
  }

  void checkResultSameAsWithoutPolicy(String text) {
    var query = fulltext(text);
    assertEquals(noPolicySearcher.search(query), searcher.search(query));
  }

  SearchQuery fulltext(String text) {
    return new SearchQuery.Builder().fulltext(text).build();
  }

  @Test
  void emptyQueryIsHandledAsMatchAllDocs() {
    // A query with no fulltext() doesn't trigger the fulltext parsing
    // logic, so the policy is never called for a truly empty
    // query (i.e.: new SearchQuery.Builder().build();)
    assertEquals(searcher.numDocs(), searcher.search(fulltext("")).totalHits());
  }

  @Test
  void matchNoDocsIsRewrittenToDefault() {
    var policy = new TermQueryRewritingPolicy(42);
    assertEquals(
        TermQueryRewritingPolicy.DEFAULT_QUERY,
        policy.rewriteParsedFulltextQuery(new MatchNoDocsQuery()));
  }

  @Test
  void booleanQueryIsWrapped() {
    var policy = new TermQueryRewritingPolicy(42);
    var bq = new BooleanQuery.Builder().add(new MatchAllDocsQuery(), Occur.SHOULD).build();
    var rewritten = policy.rewriteParsedFulltextQuery(bq);

    assertTrue(rewritten instanceof PerformanceInspectorQuery);
    assertEquals(bq, ((PerformanceInspectorQuery) rewritten).getDelegate());
    assertEquals(42, ((PerformanceInspectorQuery) rewritten).getMaxDocFrequency());
  }

  @Test
  void otherQueriesAreUntouched() {
    checkQueryIsNotRewritten(new MatchAllDocsQuery());
    checkQueryIsNotRewritten(IntPoint.newExactQuery("FIELD", 2));
    checkQueryIsNotRewritten(FloatPoint.newRangeQuery("FIELD", 0, 420));
  }

  private void checkQueryIsNotRewritten(Query orig) {
    var policy = new TermQueryRewritingPolicy(42);
    assertEquals(orig, policy.rewriteParsedFulltextQuery(orig));
  }
}
