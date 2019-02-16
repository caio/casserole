package co.caio.cerberus.boot;

import co.caio.cerberus.db.RecipeMetadata;
import co.caio.cerberus.db.RecipeMetadataDatabase;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchResult;
import co.caio.cerberus.model.SearchResultRecipe;
import co.caio.tablier.model.ErrorInfo;
import co.caio.tablier.model.FilterInfo;
import co.caio.tablier.model.PageInfo;
import co.caio.tablier.model.RecipeInfo;
import co.caio.tablier.model.SearchFormInfo;
import co.caio.tablier.model.SearchResultsInfo;
import co.caio.tablier.model.SidebarInfo;
import co.caio.tablier.model.SiteInfo;
import co.caio.tablier.view.Error;
import co.caio.tablier.view.Index;
import co.caio.tablier.view.Recipe;
import co.caio.tablier.view.Search;
import co.caio.tablier.view.ZeroResults;
import com.fizzed.rocker.RockerModel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
class ModelView {

  static final String INDEX_PAGE_TITLE = "Your Private Recipe Search Website";
  static final String SEARCH_PAGE_TITLE = "Search Results";
  static final String ERROR_PAGE_TITLE = "An Error Has Occurred";

  private static final SiteInfo defaultSite = new SiteInfo.Builder().build();
  private static final SiteInfo unstableSite = new SiteInfo.Builder().isUnstable(true).build();
  private static final PageInfo defaultIndexPage =
      new PageInfo.Builder().title(INDEX_PAGE_TITLE).build();
  private static final SearchFormInfo autoFocusSearchForm =
      new SearchFormInfo.Builder().isAutoFocus(true).build();
  private static final SearchFormInfo defaultSearchForm =
      new SearchFormInfo.Builder().isAutoFocus(false).build();
  private static final PageInfo defaultSearchPage =
      new PageInfo.Builder().title(SEARCH_PAGE_TITLE).build();
  private static final PageInfo defaultErrorPage =
      new PageInfo.Builder().title(ERROR_PAGE_TITLE).build();
  private static final String DEFAULT_UNKNOWN_ERROR_SUBTITLE = "Unknown Error Cause";

  private final int pageSize;
  private final RecipeMetadataDatabase db;
  private final CircuitBreaker breaker;

  ModelView(
      @Qualifier("searchPageSize") int pageSize,
      @Qualifier("metadataDb") RecipeMetadataDatabase db,
      CircuitBreaker breaker) {
    this.pageSize = pageSize;
    this.breaker = breaker;
    this.db = db;
  }

  RockerModel renderIndex() {
    if (breaker.isCallPermitted()) {
      return Index.template(defaultSite, defaultIndexPage, autoFocusSearchForm);
    } else {
      return Index.template(unstableSite, defaultIndexPage, defaultSearchForm);
    }
  }

