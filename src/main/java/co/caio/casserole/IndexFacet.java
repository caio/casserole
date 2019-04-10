package co.caio.casserole;

import co.caio.cerberus.model.Recipe;
import co.caio.cerberus.model.SearchQuery.RangedSpec;
import co.caio.cerberus.model.SearchQuery.SortOrder;
import co.caio.cerberus.search.CategoryExtractor;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

public class IndexFacet {

  // XXX I'm not super happy with Category and CategoryOption having
  //     the concept of a title since this is only useful for the
  //     renderer, but moving it there makes things a bit awkward
  //     so... fix me maybe?

  private final CategoryExtractor categoryExtractor;

  // TODO sync names with the parameter parser
  public enum Category {
    SORT(
        "Sort Recipes By",
        "sort",
        List.of(
            SortByOption.RELEVANCE,
            SortByOption.TOTAL_TIME,
            SortByOption.NUM_INGREDIENTS,
            SortByOption.CALORIES)),
    DIET(
        "Restrict by Diet",
        "diet",
        List.of(
            DietOption.LOW_CARB,
            DietOption.VEGETARIAN,
            DietOption.VEGAN,
            DietOption.KETO,
            DietOption.PALEO)),
    NUM_INGREDIENT(
        "Limit Ingredients",
        "ni",
        List.of(
            CategoryRange.FROM_ZERO_TO_FIVE,
            CategoryRange.FROM_SIX_TO_TEN,
            CategoryRange.FROM_ELEVEN_TO_INFINITY)),
    TOTAL_TIME(
        "Limit Total Time",
        "tt",
        List.of(
            CategoryRange.FROM_ZERO_TO_FIFTEEN,
            CategoryRange.FROM_FIFTEEN_TO_THIRTY,
            CategoryRange.FROM_THIRTY_TO_SIXTY,
            CategoryRange.FROM_SIXTY_TO_INFINITY)),
    CALORIES(
        "Calories",
        "n_k",
        List.of(CategoryRange.FROM_ZERO_TO_TWO_HUNDRED, CategoryRange.FROM_ZERO_TO_FIVE_HUNDRED)),
    FAT_CONTENT("Fat Content", "n_f", List.of(CategoryRange.FROM_ZERO_TO_TEN)),
    CARB_CONTENT("Carbohydrate Content", "n_c", List.of(CategoryRange.FROM_ZERO_TO_THIRTY));

    private final String indexKey;
    private final String title;
    private final List<CategoryOption> options;

    Category(String title, String indexKey, List<CategoryOption> options) {
      this.title = title;
      this.indexKey = indexKey;
      this.options = options;
    }

    public String getTitle() {
      return title;
    }

    public String getIndexKey() {
      return indexKey;
    }

    public List<CategoryOption> getOptions() {
      return options;
    }
  }

  public CategoryExtractor getCategoryExtractor() {
    return categoryExtractor;
  }

  public IndexFacet() {
    var builder = new CategoryExtractor.Builder();

    // NOTE that Category.SORT is not added: there's nothing to index

    builder.addCategory(Category.DIET.getIndexKey(), true, this::extractDiets);

    builder.addCategory(Category.NUM_INGREDIENT.getIndexKey(), false, this::extractNumIngredient);

    builder.addCategory(Category.TOTAL_TIME.getIndexKey(), true, this::extractTotalTime);

    builder.addCategory(Category.CALORIES.getIndexKey(), true, this::extractCalories);

    builder.addCategory(Category.FAT_CONTENT.getIndexKey(), false, this::extractFatContent);

    builder.addCategory(Category.CARB_CONTENT.getIndexKey(), false, this::extractCarbContent);

    categoryExtractor = builder.build();
  }

  interface CategoryOption {
    String getTitle();

    String getIndexKey();

    boolean isActive(Object obj);
  }

  enum SortByOption implements CategoryOption {
    RELEVANCE("Relevance", SortOrder.RELEVANCE),
    TOTAL_TIME("Fastest to Complete", SortOrder.TOTAL_TIME),
    NUM_INGREDIENTS("Least Ingredients", SortOrder.NUM_INGREDIENTS),
    CALORIES("Calories", SortOrder.CALORIES);

    private final String title;
    private final SortOrder id;

    SortByOption(String title, SortOrder id) {
      this.title = title;
      this.id = id;
    }

    @Override
    public boolean isActive(Object obj) {
      return id.equals(obj);
    }

    @Override
    public String getTitle() {
      return title;
    }

