package co.caio.casserole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.caio.casserole.TermQueryRewritingPolicy.PolicyException;
import co.caio.cerberus.model.Recipe;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.search.Indexer;
import co.caio.cerberus.search.Searcher;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class TermQueryRewritingPolicyTest {

  private static final Searcher searcher;

  private static final Searcher noPolicySearcher;

  static {
    try {
      var dataDir = Files.createTempDirectory("policy-");

      var indexer = new Indexer.Builder().createMode().dataDirectory(dataDir).build();

      // Index with 4 recipes. Heavy hitters are "until" and "cup" when
      // maxTermFrequency is 3.
      indexer.addRecipe(withWords(0, "until", "cup", "peanut butter"));
      indexer.addRecipe(withWords(1, "until", "cup", "peanut butter"));
      indexer.addRecipe(withWords(2, "until", "cup", "low_frequency"));
      indexer.addRecipe(withWords(3, "until", "cup"));
      indexer.commit();

      noPolicySearcher = indexer.buildSearcher();
      searcher =
          new Searcher.Builder()
              .dataDirectory(dataDir)
              .searchPolicy(new TermQueryRewritingPolicy(3))
              .build();
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
  void mixedQueriesAreNotRewritten() {
    checkResultSameAsWithoutPolicy("until");
    checkResultSameAsWithoutPolicy("until \"peanut butter\"");
    checkResultSameAsWithoutPolicy("until -low_frequency");
    checkResultSameAsWithoutPolicy("until cup \"peanut butter\"");
  }

  void checkResultSameAsWithoutPolicy(String text) {
    var query = fulltext(text);
    assertEquals(noPolicySearcher.search(query), searcher.search(query));
  }

  SearchQuery fulltext(String text) {
    return new SearchQuery.Builder().fulltext(text).build();
  }

  @Test
  void singleWordPhraseQueryIsTreatedAsTerm() {
    assertEquals(
        searcher.search(fulltext("until cup")), searcher.search(fulltext("until \"cup\"")));
  }

  @Test
  void cannotExecuteConfusingHeavyQuery() {
    assertThrows(PolicyException.class, () -> searcher.search(fulltext("until -cup minut")));
  }

  @Test
  void multipleHeavyTermsBecomeMatchAllDocs() {
    assertEquals(searcher.search(fulltext("until cup")), searcher.search(fulltext("*")));
  }

  @Test
  void multipleNegatedHeavyTermsBecomeMatchNoDocs() {
    assertEquals(0, searcher.search(fulltext("-until -cup")).totalHits());
  }
}
