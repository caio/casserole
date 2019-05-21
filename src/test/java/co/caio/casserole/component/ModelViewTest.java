package co.caio.casserole.component;

import static org.junit.jupiter.api.Assertions.*;

import co.caio.casserole.component.ModelView.OverPaginationError;
import co.caio.casserole.service.MetadataService;
import co.caio.cerberus.Util;
import co.caio.cerberus.db.HashMapRecipeMetadataDatabase;
import co.caio.cerberus.db.RecipeMetadata;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchQuery.Builder;
import co.caio.cerberus.model.SearchQuery.RangedSpec;
import co.caio.cerberus.model.SearchResult;
import com.fizzed.rocker.RockerModel;
import com.fizzed.rocker.runtime.StringBuilderOutput;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

class ModelViewTest {

  private static final int pageSize = 2; // just to simplify pagination testing
  private static final ModelView modelView;
  private static final MetadataService METADATA_SERVICE;
  private static final CircuitBreaker breaker = CircuitBreaker.ofDefaults("mvt");

  private UriComponentsBuilder uriBuilder;

  static {
    var db = new HashMapRecipeMetadataDatabase();
    db.saveAll(
        Util.getSampleRecipes().map(RecipeMetadata::fromRecipe).collect(Collectors.toList()));
    METADATA_SERVICE = new MetadataService(db);
    modelView = new ModelView(pageSize, Util.expectedIndexSize(), breaker);
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
    assertEquals(errorTitle, doc.selectFirst("div.notification.is-danger p strong").text());
  }

  @Test
  void emptyResultsSearchPage() {
    var unusedQuery = new SearchQuery.Builder().fulltext("unused").build();
    var result = new SearchResult.Builder().build();

    var doc =
        parseOutput(modelView.renderSearch(unusedQuery, result, METADATA_SERVICE, uriBuilder));
    assertTrue(
        doc.title().startsWith(modelView.getSearchPageTitle(unusedQuery, result.totalHits())));
    assertTrue(
        doc.selectFirst("section#results div.notification.content")
            .text()
            .contains("Try changing your query"));
  }

  @Test
  void overPaginationShouldRenderError() {
    var largeOffsetQuery = new SearchQuery.Builder().fulltext("unused").offset(200).build();
    var result = new SearchResult.Builder().totalHits(180).build();
    assertThrows(
        OverPaginationError.class,
        () -> modelView.renderSearch(largeOffsetQuery, result, METADATA_SERVICE, uriBuilder));
  }

  @Test
  void singlePageResultShouldHaveNoPagination() {
    var unusedQuery = new SearchQuery.Builder().fulltext("unused").build();
    var result = new SearchResult.Builder().totalHits(1).addRecipe(1).build();

    var doc =
        parseOutput(modelView.renderSearch(unusedQuery, result, METADATA_SERVICE, uriBuilder));

    assertTrue(
        doc.title().startsWith(modelView.getSearchPageTitle(unusedQuery, result.totalHits())));

    assertTrue(doc.selectFirst("nav.pagination a.pagination-previous").attr("href").isEmpty());
    assertTrue(doc.selectFirst("nav.pagination a.pagination-next").attr("href").isEmpty());
  }

  @Test
  void firstPageShouldNotHavePreviousPagination() {
    var unusedQuery = new SearchQuery.Builder().fulltext("unused").build();
    var resultWithNextPage =
        new SearchResult.Builder().totalHits(3).addRecipe(1).addRecipe(2).build();

    var doc =
        parseOutput(
            modelView.renderSearch(unusedQuery, resultWithNextPage, METADATA_SERVICE, uriBuilder));

    assertTrue(
        doc.title()
            .startsWith(modelView.getSearchPageTitle(unusedQuery, resultWithNextPage.totalHits())));

    assertTrue(doc.selectFirst("nav.pagination a.pagination-previous").attr("href").isEmpty());
    assertTrue(doc.selectFirst("nav.pagination a.pagination-next").attr("href").contains("page=2"));
  }

  @Test
  void middlePageShouldHavePreviousAndNextPage() {
    var unusedQuery = new SearchQuery.Builder().fulltext("unused").offset(pageSize).build();
    var offsetResultWithNextPage =
        new SearchResult.Builder()
            .totalHits(5) // 2 (first page) + 2 (this result) + 1 (next page)
            .addRecipe(3)
            .addRecipe(4)
            .build();

    var doc =
        parseOutput(
            modelView.renderSearch(
                unusedQuery, offsetResultWithNextPage, METADATA_SERVICE, uriBuilder));

    assertTrue(
        doc.title()
            .startsWith(
                modelView.getSearchPageTitle(unusedQuery, offsetResultWithNextPage.totalHits())));

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
            .addRecipe(3)
            .addRecipe(4)
            .build();

    var doc =
        parseOutput(
            modelView.renderSearch(
                unusedQuery, offsetResultWithNextPage, METADATA_SERVICE, uriBuilder));

    assertTrue(
        doc.title()
            .startsWith(
                modelView.getSearchPageTitle(unusedQuery, offsetResultWithNextPage.totalHits())));

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
            .addRecipe(3)
            .addRecipe(4)
            .build();

    var doc =
        parseOutput(
            modelView.renderSearch(
                secondPage, offsetResultWithNextPage, METADATA_SERVICE, uriBuilder));

    var sidebarLinks = doc.select("div#sidebar ul.menu-list li a").eachAttr("href");
    assertTrue(sidebarLinks.size() > 0);
    assertTrue(sidebarLinks.stream().noneMatch(s -> s.contains("page=")));
  }

