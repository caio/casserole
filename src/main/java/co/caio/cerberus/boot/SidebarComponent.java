package co.caio.cerberus.boot;

import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchQuery.RangedSpec;
import co.caio.cerberus.model.SearchQuery.SortOrder;
import co.caio.tablier.model.FilterInfo;
import co.caio.tablier.model.FilterInfo.FilterOption;
import co.caio.tablier.model.SidebarInfo;
import java.util.List;
import org.springframework.web.util.UriComponentsBuilder;

class SidebarComponent {

  static final String SORT_INFO_NAME = "Sort recipes by";
  static final String INGREDIENTS_INFO_NAME = "Limit Ingredients";
  static final String TIME_INFO_NAME = "Limit Total Time";
  static final String NUTRITION_INFO_NAME = "Limit Nutrition (per serving)";

  SidebarComponent() {}

  SidebarInfo build(SearchQuery query, UriComponentsBuilder uriBuilder) {
    var builder = new SidebarInfo.Builder();

    addSortOptions(builder, query, uriBuilder.cloneBuilder());
    addIngredientFilters(builder, query, uriBuilder.cloneBuilder());
    addTotalTimeFilters(builder, query, uriBuilder.cloneBuilder());
    addNutritionFilters(builder, query, uriBuilder.cloneBuilder());

    return builder.build();
  }

  private void addSortOptions(
      SidebarInfo.Builder builder, SearchQuery query, UriComponentsBuilder uriBuilder) {

    var sortInfoBuilder =
        new FilterInfo.Builder().showCounts(false).isRemovable(false).name(SORT_INFO_NAME);

    for (SortOptionSpec spec : sortOptions) {
      sortInfoBuilder.addOptions(spec.buildOption(uriBuilder, query.sort()));
    }

    builder.addFilters(sortInfoBuilder.build());
  }

  private void addIngredientFilters(
      SidebarInfo.Builder builder, SearchQuery query, UriComponentsBuilder uriBuilder) {
    var activeIng = query.numIngredients().orElse(unselectedRange);

    var ingredientsFilterInfoBuilder =
        new FilterInfo.Builder().showCounts(false).name(INGREDIENTS_INFO_NAME);

    for (var spec : ingredientFilterOptions) {
      ingredientsFilterInfoBuilder.addOptions(spec.buildOption(uriBuilder, activeIng));
    }

    builder.addFilters(ingredientsFilterInfoBuilder.build());
  }

  private void addTotalTimeFilters(
      SidebarInfo.Builder builder, SearchQuery query, UriComponentsBuilder uriBuilder) {
    var activeTT = query.totalTime().orElse(unselectedRange);
    var timeFilterInfoBuilder = new FilterInfo.Builder().showCounts(false).name(TIME_INFO_NAME);

    for (var spec : totalTimeFilterOptions) {
      timeFilterInfoBuilder.addOptions(spec.buildOption(uriBuilder, activeTT));
    }

    builder.addFilters(timeFilterInfoBuilder.build());
  }

  private void addNutritionFilters(
      SidebarInfo.Builder builder, SearchQuery query, UriComponentsBuilder uriBuilder) {
    var activeKcal = query.calories().orElse(unselectedRange);
    var activeFat = query.fatContent().orElse(unselectedRange);
    var activeCarbs = query.carbohydrateContent().orElse(unselectedRange);

    var nutritionFilterInfoBuilder =
        new FilterInfo.Builder().showCounts(false).name(NUTRITION_INFO_NAME);

    var otherUriBuilder = uriBuilder.cloneBuilder();
    for (var spec : caloriesFilterOptions) {
      nutritionFilterInfoBuilder.addOptions(spec.buildOption(otherUriBuilder, activeKcal));
    }

    otherUriBuilder = uriBuilder.cloneBuilder();
    for (var spec : fatFilterOptions) {
      nutritionFilterInfoBuilder.addOptions(spec.buildOption(otherUriBuilder, activeFat));
    }

    // NOTE that this uses uriBuilder directly instead of a clone to save a copy
    //      if more options are added this will need to be adjusted
    for (var spec : carbsFilterOptions) {
      nutritionFilterInfoBuilder.addOptions(spec.buildOption(uriBuilder, activeCarbs));
    }

    builder.addFilters(nutritionFilterInfoBuilder.build());
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

  private static final RangedSpec unselectedRange = RangedSpec.of(0, 0);
}
