package co.caio.loader.converter;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import picocli.CommandLine.ITypeConverter;

public class NonExistingPath implements ITypeConverter<Path> {

  @Override
  public Path convert(String value) throws Exception {
    var path = Path.of(value);

    if (path.toFile().exists()) {
      throw new InvalidPathException(value, "Path exists");
    }

    return path;
  }
}
