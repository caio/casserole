package co.caio.casserole;

import static org.junit.jupiter.api.Assertions.*;

import co.caio.casserole.IndexFacet.Category;
import co.caio.casserole.IndexFacet.CategoryRange;
import co.caio.casserole.IndexFacet.DietOption;
import co.caio.cerberus.model.Recipe;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IndexFacetTest {

  private static final IndexFacet indexFacet = new IndexFacet();

  @Test
  void extractRangeLabels() {
    // Simple extraction, no multi-value
    assertEquals(
        Set.of(CategoryRange.FROM_ZERO_TO_FIVE.getIndexKey()),
        indexFacet.extractRangeLabels(Category.NUM_INGREDIENT, 2));

    // Multi-valued
    assertEquals(
        Set.of(
            CategoryRange.FROM_THIRTY_TO_SIXTY.getIndexKey(),
            CategoryRange.FROM_SIXTY_TO_INFINITY.getIndexKey()),
        indexFacet.extractRangeLabels(Category.TOTAL_TIME, 60));

    assertEquals(
        Set.of(
            CategoryRange.FROM_ZERO_TO_TWO_HUNDRED.getIndexKey(),
            CategoryRange.FROM_ZERO_TO_FIVE_HUNDRED.getIndexKey()),
        indexFacet.extractRangeLabels(Category.CALORIES, 42));

    // No labels
    assertEquals(Set.of(), indexFacet.extractRangeLabels(Category.FAT_CONTENT, 500));
  }

  @Test
  void extractDietLabels() {
    var recipe =
        new Recipe.Builder()
            .recipeId(1)
            .name("name")
            .siteName("site")
            .crawlUrl("url")
            .slug("slug")
            .addInstructions("a", "b", "c")
            .addIngredients("egg")
            .putDiets("keto", 0.2f)
            .putDiets("vegetarian", 1f)
            .putDiets("unknown", 1f)
            .build();

    // Only "vegetarian" should be collected because:
    //  - keto's score is != 1
    //  - unknown diet is, well, unknown
    assertEquals(Set.of(DietOption.VEGETARIAN.getIndexKey()), indexFacet.extractDiets(recipe));
  }
}
