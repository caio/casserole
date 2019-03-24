package co.caio.casserole;

import co.caio.casserole.Sidebar.Category;
import co.caio.cerberus.model.FacetData.LabelData;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchQuery.RangedSpec;
import co.caio.cerberus.model.SearchQuery.SortOrder;
import co.caio.cerberus.model.SearchResult;
import co.caio.tablier.model.FilterInfo;
import co.caio.tablier.model.FilterInfo.FilterOption;
import co.caio.tablier.model.SidebarInfo;
import java.util.List;
import org.springframework.web.util.UriComponentsBuilder;

class SidebarComponent {

  static final String SORT_INFO_NAME = "Sort recipes by";
  static final String DIETS_INFO_NAME = "Restrict by Diet";
  static final String INGREDIENTS_INFO_NAME = "Limit Ingredients";
  static final String TIME_INFO_NAME = "Limit Total Time";
  static final String NUTRITION_INFO_NAME = "Limit Nutrition (per serving)";

  private static final SearchResult EMPTY_SEARCH_RESULT = new SearchResult.Builder().build();
  private static final LabelData EMPTY_LABEL_DATA = LabelData.of("EMPTY", 0);

  private static final List<SortOptionSpec> sortOptions =
      List.of(
          new SortOptionSpec("Relevance", SortOrder.RELEVANCE),
          new SortOptionSpec("Fastest to Cook", SortOrder.TOTAL_TIME),
          new SortOptionSpec("Least Ingredients", SortOrder.NUM_INGREDIENTS),
          new SortOptionSpec("Calories", SortOrder.CALORIES));

  // XXX Should SortOrder be a StringEnum? Maybe client-based also?
  //     One too many raw strings here
  private static final List<DietOptionSpec> dietFilterOptions =
      List.of(
          new DietOptionSpec("Low Carb", "lowcarb"),
          new DietOptionSpec("Vegetarian", "vegetarian"),
          new DietOptionSpec("Keto", "keto"),
          new DietOptionSpec("Paleo", "paleo"));

  // TODO Figure out how to share all these Range(X,Y) with Sidebar extractors
  private static final List<NumIngredientOptionSpec> ingredientFilterOptions =
      List.of(
          new NumIngredientOptionSpec("Up to 5", new Range(0, 5)),
          new NumIngredientOptionSpec("From 6 to 10", new Range(6, 10)),
          new NumIngredientOptionSpec("More than 10", new Range(11, Integer.MAX_VALUE)));

  private static final List<TotalTimeOptionSpec> totalTimeFilterOptions =
      List.of(
          new TotalTimeOptionSpec("Up to 15 minutes", new Range(0, 15)),
          new TotalTimeOptionSpec("From 15 to 30 minutes", new Range(15, 30)),
          new TotalTimeOptionSpec("From 30 to 60 minutes", new Range(30, 60)),
          new TotalTimeOptionSpec("One hour or more", new Range(60, Integer.MAX_VALUE)));

  private static final List<CaloriesOptionSpec> caloriesFilterOptions =
      List.of(
          new CaloriesOptionSpec("Up to 200 kcal", new Range(0, 200)),
          new CaloriesOptionSpec("Up to 500 kcal", new Range(0, 500)));

  private static final List<FatContentOptionSpec> fatFilterOptions =
      List.of(new FatContentOptionSpec("Up to 10g of Fat", new Range(0, 10)));

  private static final List<CarbContentOptionSpec> carbsFilterOptions =
      List.of(new CarbContentOptionSpec("Up to 30g of Carbs", new Range(0, 30)));

  private static final RangedSpec unselectedRange = RangedSpec.of(0, 0);

  SidebarComponent() {}

  SidebarInfo build(SearchQuery query, UriComponentsBuilder uriBuilder) {
    return build(query, EMPTY_SEARCH_RESULT, uriBuilder);
  }

  SidebarInfo build(SearchQuery query, SearchResult result, UriComponentsBuilder uriBuilder) {
    var builder = new SidebarInfo.Builder();

    addSortOptions(builder, query, uriBuilder.cloneBuilder());
    addDietFilters(builder, query, uriBuilder.cloneBuilder(), result);
    addIngredientFilters(builder, query, uriBuilder.cloneBuilder(), result);
    addTotalTimeFilters(builder, query, uriBuilder.cloneBuilder(), result);

    addNutritionFilters(builder, query, uriBuilder.cloneBuilder(), result);

    return builder.build();
  }

  // TODO Change the result model to use hashmaps for the label
  //      data since the order is irrelevant (or rather, the order is
  //      only important when rendering)

