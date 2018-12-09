package co.caio.cerberus.boot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import co.caio.cerberus.boot.FailureResponse.ErrorKind;
import co.caio.cerberus.model.SearchQuery.Builder;
import co.caio.cerberus.model.SearchQuery.RangedSpec;
import co.caio.cerberus.model.SearchQuery.SortOrder;
import co.caio.cerberus.model.SearchResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

class SearchControllerTest extends BaseSearchControllerTest {

  @TestConfiguration
  static class TestConfig {
    @Bean("searchTimeout")
    public Duration timeout() {
      // No timeout
      return Duration.ofMillis(Long.MAX_VALUE);
    }
  }

  @Test
  void badInputReturnsJson() {

    // Missing required params
    expectFailure("/search", HttpStatus.UNPROCESSABLE_ENTITY, ErrorKind.QUERY_ERROR);

    // Required parameter is too short
    expectFailure("/search?q=oi", HttpStatus.UNPROCESSABLE_ENTITY, ErrorKind.QUERY_ERROR);

    // `n` is too big
    expectFailure("/search?q=oil&n=200", HttpStatus.UNPROCESSABLE_ENTITY, ErrorKind.QUERY_ERROR);
  }

  @Test
  void apiReturnsJsonOnInternalError() {
    given(searcher.search(any())).willThrow(RuntimeException.class);
    expectFailure("/search?q=salt", HttpStatus.INTERNAL_SERVER_ERROR, ErrorKind.UNKNOWN_ERROR);
  }

  @Test
  void unknownParameterTriggersError() {
    expectFailure(
        "/search?q=valid query&unknownParam=drop",
        HttpStatus.UNPROCESSABLE_ENTITY,
        ErrorKind.QUERY_ERROR);
  }

  @Test
  void invalidRangeTriggersCorrectError() {
    var prefix = "/search?q=salt&ni=";
    var badRanges = List.of("1notANumber", "10,bad", "100,1", "1,2,3", "42,", ",5");

    badRanges.forEach(
        spec -> {
          expectFailure(prefix + spec, HttpStatus.UNPROCESSABLE_ENTITY, ErrorKind.QUERY_ERROR);
        });
  }

  private SearchResult fakeResult(String name) {
    // The name parameter is used so that we can generate multiple
    // distinct fake results in the same test
    return new SearchResult.Builder()
        .addRecipe(42, name, "https://nowhere.here")
        .totalHits(100)
        .build();
  }

  @Test
  void apiReturnsJsonOnSuccess() {
    var searchResult = fakeResult("recipe name");

    given(searcher.search(any())).willReturn(searchResult);

    var response =
        testClient
            .get()
            .uri("/search?q=a valid query")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(SuccessResponse.class)
            .returnResult()
            .getResponseBody();

    assertTrue(response.isSuccess());
    assertEquals(searchResult, response.result);
  }

  @Test
  void queryIsBuiltCorrectly() {

    var inputToQuery =
        Map.of(
            "q=oil", new Builder().fulltext("oil").build(),
            "q=oil&sort=cook_time", new Builder().fulltext("oil").sort(SortOrder.COOK_TIME).build(),
            "q=oil&n=42", new Builder().fulltext("oil").maxResults(42).build(),
            "q=oil&ni=10",
                new Builder().fulltext("oil").numIngredients(RangedSpec.of(0, 10)).build());

    inputToQuery.forEach(
        (input, query) -> {
          var searchResult = fakeResult(input);

          given(searcher.search(query)).willReturn(searchResult);

          var response =
              testClient
                  .get()
                  .uri("/search?" + input)
                  .exchange()
                  .expectStatus()
                  .isOk()
                  .expectBody(SuccessResponse.class)
                  .returnResult()
                  .getResponseBody();

          assertEquals(searchResult, response.result);
        });
  }
}
