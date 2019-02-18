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
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

class ModelViewTest {

  private static final int pageSize = 2; // just to simplify pagination testing
  private static final ModelView modelView;
  private static final CircuitBreaker breaker = CircuitBreaker.ofDefaults("mvt");

  private UriComponentsBuilder uriBuilder;

  static {
    var db = new HashMapRecipeMetadataDatabase();
    db.saveAll(
        Util.getSampleRecipes().map(RecipeMetadata::fromRecipe).collect(Collectors.toList()));
    modelView = new ModelView(pageSize, db, breaker);
  }

  private Document parseOutput(RockerModel rockerModel) {
    var rendered = rockerModel.render(StringBuilderOutput.FACTORY).toString();
    return Jsoup.parse(rendered);
  }

  @BeforeEach
  void setup() {
    uriBuilder = UriComponentsBuilder.fromUriString("/renderer");
    breaker.reset();
  }

  @Test
  void renderIndex() {
    var doc = parseOutput(modelView.renderIndex());
    assertTrue(doc.title().startsWith(ModelView.INDEX_PAGE_TITLE));
  }

  @Test
  void renderUnstableIndex() {
    breaker.transitionToOpenState();
    var doc = parseOutput(modelView.renderIndex());
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
    assertEquals("Try changing your query", doc.selectFirst("section#results h2.subtitle").text());
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

    assertTrue(doc.selectFirst("nav.pagination a.pagination-previous").attr("href").isEmpty());
    assertTrue(doc.selectFirst("nav.pagination a.pagination-next").attr("href").contains("page=2"));
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

    assertTrue(
        doc.selectFirst("nav.pagination a.pagination-previous").attr("href").contains("page=1"));
    assertTrue(doc.selectFirst("nav.pagination a.pagination-next").attr("href").contains("page=3"));
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

    assertTrue(
        doc.selectFirst("nav.pagination a.pagination-previous").attr("href").contains("page=1"));
    assertTrue(doc.selectFirst("nav.pagination a.pagination-next").attr("href").isEmpty());
  }

  @Test
  void sidebarLinksDropThePageParameter() {

    var secondPage = new SearchQuery.Builder().fulltext("unused").offset(pageSize).build();
    var offsetResultWithNextPage =
        new SearchResult.Builder()
            .totalHits(4) // 2 (first page) + 2 (this result)
            .addRecipe(3, "recipe 3", "doest matter")
            .addRecipe(4, "recipe 4", "doest matter")
            .build();

    var doc = parseOutput(modelView.renderSearch(secondPage, offsetResultWithNextPage, uriBuilder));

    var sidebarLinks = doc.select("div#sidebar div.filter-group li a.button").eachAttr("href");
    assertTrue(sidebarLinks.size() > 0);
    assertTrue(sidebarLinks.stream().noneMatch(s -> s.contains("page=")));
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
        () ->
            modelView.renderSingleRecipe(
                recipe.recipeId(), "incorrect slug", UriComponentsBuilder.newInstance()));
  }

  @Test
  void incorrectIdYieldsNotFound() {
    var recipe = Util.getSampleRecipes().limit(1).findFirst().orElseThrow();
    assertThrows(
        RecipeNotFoundError.class,
        () -> modelView.renderSingleRecipe(213, recipe.slug(), UriComponentsBuilder.newInstance()));
  }

  @Test
  void renderSingleRecipe() {
    var recipe = Util.getSampleRecipes().limit(1).findFirst().orElseThrow();
    var doc =
        parseOutput(
            modelView.renderSingleRecipe(
                recipe.recipeId(), recipe.slug(), UriComponentsBuilder.newInstance()));
    assertTrue(doc.title().startsWith(recipe.name()));
  }
}
