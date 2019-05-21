package co.caio.casserole.component;

import co.caio.casserole.service.MetadataService;
import co.caio.cerberus.db.RecipeMetadata;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchResult;
import co.caio.tablier.model.ErrorInfo;
import co.caio.tablier.model.RecipeInfo;
import co.caio.tablier.model.RecipeInfo.SimilarInfo;
import co.caio.tablier.model.SearchResultsInfo;
import co.caio.tablier.model.SiteInfo;
import co.caio.tablier.view.Error;
import co.caio.tablier.view.Index;
import co.caio.tablier.view.Recipe;
import co.caio.tablier.view.Search;
import com.fizzed.rocker.RockerModel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
class ModelView {

  static final String INDEX_PAGE_TITLE = "The Private, Ad-Free Recipe Search Experience";
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
  private final CircuitBreaker breaker;
  private final SidebarRenderer sidebarRenderer;

  ModelView(
      @Qualifier("searchPageSize") int pageSize,
      @Qualifier("numRecipes") int numRecipes,
      CircuitBreaker breaker) {
    this.pageSize = pageSize;
    this.breaker = breaker;
    this.numRecipes = numRecipes;
    this.sidebarRenderer = new SidebarRenderer();
  }

  RockerModel renderIndex() {
    if (breaker.isCallPermitted()) {
      return Index.template(DEFAULT_INDEX_SITE);
    } else {
      return Index.template(DEFAULT_UNSTABLE_INDEX_SITE);
    }
  }

  String getSearchPageTitle(SearchQuery query, long totalHits) {
    var sb = new StringBuilder();

    var fulltext = query.fulltext().orElse("");
    if (fulltext.isBlank()) {
      sb.append("Browsing ");
      sb.append(totalHits);
      sb.append(totalHits == 1 ? " recipe" : " recipes");
    } else {
      sb.append(totalHits);
      sb.append(totalHits == 1 ? " Result for: " : " Results for: ");
      sb.append(fulltext);
    }

    var numFilters = query.numSelectedFilters();
    if (numFilters == 1) {
      sb.append(", with one filter applied");
    } else if (numFilters > 1) {
      sb.append(", with ");
      sb.append(numFilters);
      sb.append(" filters applied");
    }

    sb.append(". Page ");
    sb.append((query.offset() / pageSize) + 1);

    return sb.toString();
  }

  RockerModel renderSearch(
      SearchQuery query, SearchResult result, MetadataService db, UriComponentsBuilder uriBuilder) {

    if (query.offset() >= result.totalHits() && result.totalHits() > 0) {
      throw new OverPaginationError("No more results to show for this search");
    }

    var searchBuilder =
        new SearchResultsInfo.Builder()
            .numRecipes(numRecipes)
            .paginationStart(query.offset() + 1)
            .paginationEnd(result.recipeIds().size() + query.offset())
            .numMatching(result.totalHits());

    searchBuilder.recipes(renderRecipes(result.recipeIds(), db));

    boolean isLastPage = query.offset() + pageSize >= result.totalHits();
    int currentPage = (query.offset() / pageSize) + 1;

    // NOTE that the following modifies the uriBuilder in place
    if (!isLastPage) {
      searchBuilder.nextPageHref(
          uriBuilder.replaceQueryParam("page", currentPage + 1).build().toUriString());
    }

    if (currentPage != 1) {
      searchBuilder.previousPageHref(
          uriBuilder.replaceQueryParam("page", currentPage - 1).build().toUriString());
    }

    searchBuilder.sidebar(
        sidebarRenderer.render(
            query,
            result,
            // Sidebar links always lead to the first page
            uriBuilder.replaceQueryParam("page")));

    int numFilters = (int) query.numSelectedFilters();
    if (numFilters > 0) {
      searchBuilder.numAppliedFilters(numFilters);
      searchBuilder.clearFiltersUrl(
          uriBuilder
              .cloneBuilder()
              .replaceQuery(null)
              .replaceQueryParam("q", query.fulltext().orElse(""))
              .build()
              .toUriString());
    }

    var extraParams =
        uriBuilder
            .replaceQueryParam("q")
            .replaceQueryParam("page")
            .build()
            .getQueryParams()
            .toSingleValueMap();

    var siteInfo =
        new SiteInfo.Builder()
            .title(getSearchPageTitle(query, result.totalHits()))
            .extraSearchParams(extraParams)
            .searchIsAutoFocus(false)
            .searchValue(query.fulltext().orElse(""))
            .build();

    return Search.template(siteInfo, searchBuilder.build());
  }

  private Iterable<RecipeInfo> renderRecipes(List<Long> recipeIds, MetadataService db) {
    return recipeIds
        .stream()
        .map(db::findById)
        .flatMap(Optional::stream)
        .map(r -> buildAdapter(r, db))
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

  RockerModel renderSingleRecipe(RecipeMetadata recipe, MetadataService db) {
    return Recipe.template(
        new SiteInfo.Builder().title(recipe.getName()).searchIsAutoFocus(false).build(),
        buildAdapter(recipe, db));
  }

  List<SimilarInfo> retrieveSimilarRecipes(List<Long> ids, MetadataService db) {
    return ids.stream()
        .map(db::findById)
        .flatMap(Optional::stream)
        .map(r -> new RecipeMetadataSimilarInfoAdapter(r))
        .collect(Collectors.toList());
  }

  RecipeMetadataRecipeInfoAdapter buildAdapter(RecipeMetadata recipe, MetadataService db) {
    var similar = retrieveSimilarRecipes(recipe.getSimilarRecipeIds(), db);
    return new RecipeMetadataRecipeInfoAdapter(recipe, similar);
  }

  static class RecipeMetadataSimilarInfoAdapter extends SimilarInfo {

    private final RecipeMetadata delegate;

    RecipeMetadataSimilarInfoAdapter(RecipeMetadata delegate) {
      this.delegate = delegate;
    }

    @Override
    public String name() {
      return delegate.getName();
    }

    @Override
    public String siteName() {
      return delegate.getSiteName();
    }

    @Override
    public String infoUrl() {
      return buildInfoUrl(delegate);
    }
  }

  private static final String URI_RECIPE_SLUG_ID = "/recipe/%s/%d";

  static String buildInfoUrl(RecipeMetadata recipe) {
    return String.format(URI_RECIPE_SLUG_ID, recipe.getSlug(), recipe.getRecipeId());
  }

  static class RecipeMetadataRecipeInfoAdapter implements RecipeInfo {

    private final RecipeMetadata metadata;
    private final List<SimilarInfo> similarRecipes;

    RecipeMetadataRecipeInfoAdapter(RecipeMetadata metadata, List<SimilarInfo> similarRecipes) {
      this.metadata = metadata;
      this.similarRecipes = similarRecipes;
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
    public String crawlUrl() {
      return metadata.getCrawlUrl();
    }

    @Override
    public String infoUrl() {
      return buildInfoUrl(metadata);
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

    @Override
    public boolean hasSimilarRecipes() {
      return similarRecipes.size() > 0;
    }

    @Override
    public List<SimilarInfo> similarRecipes() {
      return similarRecipes;
    }
  }

  static class OverPaginationError extends RuntimeException {
    OverPaginationError(String message) {
      super(message);
    }
  }
}
