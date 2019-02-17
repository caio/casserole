package co.caio.cerberus.boot;

import static org.junit.jupiter.api.Assertions.*;

import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchQuery.SortOrder;
import co.caio.tablier.model.FilterInfo;
import co.caio.tablier.model.SidebarInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

class SidebarComponentTest {

  private static final SidebarComponent sidebarComponent = new SidebarComponent();
  private static final SearchParameterParser paramParser = new SearchParameterParser(20);
  private UriComponentsBuilder uriBuilder;

  @BeforeEach
  void setup() {
    uriBuilder = UriComponentsBuilder.newInstance();
    uriBuilder.path("/test");
  }

  @Test
  void sortOptionsCantBeRemovedAndDonNotHaveCounts() {
    var sbb = new SidebarInfo.Builder();
    var query = new SearchQuery.Builder().fulltext("pecan").build();

    sidebarComponent.addSortOptions(sbb, query, uriBuilder);

    var sidebar = sbb.build();

    var sorts =
        sidebar
            .filters()
            .stream()
            .filter(fi -> fi.name().equals(SidebarComponent.SORT_INFO_NAME))
            .findFirst()
            .orElseThrow();

    assertFalse(sorts.isRemovable());
    assertFalse(sorts.showCounts());
  }

  @Test
  void selectedSortIsMarkedAsActive() {
    for (SortOrder order : SortOrder.values()) {
      var query = new SearchQuery.Builder().fulltext("pecan").sort(order).build();
      var info =
          findFilterInfo(
              sidebarComponent.build(query, uriBuilder), SidebarComponent.SORT_INFO_NAME);

      info.options()
          .forEach(
              fo -> {
                var sortParam = getQueryParams(fo.href()).getFirst("sort");
                if (paramParser.parseSortOrder(sortParam).equals(order)) {
                  assertTrue(fo.isActive(), fo.toString());
                }
              });
    }
  }

  // TODO test multi selection, isActive

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
    var sbb = new SidebarInfo.Builder();
    var query = new SearchQuery.Builder().fulltext("pecan").build();

    sidebarComponent.addNutritionFilters(sbb, query, uriBuilder);

    var sidebar = sbb.build();

    var nutritionFilters = findFilterInfo(sidebar, SidebarComponent.NUTRITION_INFO_NAME);

    // Generated uris should only have one query parameter
    nutritionFilters
        .options()
        .forEach(fo -> assertEquals(1, getQueryParams(fo.href()).size(), fo.toString()));
  }
}
