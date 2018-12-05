package co.caio.cerberus.boot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caio.cerberus.model.SearchResult;
import co.caio.cerberus.search.Searcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

  @Autowired private MockMvc mvc;

  @MockBean Searcher searcher;

  @Test
  void badInputReturnsJson() throws Exception {
    // too short of a query
    mvc.perform(get("/search?q=oi"))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(Boolean.FALSE))
        .andExpect(jsonPath("$.path").value("/search"))
        .andExpect(jsonPath("$.error").value("QUERY_ERROR"));

    // too big of an n
    mvc.perform(get("/search?q=oil&n=200"))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(Boolean.FALSE))
        .andExpect(jsonPath("$.path").value("/search"))
        .andExpect(jsonPath("$.error").value("QUERY_ERROR"));

    // empty query
    mvc.perform(get("/search"))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(Boolean.FALSE))
        .andExpect(jsonPath("$.path").value("/search"))
        .andExpect(jsonPath("$.error").value("QUERY_ERROR"));
  }

  @Test
  void apiReturnsJsonOnInternalError() throws Exception {
    given(searcher.search(any())).willThrow(RuntimeException.class);

    mvc.perform(get("/search?q=salt"))
        .andExpect(status().is5xxServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(Boolean.FALSE))
        .andExpect(jsonPath("$.path").value("/search"))
        .andExpect(jsonPath("$.error").value("UNKNOWN_ERROR"));
  }

  @Test
  void apiReturnsJsonOnSuccess() throws Exception {
    var searchResult =
        new SearchResult.Builder()
            .addRecipe(42, "the answer", "https://nowhere.here")
            .totalHits(100)
            .build();

    given(searcher.search(any())).willReturn(searchResult);

    mvc.perform(get("/search?q=keto"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(Boolean.TRUE))
        .andExpect(jsonPath("$.error").doesNotExist())
        .andExpect(jsonPath("$.result").exists())
        .andExpect(jsonPath("$.result.totalHits").value(100))
        .andExpect(jsonPath("$.result.recipes").isArray())
        .andExpect(jsonPath("$.result.recipes[0].recipeId").value(42));
  }
}
