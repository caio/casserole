package co.caio.casserole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import co.caio.casserole.SearchParameterParser.SearchParameterException;
import co.caio.casserole.TermQueryRewritingPolicy.PolicyException;
import co.caio.cerberus.db.RecipeMetadata;
import co.caio.cerberus.model.Recipe;
import co.caio.cerberus.model.SearchResult;
import co.caio.cerberus.search.Searcher;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
class BootApplicationTest {
  @Autowired WebTestClient testClient;
  @Autowired CircuitBreaker breaker;

  @MockBean RecipeMetadataService metadataService;
  @MockBean Searcher searcher;

  @TestConfiguration
  static class TestConfig {

    @Bean("numRecipes")
    int numRecipes() {
      return 1;
    }

    @Bean
    Duration searchTimeout() {
      return Duration.ofMillis(100);
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
    var recipe = getBasicRecipe();

    given(metadataService.findById(recipe.recipeId()))
        .willReturn(Optional.of(RecipeMetadata.fromRecipe(recipe)));

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
    var basic = getBasicRecipe();
    given(metadataService.findById(basic.recipeId()))
        .willReturn(Optional.of(RecipeMetadata.fromRecipe(basic)));

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
  void staticPageHasMaxAgeHeader() {
    testClient
        .get()
        .uri("/page/help")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.OK)
        .expectHeader()
        .valueEquals("Cache-Control", "max-age=3600");
  }

  @Test
  void canFetchFavicon() {
    assertGet("/img/favicon.ico", HttpStatus.OK, new MediaType("image", "x-icon"));
  }

  @Test
  void badRecipeURLYields404() {
    var basic = getBasicRecipe();
    given(metadataService.findById(basic.recipeId()))
        .willReturn(Optional.of(RecipeMetadata.fromRecipe(basic)));
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

  @Test
  void handlePolicyExceptionCorrectly() {
    given(searcher.search(any())).willThrow(PolicyException.class);
    assertGet("/search?until+-cup",HttpStatus.BAD_REQUEST);
  }

  void assertGet(String uri, HttpStatus status, MediaType contentType) {
    testClient
        .get()
        .uri(uri)
        .exchange()
        .expectStatus()
        .isEqualTo(status)
        .expectHeader()
        .contentTypeCompatibleWith(contentType)
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  void assertGet(String uri, HttpStatus status) {
    assertGet(uri, status, MediaType.TEXT_HTML);
  }

  void assertHead(String uri, HttpStatus status) {
    testClient.head().uri(uri).exchange().expectStatus().isEqualTo(status);
  }

  @Test
  void indexHeadRequestWorks() {
    assertHead("/", HttpStatus.OK);
  }

  @Test
  void recipeHeadRequestWorks() {
    var basic = getBasicRecipe();
    var validUri = "/recipe/" + basic.slug() + "/" + basic.recipeId();

    given(metadataService.findById(basic.recipeId()))
        .willReturn(Optional.of(RecipeMetadata.fromRecipe(basic)));

    assertHead("/recipe/", HttpStatus.NOT_FOUND);
    assertHead("/recipe/wrongslug", HttpStatus.NOT_FOUND);
    assertHead("/recipe/wrongslug/wrongid", HttpStatus.NOT_FOUND);
    assertHead("/recipe/wrongslug/wrongid", HttpStatus.NOT_FOUND);
    assertHead("/recipe/" + basic.slug() + "/wrongid", HttpStatus.NOT_FOUND);
    assertHead("/recipe/wrongslug/" + basic.recipeId(), HttpStatus.NOT_FOUND);

    assertHead(validUri, HttpStatus.OK);
  }

  @Test
  void searchHeadWorksWithValidParams() {
    assertHead("/search", HttpStatus.BAD_REQUEST);
    assertHead("/search?q=no", HttpStatus.BAD_REQUEST);
    assertHead("/search?q=banana", HttpStatus.OK);
  }

  static Recipe getBasicRecipe() {
    return new Recipe.Builder()
        .recipeId(1)
        .name("name")
        .siteName("site")
        .slug("slug")
        .crawlUrl("url")
        .addIngredients("egg")
        .addInstructions("eat")
        .build();
  }
}
