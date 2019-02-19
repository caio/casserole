package co.caio.cerberus.boot;

import co.caio.cerberus.boot.ModelView.OverPaginationError;
import co.caio.cerberus.boot.ModelView.RecipeNotFoundError;
import co.caio.cerberus.boot.SearchParameterParser.SearchParameterException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerOpenException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.ResourceProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class ExceptionHandler extends AbstractErrorWebExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger("cerberus.exception");

  private final ModelView modelView;

  public ExceptionHandler(
      ErrorAttributes errorAttributes,
      ApplicationContext applicationContext,
      ServerCodecConfigurer serverCodecConfigurer,
      ModelView modelView) {
    super(errorAttributes, new ResourceProperties(), applicationContext);
    super.setMessageWriters(serverCodecConfigurer.getWriters());
    super.setMessageReaders(serverCodecConfigurer.getReaders());
    this.modelView = modelView;
  }

  @Override
  protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
    return RouterFunctions.route(RequestPredicates.all(), this::renderError);
  }

  private Mono<ServerResponse> renderError(ServerRequest request) {

    var exception = getError(request);

    if (exception instanceof ResponseStatusException) {
      return handleResponseStatusException((ResponseStatusException) exception);
    }

    var spec = errorSpecMap.get(exception.getClass());

    if (spec == null) {
      logger.error("Exception caught handling request", exception);
      spec = DEFAULT_ERROR_SPEC;
    }

    return ServerResponse.status(spec.status)
        .body(
            BodyInserters.fromObject(
                modelView.renderError(
                    spec.getTitle(), spec.getMessage().orElse(exception.getMessage()))));
  }

  private Mono<ServerResponse> handleResponseStatusException(ResponseStatusException ex) {
    var status = ex.getStatus();

    if (status.is5xxServerError()) {
      logger.error("Exception caught handling request", ex);
    }

    var reason = ex.getReason();
    return ServerResponse.status(status)
        .body(
            BodyInserters.fromObject(
                modelView.renderError(
                    status.getReasonPhrase(), reason == null ? ex.getMessage() : reason)));
  }

  private static final ErrorSpec DEFAULT_ERROR_SPEC =
      new ErrorSpec(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Unknown Error",
          "An unexpected error has occurred and has been logged, please try again");

  private static final ErrorSpec UNKNOWN_PARAMETER =
      new ErrorSpec(HttpStatus.BAD_REQUEST, "Invalid/Unknown Parameter");

  private static final Map<Class, ErrorSpec> errorSpecMap =
      Map.of(
          IllegalStateException.class,
          UNKNOWN_PARAMETER,
          ServerWebInputException.class,
          UNKNOWN_PARAMETER,
          SearchParameterException.class,
          UNKNOWN_PARAMETER,
          OverPaginationError.class,
          new ErrorSpec(HttpStatus.BAD_REQUEST, "Invalid Page Number"),
          RecipeNotFoundError.class,
          new ErrorSpec(
              HttpStatus.NOT_FOUND, "Recipe Not Found", "The provided URL is likely incorrect."),
          TimeoutException.class,
          new ErrorSpec(
              HttpStatus.REQUEST_TIMEOUT,
              "Timeout Error",
              "We're likely overloaded, please try again in a few minutes"),
          CircuitBreakerOpenException.class,
          new ErrorSpec(
              HttpStatus.SERVICE_UNAVAILABLE,
              "Service Unavailable",
              "The site is experiencing an abnormal rate of errors, it might be a while before we're back at full speed"));

  static class ErrorSpec {
    private final HttpStatus status;
    private final String title;
    private final String message;

    ErrorSpec(HttpStatus status, String title) {
      this(status, title, null);
    }

    ErrorSpec(HttpStatus status, String title, String message) {
      this.status = status;
      this.title = title;
      this.message = message;
    }

    HttpStatus getStatus() {
      return status;
    }

    String getTitle() {
      return title;
    }

    Optional<String> getMessage() {
      return Optional.ofNullable(message);
    }
  }
}
