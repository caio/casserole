package co.caio.loader.converter;

import picocli.CommandLine.ITypeConverter;

public class NonZeroPositiveInt implements ITypeConverter<Integer> {

  @Override
  public Integer convert(String value) throws Exception {

    var number = Integer.parseInt(value);

    if (number < 1) {
      throw new IllegalArgumentException("Number must be >= 1 (Got " + number + ")");
    }

    return number;
  }
}