  private int countLabelData(SearchResult result, Category category, String label) {
    return (int)
        result
            .facets()
            .stream()
            .filter(m -> m.dimension().equals(category.toString()))
            .flatMap(fd -> fd.children().stream())
            .filter(ld -> ld.label().equals(label))
            .findFirst()
            .orElse(EMPTY_LABEL_DATA)
            .count();
  }

  private void addSortOptions(
      SidebarInfo.Builder builder, SearchQuery query, UriComponentsBuilder uriBuilder) {

    var sortInfoBuilder = new FilterInfo.Builder().isRemovable(false).name(SORT_INFO_NAME);

    for (SortOptionSpec spec : sortOptions) {
      sortInfoBuilder.addOptions(spec.buildOption(uriBuilder, query.sort()));
    }

    builder.addFilters(sortInfoBuilder.build());
  }

  private void addDietFilters(
      SidebarInfo.Builder builder,
      SearchQuery query,
      UriComponentsBuilder uriBuilder,
      SearchResult result) {

    var selectedDiets = query.dietThreshold().keySet();

    if (selectedDiets.size() > 1) {
      throw new IllegalStateException("Don't know how to handle multiple selected diets");
    }

    var dietsFilterInfoBuilder = new FilterInfo.Builder().name(DIETS_INFO_NAME);

    dietsFilterInfoBuilder.showCounts(!result.facets().isEmpty());

    var selected = selectedDiets.isEmpty() ? null : selectedDiets.iterator().next();

    for (var spec : dietFilterOptions) {
      dietsFilterInfoBuilder.addOptions(
          spec.buildOption(
              uriBuilder, selected, countLabelData(result, Category.DIET, spec.queryValue)));
    }

    builder.addFilters(dietsFilterInfoBuilder.build());
  }

  private void addIngredientFilters(
      SidebarInfo.Builder builder,
      SearchQuery query,
      UriComponentsBuilder uriBuilder,
      SearchResult result) {
    var activeIng = query.numIngredients().orElse(unselectedRange);

    var ingredientsFilterInfoBuilder = new FilterInfo.Builder().name(INGREDIENTS_INFO_NAME);

    ingredientsFilterInfoBuilder.showCounts(!result.facets().isEmpty());

    for (var spec : ingredientFilterOptions) {
      ingredientsFilterInfoBuilder.addOptions(
          spec.buildOption(
              uriBuilder,
              activeIng,
              countLabelData(result, Category.NUM_INGREDIENT, spec.queryValue.toString())));
    }

    builder.addFilters(ingredientsFilterInfoBuilder.build());
  }

  private void addTotalTimeFilters(
      SidebarInfo.Builder builder,
      SearchQuery query,
      UriComponentsBuilder uriBuilder,
      SearchResult result) {
    var activeTT = query.totalTime().orElse(unselectedRange);
    var timeFilterInfoBuilder = new FilterInfo.Builder().name(TIME_INFO_NAME);

    timeFilterInfoBuilder.showCounts(!result.facets().isEmpty());

    for (var spec : totalTimeFilterOptions) {
      timeFilterInfoBuilder.addOptions(
          spec.buildOption(
              uriBuilder,
              activeTT,
              countLabelData(result, Category.TOTAL_TIME, spec.queryValue.toString())));
    }

    builder.addFilters(timeFilterInfoBuilder.build());
  }

  private void addNutritionFilters(
      SidebarInfo.Builder builder,
      SearchQuery query,
      UriComponentsBuilder uriBuilder,
      SearchResult result) {
    var activeKcal = query.calories().orElse(unselectedRange);
    var activeFat = query.fatContent().orElse(unselectedRange);
    var activeCarbs = query.carbohydrateContent().orElse(unselectedRange);

    var nutritionFilterInfoBuilder = new FilterInfo.Builder().name(NUTRITION_INFO_NAME);

    var otherUriBuilder = uriBuilder.cloneBuilder();
    for (var spec : caloriesFilterOptions) {
      var count = countLabelData(result, Category.CALORIES, spec.queryValue.toString());
      nutritionFilterInfoBuilder.addOptions(spec.buildOption(otherUriBuilder, activeKcal, count));
    }

    otherUriBuilder = uriBuilder.cloneBuilder();
    for (var spec : fatFilterOptions) {
      var count = countLabelData(result, Category.FAT_CONTENT, spec.queryValue.toString());
      nutritionFilterInfoBuilder.addOptions(spec.buildOption(otherUriBuilder, activeFat, count));
    }

    // NOTE that this uses uriBuilder directly instead of a clone to save a copy
    //      if more options are added this will need to be adjusted
    for (var spec : carbsFilterOptions) {
      var count = countLabelData(result, Category.CARB_CONTENT, spec.queryValue.toString());
      nutritionFilterInfoBuilder.addOptions(spec.buildOption(uriBuilder, activeCarbs, count));
    }

    nutritionFilterInfoBuilder.showCounts(!result.facets().isEmpty());

    builder.addFilters(nutritionFilterInfoBuilder.build());
  }

