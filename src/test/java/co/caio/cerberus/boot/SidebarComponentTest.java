package co.caio.cerberus.boot;

import static org.junit.jupiter.api.Assertions.*;

import co.caio.cerberus.model.SearchQuery;
import co.caio.tablier.model.SidebarInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

class SidebarComponentTest {

  private static final SidebarComponent sidebarComponent = new SidebarComponent();
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
  void nutritionFilterDoesNotPoisonURIs() {
    var sbb = new SidebarInfo.Builder();
    var query = new SearchQuery.Builder().fulltext("pecan").build();

    sidebarComponent.addNutritionFilters(sbb, query, uriBuilder);

    var sidebar = sbb.build();

    var nutritionFilters =
        sidebar
            .filters()
            .stream()
            .filter(fi -> fi.name().equals(SidebarComponent.NUTRITION_INFO_NAME))
            .findFirst()
            .orElseThrow();

    // Generated uris should only have one query parameter
    nutritionFilters
        .options()
        .forEach(
            fo ->
                assertEquals(
                    1,
                    UriComponentsBuilder.fromUriString(fo.href()).build().getQueryParams().size(),
                    fo.toString()));
  }
}
