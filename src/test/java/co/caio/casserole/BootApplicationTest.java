package co.caio.casserole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import co.caio.casserole.SearchParameterParser.SearchParameterException;
import co.caio.cerberus.Util;
import co.caio.cerberus.db.HashMapRecipeMetadataDatabase;
import co.caio.cerberus.db.RecipeMetadata;
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
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
class BootApplicationTest {
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
    ModelView modelView(
        @Qualifier("metadataDb") RecipeMetadataDatabase db, CircuitBreaker breaker) {
      return new ModelView(pageSize(), db, breaker);
    }

    @Bean("metadataDb")
    RecipeMetadataDatabase getMetadataDb() {
      var db = new HashMapRecipeMetadataDatabase();
      db.saveAll(List.of(RecipeMetadata.fromRecipe(Util.getBasicRecipe())));
      return db;
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
            "q=oi", // query length must be > 2
            "q=oil&n=0", // n is >= 1
            "q=oil&n=1.2", // n is not an int
            "q=oil&n=notANumber", // n is >= 1
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
    given(searcher.search(any())).willThrow(SearchParameterException.class);
    // error rate of 100%, but the default ring buffer is of 100 so
    // the circuit should only open after the 100th request
    for (int i = 0; i < 100; i++) {
      assertGet("/search?q=bacon", HttpStatus.BAD_REQUEST);
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

  @Test
  void goActionRedirectsProperly() {
    var recipe = Util.getBasicRecipe();
    var goUri = String.format("/go/%s/%d", recipe.slug(), recipe.recipeId());
    testClient
        .get()
        .uri(goUri)
        .exchange()
        .expectStatus()
        .isPermanentRedirect()
        .expectHeader()
        .valueMatches("Location", recipe.crawlUrl());
  }

  @Test
  void goActionInvalidUriYieldsNotFound() {
    testClient.get().uri("/go/bad-slug/42").exchange().expectStatus().isNotFound();
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

  @Test
  void canFetchGzippedCss() {
    testClient
        .get()
        .uri("/css/main.css")
        .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectHeader()
        .value(HttpHeaders.CONTENT_ENCODING, s -> assertEquals("gzip", s));
  }

  @Test
  void recipeEndpointWorks() {
    var basic = Util.getBasicRecipe();

    var body =
        testClient
            .get()
            .uri("/recipe/" + basic.slug() + "/" + basic.recipeId())
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    var doc = Jsoup.parse(body);
    assertTrue(doc.title().startsWith(basic.name()));
  }

  @Test
  void canFetchStaticPagesProperly() {
    // Can't access the resource with its full name
    assertGet("/page/about.html", HttpStatus.NOT_FOUND);
    // But using the name without ".html" works
    assertGet("/page/about", HttpStatus.OK);
  }

  @Test
  void badRecipeURLYields404() {
    var basic = Util.getBasicRecipe();
    // Make sure the correct uri works
    assertGet("/recipe/" + basic.slug() + "/" + basic.recipeId(), HttpStatus.OK);
    // But wrong slug 404s
    assertGet("/recipe/" + basic.slug() + "wrong" + "/" + basic.recipeId(), HttpStatus.NOT_FOUND);
    // And wrong id 404s
    assertGet("/recipe/" + basic.slug() + "/" + (basic.recipeId() - 1), HttpStatus.NOT_FOUND);
    // Even non-numeric id 404s
    assertGet("/recipe/" + basic.slug() + "/id_that_doesnt_parse_as_number", HttpStatus.NOT_FOUND);
    // And, of course, incomplete uris also 404s
    assertGet("/recipe/" + basic.slug(), HttpStatus.NOT_FOUND);
    assertGet("/recipe/", HttpStatus.NOT_FOUND);
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
