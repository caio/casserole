package co.caio.casserole.config;

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
  @NotNull @Positive int cacheSize;

  LuceneConfigurationProperties lucene;

  SdbConfigurationProperties sdb;

  public Duration getTimeout() {
    return timeout;
  }

  public int getPageSize() {
    return pageSize;
  }

  public int getCacheSize() {
    return cacheSize;
  }

  public SdbConfigurationProperties getSdb() {
    return sdb;
  }

  public void setSdb(SdbConfigurationProperties sdb) {
    this.sdb = sdb;
  }

  public LuceneConfigurationProperties getLucene() {
    return lucene;
  }

  public void setLucene(LuceneConfigurationProperties lucene) {
    this.lucene = lucene;
  }

  public void setCacheSize(int cacheSize) {
    this.cacheSize = cacheSize;
  }

  public void setTimeout(Duration duration) {
    timeout = duration;
  }

  public void setPageSize(int size) {
    pageSize = size;
  }

  public static class LuceneConfigurationProperties {

    public void setDirectory(Path directory) {
      this.directory = directory;
    }

    public Path getDirectory() {
      return directory;
    }

    @NotNull Path directory;
  }

  public static class SdbConfigurationProperties {

    public void setDirectory(Path directory) {
      this.directory = directory;
    }

    public Path getDirectory() {
      return directory;
    }

    @NotNull Path directory;
  }
}
