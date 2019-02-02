package co.caio.cerberus.boot;

import co.caio.cerberus.Util;
import co.caio.cerberus.db.HashMapRecipeMetadataDatabase;
import co.caio.cerberus.db.RecipeMetadataDatabase;
import co.caio.cerberus.search.Searcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest
class BootApplicationTests {

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
}
