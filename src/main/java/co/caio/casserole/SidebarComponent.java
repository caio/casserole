package co.caio.casserole;

import co.caio.casserole.Sidebar.Category;
import co.caio.casserole.Sidebar.CategoryOption;
import co.caio.cerberus.model.FacetData.LabelData;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchQuery.RangedSpec;
import co.caio.cerberus.model.SearchResult;
import co.caio.tablier.model.FilterInfo;
import co.caio.tablier.model.SidebarInfo;
import java.util.Map.Entry;
import org.springframework.web.util.UriComponentsBuilder;

class SidebarComponent {

  static final String NUTRITION_INFO_NAME = "Limit Nutrition (per serving)";

  private static final SearchResult EMPTY_SEARCH_RESULT = new SearchResult.Builder().build();
  private static final LabelData EMPTY_LABEL_DATA = LabelData.of("EMPTY", 0);
  private static final RangedSpec UNSELECTED_RANGE = RangedSpec.of(0, 0);

  SidebarComponent() {}

  SidebarInfo build(SearchQuery query, UriComponentsBuilder uriBuilder) {
    return build(query, EMPTY_SEARCH_RESULT, uriBuilder);
  }

  SidebarInfo build(SearchQuery query, SearchResult result, UriComponentsBuilder uriBuilder) {
    var builder = new SidebarInfo.Builder();

    var sort = new FilterInfo.Builder().name(Category.SORT.getTitle()).isRemovable(false);
    addCategoryOptions(sort, Category.SORT, query.sort(), result, uriBuilder);
    builder.addFilters(sort.build());

    var showCounts = !result.facets().isEmpty();

    var diet = new FilterInfo.Builder().name(Category.DIET.getTitle()).showCounts(showCounts);
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
            .findFirst()
            .orElse("NONE");
    addCategoryOptions(diet, Category.DIET, selectedDiet, result, uriBuilder);
    builder.addFilters(diet.build());

    var numIngredient =
        new FilterInfo.Builder().name(Category.NUM_INGREDIENT.getTitle()).showCounts(showCounts);
    addCategoryOptions(
        numIngredient,
        Category.NUM_INGREDIENT,
        query.numIngredients().orElse(UNSELECTED_RANGE),
        result,
        uriBuilder);
    builder.addFilters(numIngredient.build());

    var totalTime =
        new FilterInfo.Builder().name(Category.TOTAL_TIME.getTitle()).showCounts(showCounts);
    addCategoryOptions(
        totalTime,
        Category.TOTAL_TIME,
        query.totalTime().orElse(UNSELECTED_RANGE),
        result,
        uriBuilder);
    builder.addFilters(totalTime.build());

    var nutrition = new FilterInfo.Builder().name(NUTRITION_INFO_NAME).showCounts(showCounts);
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
    builder.addFilters(nutrition.build());

    return builder.build();
  }

  // TODO Change the result model to use hashmaps for the label
  //      data since the order is irrelevant (or rather, the order is
  //      only important when rendering)

  private int countLabelData(SearchResult result, Category category, CategoryOption label) {
    return (int)
        result
            .facets()
            .stream()
            .filter(m -> m.dimension().equals(category.getIndexKey()))
            .flatMap(fd -> fd.children().stream())
            .filter(ld -> ld.label().equals(label.getIndexKey()))
            .findFirst()
            .orElse(EMPTY_LABEL_DATA)
            .count();
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
