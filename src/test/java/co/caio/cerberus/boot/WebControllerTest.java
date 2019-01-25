package co.caio.cerberus.boot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import co.caio.cerberus.db.RecipeMetadataDatabase;
import co.caio.cerberus.model.SearchResult;
import co.caio.cerberus.search.Searcher;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.mustache.MustacheAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(WebController.class)
@Import(MustacheAutoConfiguration.class)
class WebControllerTest {

  @Autowired WebTestClient testClient;
  @Autowired CircuitBreaker breaker;

  @MockBean Searcher searcher;

  @TestConfiguration
  static class TestConfig {
    @Bean("searchTimeout")
    Duration getTimeout() {
      return Duration.ofMillis(100);
    }

    @Bean
    @Primary
    CircuitBreaker getCircuitBreaker() {
      return CircuitBreaker.ofDefaults("test");
    }

    @Bean("searchPageSize")
    int pageSize() {
      return 10;
    }

    @Bean
    SearchParameterParser parser() {
      return new SearchParameterParser(pageSize());
    }

    @Bean
    Renderer renderer() throws Exception {
      var tmp = Files.createTempDirectory("renderer");
      var db = RecipeMetadataDatabase.Builder.open(tmp, 42, false);
      return new Renderer(pageSize(), db);
    }
  }

  @BeforeEach
  void resetCircuitBreakerState() {
    breaker.reset();
  }

  @Test
  void badInputTriggersError400() {
    var badQueries =
        List.of(
            "q=oi", // too short
            "q=oil&n=0", // n is >= 1
            "q=oil&n=1.2", // n is not an int
            "q=oil&n=notANumber", // n is >= 1
            "q=oil&n=1000", // too big
            "q=oil&nf=-1", // negative number
            "q=oil&sort=random", // invalid sort order
            "q=oil&ni=2,1", // invalid range
            "q=oil&trololo=hue" // unknown parameter
            );

    for (String badQuery : badQueries) {
      assertGet("/search?" + badQuery, HttpStatus.BAD_REQUEST);
    }
  }

  @Test
  void circuitOpensAfterManyErrors() {
    given(searcher.search(any())).willThrow(RuntimeException.class);
    // error rate of 100%, but the default ring buffer is of 100 so
    // the circuit should only open after the 100th request
    for (int i = 0; i < 100; i++) {
      assertGet("/search?q=bacon", HttpStatus.INTERNAL_SERVER_ERROR);
    }
    assertGet("/search?q=bacon", HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void unknownExceptionTriggersError500() {
    given(searcher.search(any())).willThrow(RuntimeException.class);
    assertGet("/search?q=potato", HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  void timeoutTriggersError408() {
    given(searcher.search(any()))
        .will(
            (Answer<SearchResult>)
                invocation -> {
                  try {
                    // Sleep for 2s, which should be interrupted in 100ms
                    Thread.sleep(2000);
                  } catch (InterruptedException expected) {
                    // nothing to do
                  }
                  return new SearchResult.Builder().build();
                });
    assertGet("/search?q=salt", HttpStatus.REQUEST_TIMEOUT);
  }

  void assertGet(String uri, HttpStatus status) {
    testClient
        .get()
        .uri(uri)
        .exchange()
        .expectStatus()
        .isEqualTo(status)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.TEXT_HTML)
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }
}
