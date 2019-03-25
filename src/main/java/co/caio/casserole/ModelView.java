package co.caio.casserole;

import co.caio.cerberus.db.RecipeMetadata;
import co.caio.cerberus.db.RecipeMetadataDatabase;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchResult;
import co.caio.tablier.model.ErrorInfo;
import co.caio.tablier.model.RecipeInfo;
import co.caio.tablier.model.SearchResultsInfo;
import co.caio.tablier.model.SiteInfo;
import co.caio.tablier.view.Error;
import co.caio.tablier.view.Index;
import co.caio.tablier.view.Recipe;
import co.caio.tablier.view.Search;
import com.fizzed.rocker.RockerModel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Component
class ModelView {

  static final String INDEX_PAGE_TITLE = "The Private, Ad-Free Recipe Search Experience";
  static final String SEARCH_PAGE_TITLE = "Search Results";
  static final String ERROR_PAGE_TITLE = "An Error Has Occurred";

  private static final SiteInfo DEFAULT_UNSTABLE_INDEX_SITE =
      new SiteInfo.Builder().title(INDEX_PAGE_TITLE).isUnstable(true).build();
  private static final SiteInfo DEFAULT_INDEX_SITE =
      new SiteInfo.Builder().title(INDEX_PAGE_TITLE).searchIsAutoFocus(true).build();
  private static final SiteInfo DEFAULT_ERROR_SITE =
      new SiteInfo.Builder().title(ERROR_PAGE_TITLE).searchIsAutoFocus(true).build();

  private static final String DEFAULT_UNKNOWN_ERROR_SUBTITLE = "Unknown Error Cause";

  private final int pageSize;
  private final int numRecipes;
  private final RecipeMetadataDatabase db;
  private final CircuitBreaker breaker;
  private final SidebarRenderer sidebarRenderer = new SidebarRenderer();

  ModelView(
      @Qualifier("searchPageSize") int pageSize,
      @Qualifier("numRecipes") int numRecipes,
      @Qualifier("metadataDb") RecipeMetadataDatabase db,
      CircuitBreaker breaker) {
    this.pageSize = pageSize;
    this.breaker = breaker;
    this.db = db;
    this.numRecipes = numRecipes;
  }

  RockerModel renderIndex() {
    if (breaker.isCallPermitted()) {
      return Index.template(DEFAULT_INDEX_SITE);
    } else {
      return Index.template(DEFAULT_UNSTABLE_INDEX_SITE);
    }
  }

  private static final String GO_SLUG_ID_PATH = "/go/{slug}/{recipeId}";

  RockerModel renderSearch(
      SearchQuery query, SearchResult result, UriComponentsBuilder uriBuilder) {

    var siteInfo =
        new SiteInfo.Builder()
            .title(SEARCH_PAGE_TITLE)
            .searchIsAutoFocus(false)
            .searchValue(query.fulltext().orElse(""))
            .build();

    if (query.offset() >= result.totalHits() && result.totalHits() > 0) {
      throw new OverPaginationError("No more results to show for this search");
    }

    boolean isLastPage = query.offset() + pageSize >= result.totalHits();
    int currentPage = (query.offset() / pageSize) + 1;

    var recipeGoUriComponents = uriBuilder.cloneBuilder().replacePath(GO_SLUG_ID_PATH).build();

    var searchBuilder =
        new SearchResultsInfo.Builder()
            .numRecipes(numRecipes)
            .paginationStart(query.offset() + 1)
            .paginationEnd(result.recipeIds().size() + query.offset())
            .numMatching(result.totalHits());

    if (!isLastPage) {
      searchBuilder.nextPageHref(
          uriBuilder.replaceQueryParam("page", currentPage + 1).build().toUriString());
    }

    if (currentPage != 1) {
      searchBuilder.previousPageHref(
          uriBuilder.replaceQueryParam("page", currentPage - 1).build().toUriString());
    }

    searchBuilder.recipes(renderRecipes(result.recipeIds(), recipeGoUriComponents));

    // Sidebar links always lead to the first page
    uriBuilder.replaceQueryParam("page");
    searchBuilder.sidebar(sidebarRenderer.render(query, result, uriBuilder));

    searchBuilder.numAppliedFilters(deriveAppliedFilters(query));

    return Search.template(siteInfo, searchBuilder.build());
  }

