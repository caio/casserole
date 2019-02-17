package co.caio.cerberus.boot;

import co.caio.cerberus.db.RecipeMetadata;
import co.caio.cerberus.db.RecipeMetadataDatabase;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchQuery.RangedSpec;
import co.caio.cerberus.model.SearchQuery.SortOrder;
import co.caio.cerberus.model.SearchResult;
import co.caio.cerberus.model.SearchResultRecipe;
import co.caio.tablier.model.ErrorInfo;
import co.caio.tablier.model.FilterInfo;
import co.caio.tablier.model.FilterInfo.FilterOption;
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
  private static final RangedSpec unselectedRange = RangedSpec.of(0, 0);

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

  static class SortOptionSpec {
    private final String name;
    private final SortOrder order;
    private final String queryValue;

    SortOptionSpec(String name, SortOrder order, String queryValue) {
      this.name = name;
      this.order = order;
      this.queryValue = queryValue;
    }

    FilterOption buildOption(UriComponentsBuilder uriBuilder, SortOrder active) {

      var href = uriBuilder.replaceQueryParam("sort", queryValue);

      return new FilterInfo.FilterOption.Builder()
          .name(name)
          .isActive(active.equals(order))
          .href(href.build().toUriString())
          .build();
    }
  }

  static class RangeOptionSpec {

    private final String name;
    private final int start;
    private final int end;
    private final String queryName;
    private final String queryValue;

    RangeOptionSpec(String name, int start, int end, String queryName) {
      this.name = name;
      this.start = start;
      this.end = end;
      this.queryName = queryName;
      this.queryValue = String.format("%d,%d", start, end == Integer.MAX_VALUE ? 0 : end);
    }

    FilterOption buildOption(UriComponentsBuilder uriBuilder, RangedSpec selected) {
      var isActive = selected.start() == start && selected.end() == end;

      var href =
          isActive
              ? uriBuilder.replaceQueryParam(queryName)
              : uriBuilder.replaceQueryParam(queryName, queryValue);

      return new FilterInfo.FilterOption.Builder()
          .name(name)
          .isActive(isActive)
          .href(href.build().toUriString())
          .build();
    }
  }

  private static final List<SortOptionSpec> sortOptions =
      List.of(
          new SortOptionSpec("Relevance", SortOrder.RELEVANCE, "relevance"),
          new SortOptionSpec("Fastest to Cook", SortOrder.TOTAL_TIME, "total_time"),
          new SortOptionSpec("Least Ingredients", SortOrder.NUM_INGREDIENTS, "num_ingredients"),
          new SortOptionSpec("Calories", SortOrder.CALORIES, "calories"));

  private static final List<RangeOptionSpec> ingredientFilterOptions =
      List.of(
          new RangeOptionSpec("Up to 5", 0, 5, "ni"),
          new RangeOptionSpec("From 6 to 10", 6, 10, "ni"),
          new RangeOptionSpec("More than 10", 10, Integer.MAX_VALUE, "ni"));

  private static final List<RangeOptionSpec> totalTimeFilterOptions =
      List.of(
          new RangeOptionSpec("Up to 15 minutes", 0, 15, "tt"),
          new RangeOptionSpec("From 15 to 30 minutes", 15, 30, "tt"),
          new RangeOptionSpec("From 30 to 60 minutes", 30, 60, "tt"),
          new RangeOptionSpec("One hour or more", 60, Integer.MAX_VALUE, "tt"));

  private static final List<RangeOptionSpec> caloriesFilterOptions =
      List.of(
          new RangeOptionSpec("Up to 200 kcal", 0, 200, "n_k"),
          new RangeOptionSpec("Up to 500 kcal", 0, 500, "n_k"));

  private static final List<RangeOptionSpec> fatFilterOptions =
      List.of(new RangeOptionSpec("Up to 10g of Fat", 0, 10, "n_f"));

  private static final List<RangeOptionSpec> carbsFilterOptions =
      List.of(new RangeOptionSpec("Up to 30g of Carbs", 0, 30, "n_c"));

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
      // SORTING
      var ub = uriBuilder.cloneBuilder();

      var sortInfoBuilder =
          new FilterInfo.Builder().showCounts(false).isRemovable(false).name("Sort recipes by");

      for (SortOptionSpec spec : sortOptions) {
        sortInfoBuilder.addOptions(spec.buildOption(ub, query.sort()));
      }

      sbb.addFilters(sortInfoBuilder.build());

      // INGREDIENTS
      ub = uriBuilder.cloneBuilder();
      var activeIng = query.numIngredients().orElse(unselectedRange);

      var ingredientsFilterInfoBuilder =
          new FilterInfo.Builder().showCounts(false).name("Limit Ingredients");

      for (var spec : ingredientFilterOptions) {
        ingredientsFilterInfoBuilder.addOptions(spec.buildOption(ub, activeIng));
      }

      sbb.addFilters(ingredientsFilterInfoBuilder.build());

      // TOTAL TIME
      ub = uriBuilder.cloneBuilder();
      var activeTT = query.totalTime().orElse(unselectedRange);
      var timeFilterInfoBuilder =
          new FilterInfo.Builder().showCounts(false).name("Limit Total Time");

      for (var spec : totalTimeFilterOptions) {
        timeFilterInfoBuilder.addOptions(spec.buildOption(ub, activeTT));
      }

      sbb.addFilters(timeFilterInfoBuilder.build());

      // NUTRITION
      // TODO We use mixed queryValues in this filter, make sure we're
      //      not poisoning the urls
      var activeKcal = query.calories().orElse(unselectedRange);
      var activeFat = query.fatContent().orElse(unselectedRange);
      var activeCarbs = query.carbohydrateContent().orElse(unselectedRange);
      var nutritionFilterInfoBuilder =
          new FilterInfo.Builder().showCounts(false).name("Limit Nutrition (per serving)");

      ub = uriBuilder.cloneBuilder();
      for (var spec : caloriesFilterOptions) {
        nutritionFilterInfoBuilder.addOptions(spec.buildOption(ub, activeKcal));
      }

      ub = uriBuilder.cloneBuilder();
      for (var spec : fatFilterOptions) {
        nutritionFilterInfoBuilder.addOptions(spec.buildOption(ub, activeFat));
      }

      ub = uriBuilder.cloneBuilder();
      for (var spec : carbsFilterOptions) {
        nutritionFilterInfoBuilder.addOptions(spec.buildOption(ub, activeCarbs));
      }

      sbb.addFilters(nutritionFilterInfoBuilder.build());

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
