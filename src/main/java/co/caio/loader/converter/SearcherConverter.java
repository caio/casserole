package co.caio.loader.converter;

import co.caio.cerberus.search.Searcher;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import picocli.CommandLine.ITypeConverter;

public class SearcherConverter implements ITypeConverter<Searcher> {

  @Override
  public Searcher convert(String value) throws Exception {
    var baseDir = Path.of(value);

    if (!baseDir.toFile().isDirectory()) {
      throw new InvalidPathException(value, "Not a directory");
    }

    return Searcher.Factory.open(baseDir);
  }
}
