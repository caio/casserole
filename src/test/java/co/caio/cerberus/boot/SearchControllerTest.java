package co.caio.cerberus.boot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import co.caio.cerberus.boot.FailureResponse.ErrorKind;
import co.caio.cerberus.model.SearchResult;
import java.time.Duration;
import java.util.List;
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

  @Test
  void apiReturnsJsonOnSuccess() {
    var searchResult =
        new SearchResult.Builder()
            .addRecipe(42, "the answer", "https://nowhere.here")
            .totalHits(100)
            .build();

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
}
