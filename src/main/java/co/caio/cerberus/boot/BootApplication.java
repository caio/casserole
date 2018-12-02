package co.caio.cerberus.boot;

import co.caio.cerberus.search.Searcher;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BootApplication {

  @Value("${cerberus.service.data_dir}")
  private String dataDirectory;

  @Bean
  public Searcher searcher() {
    return new Searcher.Builder().dataDirectory(Path.of(dataDirectory)).build();
  }

  public static void main(String[] args) {
    SpringApplication.run(BootApplication.class, args);
  }
}
