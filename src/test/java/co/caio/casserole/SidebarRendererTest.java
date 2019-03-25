package co.caio.casserole;

import static org.junit.jupiter.api.Assertions.*;

import co.caio.casserole.IndexFacet.Category;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchQuery.RangedSpec;
import co.caio.cerberus.model.SearchQuery.SortOrder;
import co.caio.tablier.model.FilterInfo;
import co.caio.tablier.model.FilterInfo.FilterOption;
import co.caio.tablier.model.SidebarInfo;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

class SidebarRendererTest {

  private static final SidebarRenderer SIDEBAR_RENDERER = new SidebarRenderer();
  private static final SearchParameterParser paramParser = new SearchParameterParser(20);
  private UriComponentsBuilder uriBuilder;

  @BeforeEach
  void setup() {
    uriBuilder = UriComponentsBuilder.fromUriString("/test");
  }

  @Test
  void sortOptionsCantBeRemovedAndDonNotHaveCounts() {
    var query = new SearchQuery.Builder().fulltext("pecan").build();
    var sidebar = SIDEBAR_RENDERER.render(query, uriBuilder);
    var sorts = findFilterInfo(sidebar, Category.SORT.getTitle());

    assertFalse(sorts.isRemovable());
    assertFalse(sorts.showCounts());
  }

  @Test
  void selectedSortIsMarkedAsActive() {
    int numIterations = 0;

    // XXX This is super weird now
    for (SortOrder order : SortOrder.values()) {
      // Only use options that are exposed
      var opt = Category.SORT.getOptions().stream().filter(o -> o.isActive(order)).findFirst();

      if (opt.isEmpty()) {
        continue;
      }

      var indexedOption = opt.get();
      numIterations++;

      var query = new SearchQuery.Builder().fulltext("ignored").sort(order).build();
      var info =
          findFilterInfo(SIDEBAR_RENDERER.render(query, uriBuilder), Category.SORT.getTitle());

      var numSelected =
          info.options()
              .stream()
              .filter(FilterOption::isActive)
              .peek(fo -> assertEquals(indexedOption.getTitle(), fo.name()))
              .count();

      assertEquals(1, numSelected);
    }

    assertEquals(Category.SORT.getOptions().size(), numIterations);
  }

  @Test
  void selectedFiltersAreMarkedAsActive() {
    // Right now there isn't a way to know which RangedSpecs are implemented
    // in the sidebar so these RangedSpec.of() calls are hardcoded. Not sure
    // if I care enough to make this more programmable as there's only value
    // for this particular test now
    var query =
        new SearchQuery.Builder()
            .fulltext("ignored")
            .numIngredients(RangedSpec.of(0, 5))
            .totalTime(RangedSpec.of(15, 30))
            .calories(RangedSpec.of(0, 200))
            .fatContent(RangedSpec.of(0, 10))
            .carbohydrateContent(RangedSpec.of(0, 30))
            .addMatchDiet("keto")
            .build();

    var sidebar = SIDEBAR_RENDERER.render(query, uriBuilder);

    var ingredientInfo = findFilterInfo(sidebar, Category.NUM_INGREDIENT.getTitle());
    var activeIngredients = findActive(ingredientInfo);
    assertEquals(1, activeIngredients.size());
    assertTrue(activeIngredients.get(0).name().endsWith("to 5"));

    var totalTimeInfo = findFilterInfo(sidebar, Category.TOTAL_TIME.getTitle());
    var activeTimes = findActive(totalTimeInfo);
    assertEquals(1, activeTimes.size());
    assertTrue(activeTimes.get(0).name().endsWith("15 to 30 minutes"));

    var dietInfo = findFilterInfo(sidebar, Category.DIET.getTitle());
    var activeDiets = findActive(dietInfo);
    assertEquals(1, activeDiets.size());
    assertEquals("Keto", activeDiets.get(0).name());

    var nutritionInfo = findFilterInfo(sidebar, SidebarRenderer.NUTRITION_INFO_NAME);
    var activeNutritionList = findActive(nutritionInfo);
    assertEquals(3, activeNutritionList.size());
  }

