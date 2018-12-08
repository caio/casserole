package co.caio.cerberus.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import co.caio.cerberus.boot.FailureResponse.ErrorKind;
import co.caio.cerberus.search.Searcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(SearchController.class)
abstract class BaseSearchControllerTest {

  @Autowired WebTestClient testClient;
  @MockBean Searcher searcher;

  void expectFailure(String uri, HttpStatus status, ErrorKind kind) {
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
}
