package co.caio.casserole;

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

  private static final String URI_RECIPE_SLUG_ID = "/recipe/{slug}/{recipeId}";

  RockerModel renderSearch(
      SearchQuery query,
      SearchResult result,
      RecipeMetadataService db,
      UriComponentsBuilder uriBuilder) {

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

    var recipeInfoUriComponents = uriBuilder.cloneBuilder().replacePath(URI_RECIPE_SLUG_ID).build();
    searchBuilder.recipes(renderRecipes(result.recipeIds(), db, recipeInfoUriComponents));

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

  private Iterable<RecipeInfo> renderRecipes(
      List<Long> recipeIds, RecipeMetadataService db, UriComponents uriComponents) {
    return recipeIds
        .stream()
        .map(db::findById)
        .flatMap(Optional::stream)
        .map(r -> buildAdapter(r, uriComponents, db))
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

  RockerModel renderSingleRecipe(
      RecipeMetadata recipe, UriComponentsBuilder builder, RecipeMetadataService db) {
    return Recipe.template(
        new SiteInfo.Builder().title(recipe.getName()).searchIsAutoFocus(false).build(),
        buildAdapter(recipe, builder.replacePath(URI_RECIPE_SLUG_ID).build(), db));
  }

  List<SimilarInfo> retrieveSimilarRecipes(
      List<Long> ids, RecipeMetadataService db, UriComponents infoComponents) {
    return ids.stream()
        .map(db::findById)
        .flatMap(Optional::stream)
        .map(r -> new RecipeMetadataSimilarInfoAdapter(r, infoComponents))
        .collect(Collectors.toList());
  }

  RecipeMetadataRecipeInfoAdapter buildAdapter(
      RecipeMetadata recipe, UriComponents infoUrlComponents, RecipeMetadataService db) {
    var similar = retrieveSimilarRecipes(recipe.getSimilarRecipeIds(), db, infoUrlComponents);
    return new RecipeMetadataRecipeInfoAdapter(recipe, infoUrlComponents, similar);
  }

  static class RecipeMetadataSimilarInfoAdapter extends SimilarInfo {

    private final RecipeMetadata delegate;
    private final UriComponents infoUriComponents;

    RecipeMetadataSimilarInfoAdapter(RecipeMetadata delegate, UriComponents infoUriComponents) {
      this.delegate = delegate;
      this.infoUriComponents = infoUriComponents;
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
      return infoUriComponents.expand(delegate.getSlug(), delegate.getRecipeId()).toUriString();
    }
  }

  static class RecipeMetadataRecipeInfoAdapter implements RecipeInfo {
    private final RecipeMetadata metadata;
    private final String infoUrl;
    private final List<SimilarInfo> similarRecipes;

    RecipeMetadataRecipeInfoAdapter(
        RecipeMetadata metadata,
        UriComponents infoUrlComponents,
        List<SimilarInfo> similarRecipes) {
      this.metadata = metadata;
      this.similarRecipes = similarRecipes;
      this.infoUrl =
          infoUrlComponents.expand(metadata.getSlug(), metadata.getRecipeId()).toUriString();
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
      return infoUrl;
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
