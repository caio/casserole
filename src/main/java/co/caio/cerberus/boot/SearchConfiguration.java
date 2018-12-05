package co.caio.cerberus.boot;

import co.caio.cerberus.search.Searcher;
import java.nio.file.Path;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "cerberus.search")
public class SearchConfiguration {

  @NotNull @NotBlank private String location;

  public void setLocation(String loc) {
    location = loc;
  }

  @Bean
  public Searcher getSearcher() {
    return new Searcher.Builder().dataDirectory(Path.of(location)).build();
  }
}
