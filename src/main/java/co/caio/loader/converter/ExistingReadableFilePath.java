package co.caio.loader.converter;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import picocli.CommandLine.ITypeConverter;

public class ExistingReadableFilePath implements ITypeConverter<Path> {

  @Override
  public Path convert(String value) throws Exception {
    var path = Path.of(value);
    var file = path.toFile();

    if (!file.exists()) {
      throw new InvalidPathException(value, "File not found");
    }

    if (file.isDirectory()) {
      throw new InvalidPathException(value, "Must be a regular file, not a directory");
    }

    if (!file.canRead()) {
      throw new InvalidPathException(value, "Unreadable file");
    }

    return path;
  }
}
