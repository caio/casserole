package co.caio.cerberus.boot;

import co.caio.cerberus.db.RecipeMetadata;
import co.caio.cerberus.db.RecipeMetadataDatabase;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchResult;
import co.caio.cerberus.model.SearchResultRecipe;
import co.caio.tablier.model.ErrorInfo;
import co.caio.tablier.model.PageInfo;
import co.caio.tablier.model.RecipeInfo;
import co.caio.tablier.model.SearchFormInfo;
import co.caio.tablier.model.SearchResultsInfo;
import co.caio.tablier.model.SiteInfo;
import co.caio.tablier.view.Error;
import co.caio.tablier.view.Index;
import co.caio.tablier.view.Search;
import co.caio.tablier.view.ZeroResults;
import com.fizzed.rocker.RockerModel;
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
  private static final PageInfo defaultIndexPage =
      new PageInfo.Builder().title(INDEX_PAGE_TITLE).build();
  private static final SearchFormInfo defaultSearchForm = new SearchFormInfo.Builder().build();
  private static final PageInfo unstableIndexPage =
      new PageInfo.Builder().from(defaultIndexPage).showUnstableWarning(true).build();
  private static final SearchFormInfo disabledSearchForm =
      new SearchFormInfo.Builder().isAutoFocus(false).isDisabled(true).build();
  private static final SearchFormInfo.Builder defaultSearchSearchFormBuilder =
      new SearchFormInfo.Builder().isAutoFocus(false).isDisabled(false).buttonText("Search again");
  private static final PageInfo defaultSearchPage =
      new PageInfo.Builder().title(SEARCH_PAGE_TITLE).build();
  private static final PageInfo defaultErrorPage =
      new PageInfo.Builder().title(ERROR_PAGE_TITLE).build();

  private final int pageSize;
  private final RecipeMetadataDatabase db;

  ModelView(
      @Qualifier("searchPageSize") int pageSize,
      @Qualifier("metadataDb") RecipeMetadataDatabase db) {
    this.pageSize = pageSize;
    this.db = db;
  }

  RockerModel renderIndex() {
    return Index.template(defaultSite, defaultIndexPage, defaultSearchForm);
  }

  RockerModel renderUnstableIndex() {
    return Index.template(defaultSite, unstableIndexPage, disabledSearchForm);
  }

  RockerModel renderSearch(
      SearchQuery query, SearchResult result, UriComponentsBuilder uriBuilder) {

    var searchForm = defaultSearchSearchFormBuilder.value(query.fulltext().orElse("")).build();

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
        new ErrorInfo.Builder().title(errorTitle).subtitle(errorSubtitle).build());
  }

  class RecipeMetadataRecipeInfoAdapter implements RecipeInfo {
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
  }

  class OverPaginationError extends RuntimeException {
    OverPaginationError(String message) {
      super(message);
    }
  }
}
