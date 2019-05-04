package co.caio.loader.mixin;

import co.caio.cerberus.model.Recipe;
import co.caio.loader.converter.ExistingReadableFilePath;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import picocli.CommandLine.Option;

public class Source {

  @Option(
      names = {"-s", "--source"},
      description = "Recipes source file. One json-serialized Recipe per line",
      converter = ExistingReadableFilePath.class,
      required = true)
  private Path source;

  private Stream<String> lines() {
    try {
      return Files.lines(source);
    } catch (IOException wrapped) {
      throw new RuntimeException(wrapped);
    }
  }

  public Stream<Recipe> recipes() {
    var mapper = new ObjectMapper();
    mapper.registerModule(new Jdk8Module());

    return lines()
        .map(
            line -> {
              try {
                return mapper.readValue(line, Recipe.class);
              } catch (Exception wrapped) {
                throw new RuntimeException(wrapped);
              }
            });
  }
}