  static int deriveAppliedFilters(SearchQuery query) {
    // XXX This is very error prone as I'll need to keep in sync with
    //     the SearchQuery evolution manually. It could be computed
    //     during the build phase for better speed AND correctness,
    //     but right now it's too annoying to do it with immutables
    return (int)
            Stream.of(
                    query.numIngredients(),
                    query.totalTime(),
                    query.calories(),
                    query.fatContent(),
                    query.carbohydrateContent())
                .flatMap(Optional::stream)
                .count()
        + query.dietThreshold().size(); // First bite
  }

  private Iterable<RecipeInfo> renderRecipes(List<Long> recipeIds, UriComponents uriComponents) {
    return recipeIds
        .stream()
        .map(db::findById)
        .flatMap(Optional::stream)
        .map(r -> new RecipeMetadataRecipeInfoAdapter(r, uriComponents))
        .collect(Collectors.toList());
  }

  RockerModel renderError(String errorTitle, String errorSubtitle) {
    return Error.template(
        DEFAULT_ERROR_SITE,
        new ErrorInfo.Builder()
            .subtitle(errorSubtitle == null ? DEFAULT_UNKNOWN_ERROR_SUBTITLE : errorSubtitle)
            .title(errorTitle)
            .build());
  }

  // XXX Move to a better place maybe
  RecipeMetadata fetchRecipe(long recipeId, String slug) {
    var recipe = db.findById(recipeId).orElseThrow(RecipeNotFoundError::new);

    if (!slug.equals(recipe.getSlug())) {
      throw new RecipeNotFoundError();
    }

    return recipe;
  }

  RockerModel renderSingleRecipe(long recipeId, String slug, UriComponentsBuilder builder) {
    var recipe = fetchRecipe(recipeId, slug);

    return Recipe.template(
        new SiteInfo.Builder().title(recipe.getName()).searchIsAutoFocus(false).build(),
        new RecipeMetadataRecipeInfoAdapter(recipe, builder.replacePath(GO_SLUG_ID_PATH).build()));
  }

  static class RecipeMetadataRecipeInfoAdapter implements RecipeInfo {
    private final RecipeMetadata metadata;
    private final String goUrl;

    RecipeMetadataRecipeInfoAdapter(RecipeMetadata metadata, UriComponents uriComponents) {
      this.metadata = metadata;
      this.goUrl =
          uriComponents
              .expand(Map.of("slug", metadata.getSlug(), "recipeId", metadata.getRecipeId()))
              .toUriString();
    }

    @Override
    public String name() {
      return metadata.getName();
    }

    @Override
    public String siteName() {
      return metadata.getSiteName();
    }

    @Override
    public String goUrl() {
      return goUrl;
    }

    @Override
    public String crawlUrl() {
      return metadata.getCrawlUrl();
    }

    @Override
    public String infoUrl() {
      return goUrl.replace("/go/", "/recipe/");
    }

    @Override
    public int numIngredients() {
      return metadata.getNumIngredients();
    }

    @Override
    public OptionalInt calories() {
      return metadata.getCalories();
    }

    @Override
    public OptionalDouble fatContent() {
      return metadata.getFatContent();
    }

    @Override
    public OptionalDouble carbohydrateContent() {
      return metadata.getCarbohydrateContent();
    }

    @Override
    public OptionalDouble proteinContent() {
      return metadata.getProteinContent();
    }

    @Override
    public OptionalInt prepTime() {
      return metadata.getPrepTime();
    }

    @Override
    public OptionalInt cookTime() {
      return metadata.getCookTime();
    }

    @Override
    public OptionalInt totalTime() {
      return metadata.getTotalTime();
    }

    @Override
    public List<String> ingredients() {
      return metadata.getIngredients();
    }
  }

  static class OverPaginationError extends RuntimeException {
    OverPaginationError(String message) {
      super(message);
    }
  }

  static class RecipeNotFoundError extends RuntimeException {
    RecipeNotFoundError() {
      super();
    }
  }
}