  @Test
  void removingSelectedDietAlsoRemovesScience() {
    var query = new SearchQuery.Builder().fulltext("peanut").putDietThreshold("keto", 0.8f).build();
    var uriBuilder = UriComponentsBuilder.fromUriString("/test?diet=keto&science=0.8");
    var sidebar = SIDEBAR_RENDERER.render(query, uriBuilder);

    var dietInfo = findFilterInfo(sidebar, Category.DIET.getTitle());
    var active = findActive(dietInfo);
    assertEquals(1, active.size());

    var params = getQueryParams(active.get(0).href());
    assertFalse(params.containsKey("science"));
  }

  @Test
  void activeFilterHrefRemovesFilterParam() {
    var query =
        new SearchQuery.Builder().fulltext("ignored").totalTime(RangedSpec.of(30, 60)).build();
    var sidebar = SIDEBAR_RENDERER.render(query, uriBuilder);
    var totalTimeInfo = findFilterInfo(sidebar, Category.TOTAL_TIME.getTitle());
    var activeTimes = findActive(totalTimeInfo);
    assertEquals(1, activeTimes.size());
    assertEquals("From 30 to 60 minutes", activeTimes.get(0).name());
    assertEquals("/test", activeTimes.get(0).href());
  }

  @Test
  void originalParametersArePreserved() {
    var query = new SearchQuery.Builder().fulltext("ignored").build();
    var ub = UriComponentsBuilder.fromUriString("/test?must=preserve");
    var sidebar = SIDEBAR_RENDERER.render(query, ub);

    sidebar
        .filters()
        .forEach(
            fi ->
                fi.options()
                    .forEach(
                        fo -> {
                          var params = getQueryParams(fo.href());
                          assertEquals("preserve", params.getFirst("must"));
                        }));
  }

  @Test
  void sameFilterCategoryGetsReplacedDifferentCategoryGetsAppended() {
    var query = new SearchQuery.Builder().fulltext("ignored").build();
    var ub = UriComponentsBuilder.fromUriString("/test?ni=10,42");
    var sidebar = SIDEBAR_RENDERER.render(query, ub);

    sidebar
        .filters()
        .stream()
        .filter(FilterInfo::isRemovable)
        .forEach(
            fi ->
                fi.options()
                    .forEach(
                        fo -> {
                          var params = getQueryParams(fo.href());
                          if (fi.name().equals(Category.NUM_INGREDIENT.getTitle())) {
                            assertEquals(1, params.size());
                            assertNotEquals("10,42", params.getFirst("ni"));
                          } else {
                            assertEquals(2, params.size());
                            assertEquals("10,42", params.getFirst("ni"));
                          }
                        }));
  }

  List<FilterOption> findActive(FilterInfo info) {
    return info.options().stream().filter(FilterOption::isActive).collect(Collectors.toList());
  }

  FilterInfo findFilterInfo(SidebarInfo sidebar, String name) {
    return sidebar
        .filters()
        .stream()
        .filter(fi -> fi.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  MultiValueMap<String, String> getQueryParams(String href) {
    return UriComponentsBuilder.fromUriString(href).build().getQueryParams();
  }

  @Test
  void nutritionFilterDoesNotPoisonURIs() {
    var query = new SearchQuery.Builder().fulltext("ignored").build();
    var sidebar = SIDEBAR_RENDERER.render(query, uriBuilder);

    var nutritionFilters = findFilterInfo(sidebar, SidebarRenderer.NUTRITION_INFO_NAME);

    // Generated uris should only have one query parameter
    nutritionFilters
        .options()
        .forEach(fo -> assertEquals(1, getQueryParams(fo.href()).size(), fo.toString()));
  }
}
