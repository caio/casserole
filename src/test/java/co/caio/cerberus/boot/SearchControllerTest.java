package co.caio.cerberus.boot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caio.cerberus.boot.FailureResponse.ErrorKind;
import co.caio.cerberus.model.SearchResult;
import co.caio.cerberus.search.Searcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(SearchController.class)
@AutoConfigureWebTestClient
class SearchControllerTest {

  @Autowired WebTestClient testClient;
  @MockBean Searcher searcher;

  private void expectFailure(String uri, HttpStatus status, ErrorKind kind) {
    var result =
        testClient
            .get()
            .uri(uri)
            .exchange()
            .expectStatus()
            .isEqualTo(status)
            .expectBody(FailureResponse.class)
            .returnResult()
            .getResponseBody();

    assertFalse(result.isSuccess());
    assertEquals(kind, result.error);
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
