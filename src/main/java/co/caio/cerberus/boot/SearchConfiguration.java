package co.caio.cerberus.boot;

import co.caio.cerberus.search.Searcher;
import java.nio.file.Path;
import java.time.Duration;
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
  @NotNull private Duration timeout;

  public void setLocation(String loc) {
    location = loc;
  }

  public void setTimeout(Duration duration) {
    timeout = duration;
  }

  @Bean
  Searcher getSearcher() {
    return new Searcher.Builder().dataDirectory(Path.of(location)).build();
  }

  @Bean("searchTimeout")
  Duration getTimeout() {
    return timeout;
  }
}
