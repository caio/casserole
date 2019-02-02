package co.caio.cerberus.boot;

import com.fizzed.rocker.RockerModel;
import com.fizzed.rocker.runtime.ArrayOfByteArraysOutput;
import java.util.List;
import java.util.Map;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.HttpMessageWriter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RockerModelHttpMessageWriter implements HttpMessageWriter<RockerModel> {

  private static final List<MediaType> supportedMediaTypes = List.of(MediaType.TEXT_HTML);
  private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();

  @Override
  public List<MediaType> getWritableMediaTypes() {
    return supportedMediaTypes;
  }

  @Override
  public boolean canWrite(ResolvableType elementType, MediaType mediaType) {
    return RockerModel.class.isAssignableFrom(elementType.toClass());
  }

  @Override
  public Mono<Void> write(
      Publisher<? extends RockerModel> inputStream,
      ResolvableType elementType,
      MediaType mediaType,
      ReactiveHttpOutputMessage message,
      Map<String, Object> hints) {

    return Mono.from(inputStream)
        .flatMap(
            rockerModel -> {
              var allBytes = rockerModel.render(ArrayOfByteArraysOutput.FACTORY).getArrays();
              return message.writeWith(Flux.fromIterable(allBytes).map(bufferFactory::wrap));
            });
  }
}