  abstract static class OptionSpec<N, V> {
    final String name;
    final N queryName;
    final V queryValue;

    OptionSpec(String name, N queryName, V queryValue) {
      this.name = name;
      this.queryName = queryName;
      this.queryValue = queryValue;
    }

    String getUri(UriComponentsBuilder builder, boolean isActive) {
      if (isActive) {
        builder.replaceQueryParam(queryName.toString());
      } else {
        builder.replaceQueryParam(queryName.toString(), queryValue.toString());
      }
      return builder.build().toUriString();
    }

    boolean isActive(V selected) {
      return queryValue.equals(selected);
    }

    FilterOption buildOption(UriComponentsBuilder uriBuilder, V selected, int count) {
      var isActive = isActive(selected);
      return new FilterInfo.FilterOption.Builder()
          .name(name)
          .count(count)
          .href(getUri(uriBuilder, isActive))
          .isActive(isActive)
          .build();
    }
  }

  static class SortOptionSpec extends OptionSpec<String, SortOrder> {

    SortOptionSpec(String name, SortOrder queryValue) {
      // XXX should this move to Sidebar?
      super(name, "sort", queryValue);
    }

    FilterOption buildOption(UriComponentsBuilder uriBuilder, SortOrder active) {
      return super.buildOption(uriBuilder, active, 0);
    }

    @Override
    String getUri(UriComponentsBuilder builder, boolean isActive) {
      // See, this toLowerCase() bothers me
      return builder
          .replaceQueryParam("sort", queryValue.name().toLowerCase())
          .build()
          .toUriString();
    }
  }

  static class DietOptionSpec extends OptionSpec<Category, String> {
    DietOptionSpec(String name, String queryValue) {
      super(name, Category.DIET, queryValue);
    }

    @Override
    String getUri(UriComponentsBuilder builder, boolean isActive) {
      if (isActive) {
        builder.replaceQueryParam(queryName.toString());
        builder.replaceQueryParam("science");
      } else {
        builder.replaceQueryParam(queryName.toString(), queryValue);
      }
      return builder.build().toUriString();
    }
  }

  abstract static class RangeOptionSpec extends OptionSpec<Category, RangedSpec> {
    RangeOptionSpec(String name, Category queryName, RangedSpec queryValue) {
      super(name, queryName, queryValue);
    }
  }

  static class NumIngredientOptionSpec extends RangeOptionSpec {
    NumIngredientOptionSpec(String name, RangedSpec queryValue) {
      super(name, Category.NUM_INGREDIENT, queryValue);
    }
  }

  static class TotalTimeOptionSpec extends RangeOptionSpec {
    TotalTimeOptionSpec(String name, RangedSpec queryValue) {
      super(name, Category.TOTAL_TIME, queryValue);
    }
  }

  static class CaloriesOptionSpec extends RangeOptionSpec {
    CaloriesOptionSpec(String name, RangedSpec queryValue) {
      super(name, Category.CALORIES, queryValue);
    }
  }

  static class FatContentOptionSpec extends RangeOptionSpec {
    FatContentOptionSpec(String name, RangedSpec queryValue) {
      super(name, Category.FAT_CONTENT, queryValue);
    }
  }

  static class CarbContentOptionSpec extends RangeOptionSpec {
    CarbContentOptionSpec(String name, RangedSpec queryValue) {
      super(name, Category.CARB_CONTENT, queryValue);
    }
  }

  static class Range implements RangedSpec {
    final int start;
    final int end;

    Range(int start, int end) {
      this.start = start;
      this.end = end;
    }

    @Override
    public boolean equals(Object obj) {
      // Shouldn't immutables have generated this?
      if (obj instanceof RangedSpec) {
        var spec = (RangedSpec) obj;
        return start == spec.start() && end == spec.end();
      }
      return super.equals(obj);
    }

    @Override
    public String toString() {
      // This is how we encode ranges in the parameter parser
      // TODO join up both implementations so this logic only appears once
      return start + "," + (end == Integer.MAX_VALUE ? 0 : end);
    }

    @Override
    public int start() {
      return start;
    }

    @Override
    public int end() {
      return end;
    }
  }
}
