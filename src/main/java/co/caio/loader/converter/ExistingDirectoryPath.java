package co.caio.loader.converter;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import picocli.CommandLine.ITypeConverter;

public class ExistingDirectoryPath implements ITypeConverter<Path> {

  @Override
  public Path convert(String value) throws Exception {
    var path = Path.of(value);
    var file = path.toFile();

    if (!file.exists()) {
      throw new InvalidPathException(value, "Path doesn't exist");
    }

    if (!file.isDirectory()) {
      throw new InvalidPathException(value, "Not a directory");
    }

    return path;
  }
}