  RockerModel renderSearch(
      SearchQuery query, SearchResult result, UriComponentsBuilder uriBuilder) {

    var searchForm =
        new SearchFormInfo.Builder().isAutoFocus(false).value(query.fulltext().orElse("")).build();

    if (result.totalHits() == 0) {
      return ZeroResults.template(defaultSite, defaultSearchPage, searchForm);
    } else if (query.offset() >= result.totalHits()) {
      throw new OverPaginationError("No more results to show for this search");
    } else {
      boolean isLastPage = query.offset() + pageSize >= result.totalHits();
      int currentPage = (query.offset() / pageSize) + 1;

      var searchBuilder =
          new SearchResultsInfo.Builder()
              .paginationStart(query.offset() + 1)
              .paginationEnd(result.recipes().size() + query.offset())
              .numMatching(result.totalHits());

      uriBuilder.fragment("results");

      if (!isLastPage) {
        searchBuilder.nextPageHref(
            uriBuilder.replaceQueryParam("page", currentPage + 1).build().toUriString());
      }

      if (currentPage != 1) {
        searchBuilder.previousPageHref(
            uriBuilder.replaceQueryParam("page", currentPage - 1).build().toUriString());
      }

      searchBuilder.recipes(renderRecipes(result.recipes()));

      // TODO interpret SearchQuery filters so that we can display an
      //      explanation such as "searched for avocado, with 6 to 10 ingredients
      //      and cook time less than 15 minutes.
      // TODO figure out active / undo filtering
      // XXX maybe add a link to reset filters

      var sbb = new SidebarInfo.Builder();

      // Filters always lead to the first page
      // FIXME test
      uriBuilder.replaceQueryParam("page");

      // We need to clone the builder for every group of filters otherwise
      // we'll end up overwriting previous selections or poisoning the
      // uris for the subsequent filters
      var ub = uriBuilder.cloneBuilder();

      sbb.addFilters(
          new FilterInfo.Builder()
              .showCounts(false)
              .name("Sort recipes by")
              .addOption(
                  "Relevance", ub.replaceQueryParam("sort", "relevance").build().toUriString(), 0)
              .addOption(
                  "Fastest to Cook",
                  ub.replaceQueryParam("sort", "total_time").build().toUriString(),
                  0)
              .addOption(
                  "Least Ingredients",
                  ub.replaceQueryParam("sort", "num_ingredients").build().toUriString(),
                  0)
              .build());

      ub = uriBuilder.cloneBuilder();
      sbb.addFilters(
          new FilterInfo.Builder()
              .showCounts(false)
              .name("Limit Ingredients")
              .addOption("Less than 5", ub.replaceQueryParam("ni", "5").build().toUriString(), 0)
              .addOption("6 to 10", ub.replaceQueryParam("ni", "6,10").build().toUriString(), 0)
              .addOption(
                  "More than 10", ub.replaceQueryParam("ni", "11,0").build().toUriString(), 0)
              .build());

      ub = uriBuilder.cloneBuilder();
      sbb.addFilters(
          new FilterInfo.Builder()
              .showCounts(false)
              .name("Limit Cook Time")
              .addOption(
                  "Up to 15 minutes", ub.replaceQueryParam("ct", "15").build().toUriString(), 0)
              .addOption(
                  "15 to 30 minutes", ub.replaceQueryParam("ct", "15,30").build().toUriString(), 0)
              .addOption(
                  "30 to 60 minutes", ub.replaceQueryParam("ct", "30,60").build().toUriString(), 0)
              .addOption(
                  "One hour or more", ub.replaceQueryParam("ct", "60,0").build().toUriString(), 0)
              .build());

      var nutritionFilterBuilder =
          new FilterInfo.Builder().showCounts(false).name("Limit Nutrition (per serving)");

      ub = uriBuilder.cloneBuilder();
      nutritionFilterBuilder
          .addOption("Up to 200 kcal", ub.replaceQueryParam("n_k", "200").build().toUriString(), 0)
          .addOption("Up to 500 kcal", ub.replaceQueryParam("n_k", "500").build().toUriString(), 0);

      nutritionFilterBuilder
          .addOption(
              "Up to 10g of Fat",
              ub.cloneBuilder().replaceQueryParam("n_f", "10").build().toUriString(),
              0)
          .addOption(
              "Up to 30g of Carbs",
              ub.cloneBuilder().replaceQueryParam("n_c", "30").build().toUriString(),
              0);

      sbb.addFilters(nutritionFilterBuilder.build());

      searchBuilder.sidebar(sbb.build());

      return Search.template(defaultSite, defaultSearchPage, searchForm, searchBuilder.build());
    }
  }

  private Iterable<RecipeInfo> renderRecipes(List<SearchResultRecipe> recipes) {
    var recipeIds = recipes.stream().map(SearchResultRecipe::recipeId).collect(Collectors.toList());
    return db.findAllById(recipeIds)
        .stream()
        .map(RecipeMetadataRecipeInfoAdapter::new)
        .collect(Collectors.toList());
  }

  RockerModel renderError(String errorTitle, String errorSubtitle) {
    return Error.template(
        defaultSite,
        defaultErrorPage,
        defaultSearchForm,
        new ErrorInfo.Builder()
            .subtitle(errorSubtitle == null ? DEFAULT_UNKNOWN_ERROR_SUBTITLE : errorSubtitle)
            .title(errorTitle)
            .build());
  }

  RockerModel renderSingleRecipe(long recipeId, String slug) {
    var recipe = db.findById(recipeId).orElseThrow(RecipeNotFoundError::new);

    if (!slug.equals(recipe.getSlug())) {
      throw new RecipeNotFoundError();
    }

    return Recipe.template(
        defaultSite,
        new PageInfo.Builder().title(recipe.getName()).build(),
        defaultSearchForm,
        new RecipeMetadataRecipeInfoAdapter(recipe));
  }

  static class RecipeMetadataRecipeInfoAdapter implements RecipeInfo {
    private final RecipeMetadata metadata;

    RecipeMetadataRecipeInfoAdapter(RecipeMetadata metadata) {
      this.metadata = metadata;
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
    public String slug() {
      return String.format("%s/%d", metadata.getSlug(), metadata.getRecipeId());
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
    public OptionalInt totalTime() {
      return metadata.getTotalTime();
    }

    @Override
    public List<String> ingredients() {
      return metadata.getIngredients();
    }

    @Override
    public List<String> instructions() {
      return metadata.getInstructions();
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