  @Test
  void regressionPaginationEndHasProperValue() {

    var secondPage = new SearchQuery.Builder().fulltext("unused").offset(pageSize).build();
    var offsetResultWithNextPage =
        new SearchResult.Builder()
            .totalHits(4) // 2 (first page) + 2 (this result)
            .addRecipe(3)
            .addRecipe(4)
            .build();

    var doc =
        parseOutput(
            modelView.renderSearch(
                secondPage, offsetResultWithNextPage, METADATA_SERVICE, uriBuilder));

    assertTrue(
        doc.title()
            .startsWith(
                modelView.getSearchPageTitle(secondPage, offsetResultWithNextPage.totalHits())));

    var subtitle = doc.selectFirst("section#results div.notification.content").text();
    assertTrue(subtitle.contains("from 3 to 4."));
  }

  @Test
  void renderSingleRecipe() {
    var model = Util.getSampleRecipes().limit(1).findFirst().orElseThrow();
    var recipe = METADATA_SERVICE.findById(model.recipeId());
    assertTrue(recipe.isPresent());
    var doc = parseOutput(modelView.renderSingleRecipe(recipe.get(), METADATA_SERVICE));
    assertTrue(doc.title().startsWith(model.name()));
  }

  @Test
  void retrieveSimilarRecipes() {
    Util.getSampleRecipes()
        .map(r -> METADATA_SERVICE.findById(r.recipeId()))
        .flatMap(Optional::stream)
        .forEach(
            metadata -> {
              var simIds = metadata.getSimilarRecipeIds();

              var similarRecipes = modelView.retrieveSimilarRecipes(simIds, METADATA_SERVICE);

              for (int i = 0; i < simIds.size(); i++) {
                var wanted = METADATA_SERVICE.findById(simIds.get(i)).orElseThrow();
                var sim = similarRecipes.get(i);
                assertEquals(wanted.getName(), sim.name());
              }
            });
  }

  @Test
  void regressionInfoUrisAreNotPoisonedByLogic() {
    var query = new SearchQuery.Builder().fulltext("unused").build();
    var builder =
        new SearchResult.Builder().totalHits(3); // pageSize is 2, so 3 means there's a next page
    Util.getSampleRecipes().limit(2).forEach(r -> builder.addRecipe(r.recipeId()));

    var doc =
        parseOutput(modelView.renderSearch(query, builder.build(), METADATA_SERVICE, uriBuilder));

    var found = doc.select("section#results article.media .media-right a");

    assertFalse(found.isEmpty());
    found.forEach(
        element -> {
          assertTrue(element.is("a"));
          var href = element.attr("href");
          assertFalse(href.isEmpty());
          assertFalse(href.contains("page="));
        });
  }

  @Test
  void getSearchPageTitle() {
    var range = RangedSpec.of(1, 10);

    checkTitle("Browsing 1 recipe. Page 1", fulltext(""), 1);
    checkTitle("Browsing 2 recipes. Page 1", fulltext(""), 2);
    checkTitle("Browsing 5 recipes. Page 3", fulltext("").offset(pageSize * 2), 5);
    checkTitle(
        "Browsing 12 recipes, with one filter applied. Page 1", fulltext("").calories(range), 12);
    checkTitle(
        "Browsing 42 recipes, with 2 filters applied. Page 1",
        fulltext("").calories(range).cookTime(range),
        42);

    checkTitle("1 Result for: bacon. Page 1", fulltext("bacon"), 1);
    checkTitle("34 Results for: bacon. Page 1", fulltext("bacon"), 34);
    checkTitle("5 Results for: bacon. Page 3", fulltext("bacon").offset(pageSize * 2), 5);
    checkTitle(
        "13 Results for: bacon, with one filter applied. Page 1",
        fulltext("bacon").calories(range),
        13);
    checkTitle(
        "0 Results for: bacon, with 2 filters applied. Page 1",
        fulltext("bacon").calories(range).cookTime(range),
        0);

    checkTitle("1 Result for: \"hard cheese\". Page 1", fulltext("\"hard cheese\""), 1);
    checkTitle("27 Results for: \"hard cheese\". Page 1", fulltext("\"hard cheese\""), 27);
    checkTitle(
        "5 Results for: \"hard cheese\". Page 3",
        fulltext("\"hard cheese\"").offset(pageSize * 2),
        5);
    checkTitle(
        "123 Results for: \"hard cheese\", with one filter applied. Page 1",
        fulltext("\"hard cheese\"").calories(range),
        123);
    checkTitle(
        "1 Result for: \"hard cheese\", with 3 filters applied. Page 1",
        fulltext("\"hard cheese\"").calories(range).cookTime(range).addMatchDiet("keto"),
        1);
  }

  private void checkTitle(String wanted, SearchQuery.Builder builder, long totalHits) {
    assertEquals(wanted, modelView.getSearchPageTitle(builder.build(), totalHits));
  }

  private SearchQuery.Builder fulltext(String query) {
    return new Builder().fulltext(query);
  }
}
