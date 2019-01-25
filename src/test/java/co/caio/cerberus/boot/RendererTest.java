package co.caio.cerberus.boot;

import static org.junit.jupiter.api.Assertions.*;

import co.caio.cerberus.db.RecipeMetadataDatabase;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchResult;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

class RendererTest {

  private static final int pageSize = 2; // just to simplify pagination testing
  private static final Renderer renderer;
  private UriComponentsBuilder uriBuilder;

  static {
    try {
      var tmp = Files.createTempDirectory("renderer");
      var db = RecipeMetadataDatabase.Builder.open(tmp, 42, false);
      renderer = new Renderer(pageSize, db);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @BeforeEach
  void setupUriBuilder() {
    uriBuilder = UriComponentsBuilder.fromUriString("/renderer");
  }

  @Test
  void renderIndex() {
    var index = renderer.renderIndex();
    assertEquals("index", index.view());
  }

  @Test
  void renderError() {
    var error = renderer.renderError("title", "subtitle", HttpStatus.UNPROCESSABLE_ENTITY);
    assertEquals("error", error.view());
    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.status());
    assertEquals("title", error.modelAttributes().get("error_title"));
    assertEquals("subtitle", error.modelAttributes().get("error_subtitle"));
  }

  @Test
  void emptyResultsShouldRenderItsOwnView() {
    var unusedQuery = new SearchQuery.Builder().fulltext("unused").build();
    var result = new SearchResult.Builder().build();
    assertEquals("zero_results", renderer.renderSearch(unusedQuery, result, uriBuilder).view());
  }

  @Test
  void overPaginationShouldRenderError() {
    var largeOffsetQuery = new SearchQuery.Builder().fulltext("unused").offset(200).build();
    var result = new SearchResult.Builder().totalHits(180).build();
    assertEquals("error", renderer.renderSearch(largeOffsetQuery, result, uriBuilder).view());
  }

  @Test
  void singlePageResultShouldHaveNoPagination() {
    var unusedQuery = new SearchQuery.Builder().fulltext("unused").build();
    var result =
        new SearchResult.Builder().totalHits(1).addRecipe(1, "recipe 1", "doest matter").build();

    var r = renderer.renderSearch(unusedQuery, result, uriBuilder);

    assertEquals("search", r.view());
    assertNull(r.modelAttributes().get("pagination_next_href"));
    assertNull(r.modelAttributes().get("pagination_prev_href"));
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

    var r = renderer.renderSearch(unusedQuery, resultWithNextPage, uriBuilder);

    assertEquals("search", r.view());
    assertNull(r.modelAttributes().get("pagination_prev_href"));
    assertNotNull(r.modelAttributes().get("pagination_next_href"));
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

    var r = renderer.renderSearch(unusedQuery, offsetResultWithNextPage, uriBuilder);

    assertEquals("search", r.view());
    assertNotNull(r.modelAttributes().get("pagination_prev_href"));
    assertNotNull(r.modelAttributes().get("pagination_next_href"));
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

    var r = renderer.renderSearch(unusedQuery, offsetResultWithNextPage, uriBuilder);

    assertEquals("search", r.view());
    assertNotNull(r.modelAttributes().get("pagination_prev_href"));
    assertNull(r.modelAttributes().get("pagination_next_href"));
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

    var r = renderer.renderSearch(secondPage, offsetResultWithNextPage, uriBuilder);

    assertEquals("search", r.view());
    assertEquals(4, r.modelAttributes().get("pagination_end"));
  }

  @Test
  void paginationUrisAreRenderedCorrectly() {
    var unusedQuery = new SearchQuery.Builder().fulltext("unused").offset(pageSize).build();
    var offsetResultWithNextPage =
        new SearchResult.Builder()
            .totalHits(5) // 2 (first page) + 2 (this result) + 1 (next page)
            .addRecipe(3, "recipe 3", "doest matter")
            .addRecipe(4, "recipe 4", "doest matter")
            .build();

    var r = renderer.renderSearch(unusedQuery, offsetResultWithNextPage, uriBuilder);

    assertEquals("search", r.view());
    assertEquals(
        "/renderer?page=1#results", r.modelAttributes().get("pagination_prev_href").toString());
    assertEquals(
        "/renderer?page=3#results", r.modelAttributes().get("pagination_next_href").toString());
  }
}
