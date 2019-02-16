package co.caio.cerberus.boot;

import static org.junit.jupiter.api.Assertions.*;

import co.caio.cerberus.Util;
import co.caio.cerberus.boot.ModelView.OverPaginationError;
import co.caio.cerberus.boot.ModelView.RecipeNotFoundError;
import co.caio.cerberus.db.HashMapRecipeMetadataDatabase;
import co.caio.cerberus.db.RecipeMetadata;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchResult;
import com.fizzed.rocker.RockerModel;
import com.fizzed.rocker.runtime.StringBuilderOutput;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

class ModelViewTest {

  private static final int pageSize = 2; // just to simplify pagination testing
  private static final ModelView modelView;

  private UriComponentsBuilder uriBuilder;

  static {
    var db = new HashMapRecipeMetadataDatabase();
    db.saveAll(
        Util.getSampleRecipes().map(RecipeMetadata::fromRecipe).collect(Collectors.toList()));
    modelView = new ModelView(pageSize, db);
  }

  private Document parseOutput(RockerModel rockerModel) {
    var rendered = rockerModel.render(StringBuilderOutput.FACTORY).toString();
    return Jsoup.parse(rendered);
  }

  @BeforeEach
  void setupUriBuilder() {
    uriBuilder = UriComponentsBuilder.fromUriString("/renderer");
  }

  @Test
  void renderIndex() {
    var doc = parseOutput(modelView.renderIndex());
    assertTrue(doc.title().startsWith(ModelView.INDEX_PAGE_TITLE));
  }

  @Test
  void renderUnstableIndex() {
    var doc = parseOutput(modelView.renderUnstableIndex());
    assertTrue(doc.title().startsWith(ModelView.INDEX_PAGE_TITLE));

    // The warning is displayed
    assertNotNull(doc.selectFirst("div.hero-body div[class*='notification is-warning']"));
  }

  @Test
  void renderError() {
    var errorTitle = "Test Error Title";
    var errorSubtitle = "Error Subtitle";
    var doc = parseOutput(modelView.renderError(errorTitle, errorSubtitle));

    assertTrue(doc.title().startsWith(ModelView.ERROR_PAGE_TITLE));
    assertEquals(errorTitle, doc.selectFirst("section#error.section h1.title").text());
    assertEquals(errorSubtitle, doc.selectFirst("section#error.section h2.subtitle").text());
  }

  @Test
  void emptyResultsSearchPage() {
    var unusedQuery = new SearchQuery.Builder().fulltext("unused").build();
    var result = new SearchResult.Builder().build();

    var doc = parseOutput(modelView.renderSearch(unusedQuery, result, uriBuilder));
    assertTrue(doc.title().startsWith(ModelView.SEARCH_PAGE_TITLE));
    assertEquals(
        "Couldn't find any recipe matching your search :(",
        doc.selectFirst("section#results h2.subtitle").text());
  }

  @Test
  void overPaginationShouldRenderError() {
    var largeOffsetQuery = new SearchQuery.Builder().fulltext("unused").offset(200).build();
    var result = new SearchResult.Builder().totalHits(180).build();
    assertThrows(
        OverPaginationError.class,
        () -> modelView.renderSearch(largeOffsetQuery, result, uriBuilder));
  }

  @Test
  void singlePageResultShouldHaveNoPagination() {
    var unusedQuery = new SearchQuery.Builder().fulltext("unused").build();
    var result =
        new SearchResult.Builder().totalHits(1).addRecipe(1, "recipe 1", "doest matter").build();

    var doc = parseOutput(modelView.renderSearch(unusedQuery, result, uriBuilder));

    assertTrue(doc.title().startsWith(ModelView.SEARCH_PAGE_TITLE));

    assertNotNull(doc.selectFirst("nav.pagination a.pagination-previous[disabled]"));
    assertNotNull(doc.selectFirst("nav.pagination a.pagination-next[disabled]"));
  }

