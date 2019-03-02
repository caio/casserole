package co.caio.cerberus.boot;

import static org.junit.jupiter.api.Assertions.*;

import co.caio.cerberus.Util;
import co.caio.cerberus.boot.SearchParameterParser.SearchParameterException;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.search.Searcher;
import org.junit.jupiter.api.Test;

class NoMatchAllDocsSearchPolicyTest {

  @Test
  void matchAllDocsThrows() {
    var searcher =
        new Searcher.Builder()
            .dataDirectory(Util.getTestDataDir())
            .searchPolicy(new NoMatchAllDocsSearchPolicy())
            .build();

    assertThrows(
        SearchParameterException.class,
        () -> searcher.search(new SearchQuery.Builder().fulltext("*").build()));
  }
}
