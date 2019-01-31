package co.caio.cerberus.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.caio.cerberus.Util;
import co.caio.cerberus.db.HashMapRecipeMetadataDatabase;
import co.caio.cerberus.db.RecipeMetadataDatabase;
import co.caio.cerberus.search.Searcher;
import com.samskivert.mustache.Mustache;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest
class BootApplicationTests {

  @Autowired Mustache.Compiler compiler;

  @TestConfiguration
  static class SampleSettings {
    @Bean
    Searcher getSearcher() {
      return Util.getTestIndexer().buildSearcher();
    }

    @Bean("metadataDb")
    RecipeMetadataDatabase getMetadataDb() {
      return new HashMapRecipeMetadataDatabase();
    }
  }

  @Test
  void contextLoads() {}

  @Test
  void mustacheSettings() {
    checkRendering("", OptionalInt.empty());
    checkRendering("42", OptionalInt.of(42));
  }

  private void checkRendering(
      String wanted, @SuppressWarnings("OptionalUsedAsFieldOrParameterType") OptionalInt item) {
    var template = "{{#opt}}{{.}}{{/opt}}";
    var result = compiler.compile(template).execute(Map.of("opt", item));
    assertEquals(wanted, result);
  }
}
