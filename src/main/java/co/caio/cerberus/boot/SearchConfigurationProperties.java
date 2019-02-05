package co.caio.cerberus.boot;

import java.nio.file.Path;
import java.time.Duration;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:cerberus.properties")
@ConfigurationProperties(prefix = "cerberus")
public class SearchConfigurationProperties {

  @NotNull Duration timeout;
  @NotNull @Positive int pageSize;

  LuceneConfigurationProperties lucene;
  ChronicleConfigurationProperties chronicle;

  public void setLucene(LuceneConfigurationProperties lucene) {
    this.lucene = lucene;
  }

  public void setChronicle(ChronicleConfigurationProperties chronicle) {
    this.chronicle = chronicle;
  }

  public void setTimeout(Duration duration) {
    timeout = duration;
  }

  public void setPageSize(int size) {
    pageSize = size;
  }

  public static class ChronicleConfigurationProperties {

    public void setFilename(Path filename) {
      this.filename = filename;
    }

    Path filename;
  }

  public static class LuceneConfigurationProperties {

    public void setDirectory(Path directory) {
      this.directory = directory;
    }

    @NotNull Path directory;
  }
}
