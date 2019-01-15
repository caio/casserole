package co.caio.cerberus.boot;

import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchResult;
import co.caio.cerberus.model.SearchResultRecipe;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.result.view.Rendering;

class Renderer {

  private final int pageSize;

  Renderer(@Qualifier("pageSize") int pageSize) {
    this.pageSize = pageSize;
  }

  private Map<String, String> baseModel =
      Map.of(
          "site_title", "gula.recipes",
          "page_description", "",
          "search_title", "Search for Recipes",
          "search_subtitle", "Over a million delicious recipes, zero ads",
          "search_placeholder", "Ingredients, diets, brands, etc.",
          "search_value", "",
          "search_text", "Search");

  Rendering renderIndex() {
    return Rendering.view("index").model(baseModel).build();
  }

  Rendering renderSearch(SearchQuery query, SearchResult result) {
    var model = new HashMap<String, Object>(baseModel);
    model.put("page_title", "Search Results");
    model.put("search_value", query.fulltext().orElse(""));
    model.put("search_text", "Search again");

    // FIXME test each result state
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

      // FIXME proper pagination hrefs
      model.put("pagination_next_href", isLastPage ? null : "next_page");
      model.put("pagination_prev_href", currentPage == 1 ? null : "prev_page");

      model.put("pagination_start", query.offset() + 1);
      model.put("pagination_end", result.recipes().size() + query.offset());
      model.put("pagination_max", result.totalHits());

      model.put("recipes", renderRecipes(result.recipes()));

      return Rendering.view("search").model(model).build();
    }
  }

  private List<Map<String, Object>> renderRecipes(List<SearchResultRecipe> recipes) {
    return recipes
        .stream()
        .map(
            srr -> {
              List<Map<String, Object>> meta = List.of(); // FIXME
              return Map.of(
                  "name",
                  srr.name(),
                  "href",
                  srr.crawlUrl(),
                  "site",
                  "nowhere.local", // FIXME
                  "description",
                  "", // FIXME
                  "meta",
                  meta);
            })
        .collect(Collectors.toList());
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