    @Override
    public String getIndexKey() {
      return id.name().toLowerCase();
    }
  }

  enum DietOption implements CategoryOption {
    LOW_CARB("Low Carb", "lowcarb"),
    KETO("Keto", "keto"),
    PALEO("Paleo", "paleo"),
    VEGAN("Vegan", "vegan"),
    VEGETARIAN("Vegetarian", "vegetarian");

    private final String title;
    private final String indexKey;

    DietOption(String title, String indexKey) {
      this.title = title;
      this.indexKey = indexKey;
    }

    @Override
    public boolean isActive(Object obj) {
      return indexKey.equals(obj);
    }

    @Override
    public String getTitle() {
      return title;
    }

    @Override
    public String getIndexKey() {
      return indexKey;
    }
  }

  enum CategoryRange implements CategoryOption, RangedSpec {
    // num_ingredients
    FROM_ZERO_TO_FIVE("Up to 5", 0, 5),
    FROM_SIX_TO_TEN("From 6 to 10", 6, 10),
    FROM_ELEVEN_TO_INFINITY("More than 10", 11, Integer.MAX_VALUE),

    // total_time
    FROM_ZERO_TO_FIFTEEN("Up to 15 minutes", 0, 15),
    FROM_FIFTEEN_TO_THIRTY("From 15 to 30 minutes", 15, 30),
    FROM_THIRTY_TO_SIXTY("From 30 to 60 minutes", 30, 60),
    FROM_SIXTY_TO_INFINITY("One hour or more", 60, Integer.MAX_VALUE),

    // calories
    FROM_ZERO_TO_TWO_HUNDRED("Up to 200 kcal", 0, 200),
    FROM_ZERO_TO_FIVE_HUNDRED("Up to 500 kcal", 0, 500),

    // fat
    FROM_ZERO_TO_TEN("Up to 10g of Fat", 0, 10),

    // carbohydrates
    FROM_ZERO_TO_THIRTY("Up to 30g of Carbs", 0, 30);

    private final String title;
    private final int start;
    private final int end;
    private final String indexKey;

    CategoryRange(String title, int start, int end) {
      this.title = title;
      this.start = start;
      this.end = end;
      this.indexKey = start + "," + (end == Integer.MAX_VALUE ? 0 : end);
    }

    @Override
    public String getTitle() {
      return title;
    }

    @Override
    public boolean isActive(Object obj) {
      if (obj instanceof RangedSpec) {
        var o = (RangedSpec) obj;
        return start == o.start() && end == o.end();
      } else {
        return false;
      }
    }

    public boolean matches(double value) {
      return value >= start && value <= end;
    }

    @Override
    public String getIndexKey() {
      return indexKey;
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

  Set<String> extractRangeLabels(Category category, double value) {
    return category
        .getOptions()
        .stream()
        .filter(opt -> ((CategoryRange) opt).matches(value))
        .map(CategoryOption::getIndexKey)
        .collect(Collectors.toSet());
  }

  private Set<String> extractNumIngredient(Recipe r) {
    return extractRangeLabels(Category.NUM_INGREDIENT, r.ingredients().size());
  }

  private Set<String> extractTotalTime(Recipe r) {
    if (r.totalTime().isEmpty()) {
      return Set.of();
    }

    return extractRangeLabels(Category.TOTAL_TIME, r.totalTime().getAsInt());
  }

  private Set<String> extractCalories(Recipe r) {
    if (r.calories().isEmpty()) {
      return Set.of();
    }

    return extractRangeLabels(Category.CALORIES, r.calories().getAsInt());
  }

  private Set<String> extractFatContent(Recipe r) {
    if (r.fatContent().isEmpty()) {
      return Set.of();
    }

    return extractRangeLabels(Category.FAT_CONTENT, r.fatContent().getAsDouble());
  }

  private Set<String> extractCarbContent(Recipe r) {
    if (r.carbohydrateContent().isEmpty()) {
      return Set.of();
    }

    return extractRangeLabels(Category.CARB_CONTENT, r.carbohydrateContent().getAsDouble());
  }

  Set<String> extractDiets(Recipe r) {
    return r.diets()
        .entrySet()
        .stream()
        .filter(e -> e.getValue() == 1f)
        .map(Entry::getKey) // Could collect and return here
        .flatMap(diet -> Category.DIET.getOptions().stream().filter(o -> o.isActive(diet)))
        .map(CategoryOption::getIndexKey)
        .collect(Collectors.toSet());
  }
}
