package co.caio.cerberus.boot;

import co.caio.cerberus.db.RecipeMetadata;
import co.caio.cerberus.db.RecipeMetadataDatabase;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchResult;
import co.caio.cerberus.model.SearchResultRecipe;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.util.UriComponentsBuilder;

@Component
class ModelView {

  private final int pageSize;
  private final RecipeMetadataDatabase db;

  ModelView(
      @Qualifier("searchPageSize") int pageSize,
      @Qualifier("metadataDb") RecipeMetadataDatabase db) {
    this.pageSize = pageSize;
    this.db = db;
  }

  private Map<String, String> baseModel =
      Map.of(
          "site_title", "gula.recipes",
          "page_description", "",
          "search_title", "Search Recipes",
          "search_subtitle", "Over a million delicious recipes, zero ads",
          "search_placeholder", "Ingredients, diets, brands, etc.",
          "search_value", "",
          "search_text", "Search");

  Rendering renderIndex() {
    return Rendering.view("index").model(baseModel).build();
  }

  Rendering renderUnstableIndex() {
    return Rendering.view("index")
        .model(baseModel)
        .modelAttribute("search_is_disabled", true)
        .modelAttribute("show_unstable_warning", true)
        .build();
  }

  Rendering renderSearch(SearchQuery query, SearchResult result, UriComponentsBuilder uriBuilder) {
    var model = new HashMap<String, Object>(baseModel);
    model.put("search_no_focus", true);
    model.put("page_title", "Search Results");
    model.put("search_value", query.fulltext().orElse(""));
    model.put("search_text", "Search again");

    if (result.totalHits() == 0) {
      return Rendering.view("zero_results").model(model).build();
    } else if (query.offset() >= result.totalHits()) {
      // over pagination
      return renderError(
          "Invalid Page Number", "No more results to show for this search", HttpStatus.BAD_REQUEST);
    } else {
      // normal results
      boolean isLastPage = query.offset() + pageSize >= result.totalHits();
      int currentPage = (query.offset() / pageSize) + 1;

      uriBuilder.fragment("results");
      model.put(
          "pagination_next_href",
          isLastPage ? null : uriBuilder.replaceQueryParam("page", currentPage + 1).build());
      model.put(
          "pagination_prev_href",
          currentPage == 1 ? null : uriBuilder.replaceQueryParam("page", currentPage - 1).build());

      model.put("pagination_start", query.offset() + 1);
      model.put("pagination_end", result.recipes().size() + query.offset());
      model.put("pagination_max", result.totalHits());

      model.put("recipes", renderRecipes(result.recipes()));

      return Rendering.view("search").model(model).build();
    }
  }

  private Iterable<RecipeMetadata> renderRecipes(List<SearchResultRecipe> recipes) {
    var recipeIds = recipes.stream().map(SearchResultRecipe::recipeId).collect(Collectors.toList());
    return db.findAllById(recipeIds);
  }

  Rendering renderError(String errorTitle, String errorSubtitle, HttpStatus status) {
    return Rendering.view("error")
        .model(baseModel)
        .modelAttribute("page_title", "An Error Has Occurred")
        .modelAttribute("error_title", errorTitle)
        .modelAttribute("error_subtitle", errorSubtitle)
        .status(status)
        .build();
  }
}
