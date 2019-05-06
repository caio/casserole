package co.caio.casserole;

import static org.junit.jupiter.api.Assertions.*;

import co.caio.casserole.component.SearchParameterParser;
import co.caio.casserole.component.SidebarRenderer;
import co.caio.casserole.index.Facet.Category;
import co.caio.casserole.index.Facet.CategoryRange;
import co.caio.cerberus.model.FacetData;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchQuery.RangedSpec;
import co.caio.cerberus.model.SearchQuery.SortOrder;
import co.caio.cerberus.model.SearchResult;
import co.caio.tablier.model.FilterInfo;
import co.caio.tablier.model.FilterInfo.FilterOption;
import co.caio.tablier.model.SidebarInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
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
  void resultDataIsUsed() {
    var cat = Category.DIET;

    var unusedQuery = new SearchQuery.Builder().fulltext("*").build();
    var facetDataBuilder = new FacetData.Builder().dimension(cat.getIndexKey());

    // Assemble a facet data with random counts for every
    // option for this category and record it in a map so that
    // we can compare with the rendered result
    var wantedCounts = new HashMap<String, Integer>();
    var random = new Random();
    cat.getOptions()
        .forEach(
            opt -> {
              var count = random.nextInt(100) + 1;
              facetDataBuilder.putChildren(opt.getIndexKey(), count);
              wantedCounts.put(opt.getTitle(), count);
            });

    var result =
        new SearchResult.Builder()
            .addRecipe(1)
            .totalHits(1)
            .putFacets(cat.getIndexKey(), facetDataBuilder.build())
            .build();

    var rendered = SIDEBAR_RENDERER.render(unusedQuery, result, uriBuilder);

    var info = findFilterInfo(rendered, cat.getTitle());

    assertTrue(info.showCounts());

    info.options()
        .forEach(
            fo -> {
              assertEquals((int) wantedCounts.get(fo.name()), fo.count());
            });
  }

  @Test
  void dontShowCountsWithActive() {
    // We don't drill sideways on facets so the counts that we get
    // for a category when we already have an option selected are
    // for the number of recipes we'd get if we selected ANOTHER
    // option, which we don't allow (kinda...)
    var cat = Category.TOTAL_TIME;

    var facetDataBuilder = new FacetData.Builder().dimension(cat.getIndexKey());

    var random = new Random();
    cat.getOptions()
        .forEach(
            opt -> {
              var count = random.nextInt(100) + 1;
              facetDataBuilder.putChildren(opt.getIndexKey(), count);
            });

    var result =
        new SearchResult.Builder()
            .addRecipe(1)
            .totalHits(1)
            .putFacets(cat.getIndexKey(), facetDataBuilder.build())
            .build();

    // If we selected totalTime, this filter option should not
    // have counts being displayed
    var withTT =
        SIDEBAR_RENDERER.render(
            new SearchQuery.Builder()
                .fulltext("*")
                .totalTime((CategoryRange) cat.getOptions().get(0))
                .build(),
            result,
            uriBuilder);
    assertFalse(findFilterInfo(withTT, cat.getTitle()).showCounts());

    // But any other query should render the totalTime counts
    var withoutTT =
        SIDEBAR_RENDERER.render(
            new SearchQuery.Builder().fulltext("*").build(), result, uriBuilder);
    assertTrue(findFilterInfo(withoutTT, cat.getTitle()).showCounts());
  }

  @Test
  void singleFilterSelection() {
    Category.DIET
        .getOptions()
        .forEach(
            option -> {
              var query =
                  new SearchQuery.Builder()
                      .fulltext("*")
                      .addMatchDiet(option.getIndexKey())
                      .build();
              var sidebar = SIDEBAR_RENDERER.render(query, uriBuilder);
              var info = findFilterInfo(sidebar, Category.DIET.getTitle());
              var activeItems = findActive(info);
              assertEquals(1, activeItems.size());
              assertEquals(option.getTitle(), activeItems.get(0).name());
            });

    Category.NUM_INGREDIENT
        .getOptions()
        .forEach(
            option -> {
              var query =
                  new SearchQuery.Builder()
                      .fulltext("*")
                      .numIngredients((CategoryRange) option)
                      .build();
              var sidebar = SIDEBAR_RENDERER.render(query, uriBuilder);
              var info = findFilterInfo(sidebar, Category.NUM_INGREDIENT.getTitle());
              var activeItems = findActive(info);
              assertEquals(1, activeItems.size());
              assertEquals(option.getTitle(), activeItems.get(0).name());
            });

    Category.TOTAL_TIME
        .getOptions()
        .forEach(
            option -> {
              var query =
                  new SearchQuery.Builder().fulltext("*").totalTime((CategoryRange) option).build();
              var sidebar = SIDEBAR_RENDERER.render(query, uriBuilder);
              var info = findFilterInfo(sidebar, Category.TOTAL_TIME.getTitle());
              var activeItems = findActive(info);
              assertEquals(1, activeItems.size());
              assertEquals(option.getTitle(), activeItems.get(0).name());
            });

    Category.CALORIES
        .getOptions()
        .forEach(
            option -> {
              var query =
                  new SearchQuery.Builder().fulltext("*").calories((CategoryRange) option).build();
              var sidebar = SIDEBAR_RENDERER.render(query, uriBuilder);
              var info = findFilterInfo(sidebar, SidebarRenderer.NUTRITION_INFO_NAME);
              var activeItems = findActive(info);
              assertEquals(1, activeItems.size());
              assertEquals(option.getTitle(), activeItems.get(0).name());
            });

    Category.FAT_CONTENT
        .getOptions()
        .forEach(
            option -> {
              var query =
                  new SearchQuery.Builder()
                      .fulltext("*")
                      .fatContent((CategoryRange) option)
                      .build();
              var sidebar = SIDEBAR_RENDERER.render(query, uriBuilder);
              var info = findFilterInfo(sidebar, SidebarRenderer.NUTRITION_INFO_NAME);
              var activeItems = findActive(info);
              assertEquals(1, activeItems.size());
              assertEquals(option.getTitle(), activeItems.get(0).name());
            });

    Category.CARB_CONTENT
        .getOptions()
        .forEach(
            option -> {
              var query =
                  new SearchQuery.Builder()
                      .fulltext("*")
                      .carbohydrateContent((CategoryRange) option)
                      .build();
              var sidebar = SIDEBAR_RENDERER.render(query, uriBuilder);
              var info = findFilterInfo(sidebar, SidebarRenderer.NUTRITION_INFO_NAME);
              var activeItems = findActive(info);
              assertEquals(1, activeItems.size());
              assertEquals(option.getTitle(), activeItems.get(0).name());
            });
  }

  @Test
  void multipleFilterSelection() {
    // Pick the first from each category, select them all
    var ni = (CategoryRange) Category.NUM_INGREDIENT.getOptions().get(0);
    var tt = (CategoryRange) Category.TOTAL_TIME.getOptions().get(0);
    var nk = (CategoryRange) Category.CALORIES.getOptions().get(0);
    var nf = (CategoryRange) Category.FAT_CONTENT.getOptions().get(0);
    var nc = (CategoryRange) Category.CARB_CONTENT.getOptions().get(0);
    var diet = Category.DIET.getOptions().get(0);

    var query =
        new SearchQuery.Builder()
            .fulltext("ignored")
            .numIngredients(ni)
            .totalTime(tt)
            .calories(nk)
            .fatContent(nf)
            .carbohydrateContent(nc)
            .addMatchDiet(diet.getIndexKey())
            .build();

    var sidebar = SIDEBAR_RENDERER.render(query, uriBuilder);

    var ingredientInfo = findFilterInfo(sidebar, Category.NUM_INGREDIENT.getTitle());
    var activeIngredients = findActive(ingredientInfo);
    assertEquals(1, activeIngredients.size());
    assertEquals(ni.getTitle(), activeIngredients.get(0).name());

    var totalTimeInfo = findFilterInfo(sidebar, Category.TOTAL_TIME.getTitle());
    var activeTimes = findActive(totalTimeInfo);
    assertEquals(1, activeTimes.size());
    assertEquals(tt.getTitle(), activeTimes.get(0).name());

    var dietInfo = findFilterInfo(sidebar, Category.DIET.getTitle());
    var activeDiets = findActive(dietInfo);
    assertEquals(1, activeDiets.size());
    assertEquals(diet.getTitle(), activeDiets.get(0).name());

    // Nutrition category allows multiple selection
    var nutritionInfo = findFilterInfo(sidebar, SidebarRenderer.NUTRITION_INFO_NAME);
    var activeNutrition =
        findActive(nutritionInfo).stream().map(FilterOption::name).collect(Collectors.toSet());
    assertEquals(Set.of(nk.getTitle(), nf.getTitle(), nc.getTitle()), activeNutrition);
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
