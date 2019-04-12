package co.caio.casserole;

import co.caio.casserole.IndexFacet.Category;
import co.caio.casserole.IndexFacet.CategoryOption;
import co.caio.cerberus.model.FacetData;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchQuery.RangedSpec;
import co.caio.cerberus.model.SearchResult;
import co.caio.tablier.model.FilterInfo;
import co.caio.tablier.model.SidebarInfo;
import java.util.Map.Entry;
import org.springframework.web.util.UriComponentsBuilder;

class SidebarRenderer {

  static final String NUTRITION_INFO_NAME = "Limit Nutrition (per serving)";

  private static final SearchResult EMPTY_SEARCH_RESULT = new SearchResult.Builder().build();
  private static final RangedSpec UNSELECTED_RANGE = RangedSpec.of(0, 0);
  private static final FacetData EMPTY_FACET_DATA =
      new FacetData.Builder().dimension("EMPTY").build();

  SidebarRenderer() {}

  SidebarInfo render(SearchQuery query, UriComponentsBuilder uriBuilder) {
    return render(query, EMPTY_SEARCH_RESULT, uriBuilder);
  }

  SidebarInfo render(SearchQuery query, SearchResult result, UriComponentsBuilder uriBuilder) {
    var builder = new SidebarInfo.Builder();

    var sort = new FilterInfo.Builder().name(Category.SORT.getTitle()).isRemovable(false);
    addCategoryOptions(sort, Category.SORT, query.sort(), result, uriBuilder);
    builder.addFilters(sort.build());

    var hasFacetData = !result.facets().isEmpty();

    var diet = new FilterInfo.Builder().name(Category.DIET.getTitle());
    var selectedDiet =
        query
            .dietThreshold()
            .entrySet()
            .stream()
            // NOTE getting the first key with a threshold set
            //      this only works because the we don't allow multiple
            //      and if we check for 1f (arguably the correct approach),
            //      we end up breaking the render when using &science=
            .filter(e -> e.getValue() > 0f)
            .map(Entry::getKey)
            .findFirst();
    addCategoryOptions(diet, Category.DIET, selectedDiet.orElse("NONE"), result, uriBuilder);
    diet.showCounts(hasFacetData && selectedDiet.isEmpty());
    builder.addFilters(diet.build());

    var numIngredient = new FilterInfo.Builder().name(Category.NUM_INGREDIENT.getTitle());
    addCategoryOptions(
        numIngredient,
        Category.NUM_INGREDIENT,
        query.numIngredients().orElse(UNSELECTED_RANGE),
        result,
        uriBuilder);
    numIngredient.showCounts(hasFacetData && query.numIngredients().isEmpty());
    builder.addFilters(numIngredient.build());

    var totalTime =
        new FilterInfo.Builder().name(Category.TOTAL_TIME.getTitle()).showCounts(hasFacetData);
    addCategoryOptions(
        totalTime,
        Category.TOTAL_TIME,
        query.totalTime().orElse(UNSELECTED_RANGE),
        result,
        uriBuilder);
    totalTime.showCounts(hasFacetData && query.totalTime().isEmpty());
    builder.addFilters(totalTime.build());

    var nutrition = new FilterInfo.Builder().name(NUTRITION_INFO_NAME).showCounts(hasFacetData);
    addCategoryOptions(
        nutrition,
        Category.CALORIES,
        query.calories().orElse(UNSELECTED_RANGE),
        result,
        uriBuilder);
    addCategoryOptions(
        nutrition,
        Category.FAT_CONTENT,
        query.fatContent().orElse(UNSELECTED_RANGE),
        result,
        uriBuilder);
    addCategoryOptions(
        nutrition,
        Category.CARB_CONTENT,
        query.carbohydrateContent().orElse(UNSELECTED_RANGE),
        result,
        uriBuilder);
    nutrition.showCounts(
        hasFacetData
            && query.calories().isEmpty()
            && query.fatContent().isEmpty()
            && query.carbohydrateContent().isEmpty());
    builder.addFilters(nutrition.build());

    return builder.build();
  }

  private int countLabelData(SearchResult result, Category category, CategoryOption label) {
    return result
        .facets()
        .getOrDefault(category.getIndexKey(), EMPTY_FACET_DATA)
        .children()
        .getOrDefault(label.getIndexKey(), 0L)
        .intValue();
  }

  private void addCategoryOptions(
      FilterInfo.Builder infoBuilder,
      Category category,
      Object selected,
      SearchResult result,
      UriComponentsBuilder cloneMe) {

    var uriBuilder = cloneMe.cloneBuilder();

    category
        .getOptions()
        .forEach(
            opt -> {
              var isActive = opt.isActive(selected);

              if (isActive) {
                uriBuilder.replaceQueryParam(category.getIndexKey());
                // FIXME Shouldn't be here, but `science` is temporary, so...
                if (category == Category.DIET) {
                  uriBuilder.replaceQueryParam("science");
                }
              } else {
                uriBuilder.replaceQueryParam(category.getIndexKey(), opt.getIndexKey());
              }

              var filterOption =
                  new FilterInfo.FilterOption.Builder()
                      .name(opt.getTitle())
                      .count(countLabelData(result, category, opt))
                      .isActive(isActive)
                      .href(uriBuilder.build().toUriString())
                      .build();

              infoBuilder.addOptions(filterOption);
            });
  }
}
