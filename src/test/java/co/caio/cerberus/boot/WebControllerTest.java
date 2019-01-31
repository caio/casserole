package co.caio.cerberus.boot;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import co.caio.cerberus.db.HashMapRecipeMetadataDatabase;
import co.caio.cerberus.db.RecipeMetadataDatabase;
import co.caio.cerberus.model.SearchResult;
import co.caio.cerberus.search.Searcher;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
    ModelView modelView(@Qualifier("metadataDb") RecipeMetadataDatabase db) {
      return new ModelView(pageSize(), db);
    }

    @Bean("metadataDb")
    RecipeMetadataDatabase getMetadataDb() {
      return new HashMapRecipeMetadataDatabase();
    }

    @Bean
    SearchConfigurationProperties conf() {
      return new SearchConfigurationProperties();
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

  @Test
  void indexPageRendersNormally() {
    var doc = parseIndexBody();

    // There are no warning messages
    assertNull(doc.select("div.hero-body div[class*='notification is-warning']").first());
    // And the search controls are NOT disabled
    assertNull(doc.select("form input[disabled]").first());
    assertNull(doc.select("form button[disabled]").first());
  }

  @Test
  void indexPageRendersWarningWhenCircuitBreakerIsOpen() {
    breaker.transitionToOpenState();

    var doc = parseIndexBody();

    // The warning is displayed
    assertNotNull(doc.select("div.hero-body div[class*='notification is-warning']").first());
    // And the search controls are disabled
    assertNotNull(doc.select("form input[disabled]").first());
    assertNotNull(doc.select("form button[disabled]").first());
  }

  private Document parseIndexBody() {
    var body =
        testClient
            .get()
            .uri("/")
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    assertNotNull(body);
    return Jsoup.parse(body);
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
