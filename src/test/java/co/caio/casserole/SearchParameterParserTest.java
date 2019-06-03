package co.caio.casserole;

import static org.junit.jupiter.api.Assertions.*;

import co.caio.casserole.component.SearchParameterParser;
import co.caio.casserole.component.SearchParameterParser.SearchParameterException;
import co.caio.casserole.index.Facet.DietOption;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchQuery.DietSpec;
import co.caio.cerberus.model.SearchQuery.RangedSpec;
import co.caio.cerberus.model.SearchQuery.SortOrder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SearchParameterParserTest {

  private static final int pageSize = 10;
  private static final SearchParameterParser parser = new SearchParameterParser(pageSize);

  @Test
  void buildQuery() {
    var input = new HashMap<String, String>();
    var builder = new SearchQuery.Builder().maxResults(pageSize).maxFacets(5);

    input.put("q", "oil");
    assertEquals(parser.buildQuery(input), builder.fulltext("oil").build());

    input.put("sort", "cook_time");
    assertEquals(parser.buildQuery(input), builder.sort(SortOrder.COOK_TIME).build());

    input.put("ni", "5,10");
    assertEquals(parser.buildQuery(input), builder.numIngredients(RangedSpec.of(5, 10)).build());

    input.put("tt", "10");
    assertEquals(parser.buildQuery(input), builder.totalTime(RangedSpec.of(0, 10)).build());

    input.put("n_k", "200,0");
    assertEquals(
        parser.buildQuery(input), builder.calories(RangedSpec.of(200, Integer.MAX_VALUE)).build());

    input.put("n_f", "1,52");
    assertEquals(parser.buildQuery(input), builder.fatContent(RangedSpec.of(1, 52)).build());

    input.put("n_c", "30");
    assertEquals(
        parser.buildQuery(input), builder.carbohydrateContent(RangedSpec.of(0, 30)).build());

    input.put("diet", "keto");
    assertEquals(parser.buildQuery(input), builder.diet("keto").build());

    input.put("diet", "keto:0.75");
    assertEquals(parser.buildQuery(input), builder.diet("keto", 0.75f).build());

    input.put("page", "1");
    assertEquals(parser.buildQuery(input), builder.build());

    input.put("page", "2");
    assertEquals(parser.buildQuery(input), builder.offset(pageSize).build());

    input.put("page", "4");
    assertEquals(parser.buildQuery(input), builder.offset((4 - 1) * pageSize).build());
  }

  @Test
  void smallOrEmptyQueryIsAllowed() {
    assertDoesNotThrow(() -> parser.buildQuery(Collections.singletonMap("q", "")));
    assertDoesNotThrow(() -> parser.buildQuery(Collections.singletonMap("q", "oi")));
  }

  @Test
  void cantPaginateAfter30() {
    assertThrows(
        SearchParameterException.class,
        () -> parser.buildQuery(Map.of("q", "unused", "page", "31")));
  }

  @Test
  void unknownParameterThrows() {
    assertThrows(
        SearchParameterException.class,
        () -> parser.buildQuery(Collections.singletonMap("unknown", "doesn't matter")));
  }

  @Test
  void parseSortOrder() {

    for (SortOrder order : SortOrder.values()) {
      var parsed = parser.parseSortOrder(order.name().toLowerCase());
      assertEquals(order, parsed);
    }

    // Any other value should throw
    assertThrows(SearchParameterException.class, () -> parser.parseSortOrder("invalid sort"));
  }

  @Test
  void parseRange() {
    // Plain numbers are treated as [0,number]
    assertEquals(RangedSpec.of(0, 10), parser.parseRange("10"));
    // Ranges are encoded as "numberA,numberB"
    assertEquals(RangedSpec.of(1, 10), parser.parseRange("1,10"));
    // Special case: "numberA,0" means [numberA, MAX]
    assertEquals(RangedSpec.of(42, Integer.MAX_VALUE), parser.parseRange("42,0"));

    assertThrows(SearchParameterException.class, () -> parser.parseRange("asd"));

    assertThrows(SearchParameterException.class, () -> parser.parseRange(",10"));
    assertThrows(SearchParameterException.class, () -> parser.parseRange("10,"));

    assertThrows(SearchParameterException.class, () -> parser.parseRange("1,notANumber"));
    assertThrows(SearchParameterException.class, () -> parser.parseRange("1,10hue"));
    assertThrows(SearchParameterException.class, () -> parser.parseRange("10,10 "));
    assertThrows(SearchParameterException.class, () -> parser.parseRange("  10,10"));

    // Verify that we throw when there's still stuff after the range spec
    assertThrows(SearchParameterException.class, () -> parser.parseRange("10,10,10"));
    // And that inverted ranges are handled as errors
    assertThrows(SearchParameterException.class, () -> parser.parseRange("5,1"));
  }

  @Test
  void parseDiet() {
    assertEquals(DietSpec.of("keto", 0.57F), parser.parseDiet("keto:0.57"));
    assertEquals(DietSpec.of("paleo", 1F), parser.parseDiet("paleo"));
  }

  @Test
  void canParseAllKnownDiets() {
    for (DietOption dietOption : DietOption.values()) {
      assertDoesNotThrow(() -> parser.parseDiet(dietOption.getIndexKey()));
      assertDoesNotThrow(() -> parser.parseDiet(dietOption.getIndexKey() + ":0.42"));
    }
  }

  @Test
  void refuseToParseUnknownDiet() {
    assertThrows(SearchParameterException.class, () -> parser.parseDiet("unknown"));
  }

  @Test
  void refuseToParseDietWithExtraData() {
    var validInput = DietOption.KETO.getIndexKey() + ":0.9";
    assertDoesNotThrow(() -> parser.parseDiet(validInput));
    assertThrows(SearchParameterException.class, () -> parser.parseDiet(validInput + "rubbish"));
    assertThrows(
        SearchParameterException.class, () -> parser.parseDiet(validInput + ":moreTokens"));
    assertThrows(SearchParameterException.class, () -> parser.parseDiet(validInput + ":"));
  }
}