  @Test
  void firstPageShouldNotHavePreviousPagination() {
    var unusedQuery = new SearchQuery.Builder().fulltext("unused").build();
    var resultWithNextPage =
        new SearchResult.Builder()
            .totalHits(3)
            .addRecipe(1, "recipe 1", "doest matter")
            .addRecipe(2, "recipe 2", "doest matter")
            .build();

    var doc = parseOutput(modelView.renderSearch(unusedQuery, resultWithNextPage, uriBuilder));

    assertTrue(doc.title().startsWith(ModelView.SEARCH_PAGE_TITLE));

    assertNotNull(doc.selectFirst("nav.pagination a.pagination-previous[disabled]"));
    assertNotNull(doc.selectFirst("nav.pagination a.pagination-next[href$='page=2#results']"));
  }

  @Test
  void middlePageShouldHavePreviousAndNextPage() {
    var unusedQuery = new SearchQuery.Builder().fulltext("unused").offset(pageSize).build();
    var offsetResultWithNextPage =
        new SearchResult.Builder()
            .totalHits(5) // 2 (first page) + 2 (this result) + 1 (next page)
            .addRecipe(3, "recipe 3", "doest matter")
            .addRecipe(4, "recipe 4", "doest matter")
            .build();

    var doc =
        parseOutput(modelView.renderSearch(unusedQuery, offsetResultWithNextPage, uriBuilder));

    assertTrue(doc.title().startsWith(ModelView.SEARCH_PAGE_TITLE));

    assertNotNull(doc.selectFirst("nav.pagination a.pagination-previous[href$='page=1#results']"));
    assertNotNull(doc.selectFirst("nav.pagination a.pagination-next[href$='page=3#results']"));
  }

  @Test
  void lastPageShouldNotHaveNextPage() {

    var unusedQuery = new SearchQuery.Builder().fulltext("unused").offset(pageSize).build();
    var offsetResultWithNextPage =
        new SearchResult.Builder()
            .totalHits(4) // 2 (first page) + 2 (this result)
            .addRecipe(3, "recipe 3", "doest matter")
            .addRecipe(4, "recipe 4", "doest matter")
            .build();

    var doc =
        parseOutput(modelView.renderSearch(unusedQuery, offsetResultWithNextPage, uriBuilder));

    assertTrue(doc.title().startsWith(ModelView.SEARCH_PAGE_TITLE));

    assertNotNull(doc.selectFirst("nav.pagination a.pagination-previous[href$='page=1#results']"));
    assertNotNull(doc.selectFirst("nav.pagination a.pagination-next[disabled]"));
  }

  @Test
  void regressionPaginationEndHasProperValue() {

    var secondPage = new SearchQuery.Builder().fulltext("unused").offset(pageSize).build();
    var offsetResultWithNextPage =
        new SearchResult.Builder()
            .totalHits(4) // 2 (first page) + 2 (this result)
            .addRecipe(3, "recipe 3", "doest matter")
            .addRecipe(4, "recipe 4", "doest matter")
            .build();

    var doc = parseOutput(modelView.renderSearch(secondPage, offsetResultWithNextPage, uriBuilder));

    assertTrue(doc.title().startsWith(ModelView.SEARCH_PAGE_TITLE));

    var subtitle = doc.selectFirst("section#results div.notification.content").text();
    assertTrue(subtitle.contains("from 3 to 4."));
  }

  @Test
  void incorrectSlugYieldsNotFound() {
    var recipe = Util.getSampleRecipes().limit(1).findFirst().orElseThrow();
    assertThrows(
        RecipeNotFoundError.class,
        () -> modelView.renderSingleRecipe(recipe.recipeId(), "incorrect slug"));
  }

  @Test
  void incorrectIdYieldsNotFound() {
    var recipe = Util.getSampleRecipes().limit(1).findFirst().orElseThrow();
    assertThrows(RecipeNotFoundError.class, () -> modelView.renderSingleRecipe(213, recipe.slug()));
  }

  @Test
  void renderSingleRecipe() {
    var recipe = Util.getSampleRecipes().limit(1).findFirst().orElseThrow();
    var doc = parseOutput(modelView.renderSingleRecipe(recipe.recipeId(), recipe.slug()));
    assertTrue(doc.title().startsWith(recipe.name()));
  }
}
