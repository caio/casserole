package co.caio.casserole;

import co.caio.cerberus.model.Recipe;
import co.caio.cerberus.search.CategoryExtractor;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

public class Sidebar {

  private final CategoryExtractor categoryExtractor;

  // TODO sync names with the parameter parser
  public enum Category {
    DIET("diet"),
    NUM_INGREDIENT("ni"),
    TOTAL_TIME("tt"),
    CALORIES("n_k"),
    FAT_CONTENT("n_f"),
    CARB_CONTENT("n_c");

    private final String name;

    Category(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public CategoryExtractor getCategoryExtractor() {
    return categoryExtractor;
  }

  public Sidebar() {
    var builder = new CategoryExtractor.Builder();

    builder.addCategory(Category.DIET.toString(), true, this::extractDiets);

    builder.addCategory(Category.NUM_INGREDIENT.toString(), false, this::extractNumIngredient);

    builder.addCategory(Category.TOTAL_TIME.toString(), true, this::extractTotalTime);

    builder.addCategory(Category.CALORIES.toString(), true, this::extractCalories);

    builder.addCategory(Category.FAT_CONTENT.toString(), false, this::extractFatContent);

    builder.addCategory(Category.CARB_CONTENT.toString(), false, this::extractCarbContent);

    categoryExtractor = builder.build();
  }

  // TODO Labels should be addressable also

  private Set<String> extractNumIngredient(Recipe r) {
    int n = r.ingredients().size();
    if (n <= 5) {
      return Set.of("0,5");
    } else if (n <= 10) {
      return Set.of("6,10");
    } else {
      return Set.of("11,0");
    }
  }

  private Set<String> extractTotalTime(Recipe r) {
    Set<String> result = new HashSet<>();

    if (r.totalTime().isEmpty()) {
      return result;
    }

    int t = r.totalTime().getAsInt();
    if (t <= 15) {
      result.add("0,15");
    }

    if (t >= 15 && t <= 30) {
      result.add("15,30");
    }

    if (t >= 30 && t <= 60) {
      result.add("30,60");
    }

    if (t >= 60) {
      result.add("60,0");
    }

    return result;
  }

  private Set<String> extractCalories(Recipe r) {
    int kcal = r.calories().orElse(Integer.MAX_VALUE);
    Set<String> result = new HashSet<>();

    if (kcal <= 200) {
      result.add("0,200");
    }

    if (kcal <= 500) {
      result.add("0,500");
    }

    return result;
  }

  private Set<String> extractFatContent(Recipe r) {
    double fat = r.fatContent().orElse(Double.MAX_VALUE);
    return fat <= 10 ? Set.of("0,10") : Set.of();
  }

  private Set<String> extractCarbContent(Recipe r) {
    double carbs = r.fatContent().orElse(Double.MAX_VALUE);
    return carbs <= 30 ? Set.of("0,30") : Set.of();
  }

  private Set<String> extractDiets(Recipe r) {
    return r.diets()
        .entrySet()
        .stream()
        .filter(e -> e.getValue() == 1f)
        .map(Entry::getKey)
        .collect(Collectors.toSet());
  }
}
