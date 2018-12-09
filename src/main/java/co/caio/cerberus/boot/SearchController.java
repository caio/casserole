package co.caio.cerberus.boot;

import co.caio.cerberus.boot.SearchParameterParser.SearchParameterException;
import co.caio.cerberus.search.Searcher;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javax.validation.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
public class SearchController {

  private final Searcher searcher;
  private final Duration timeout;
  private final SearchParameterParser parser;

  public SearchController(Searcher searcher, @Qualifier("searchTimeout") Duration timeout) {
    this.searcher = searcher;
    this.timeout = timeout;
    this.parser = new SearchParameterParser();
  }

  @GetMapping("search")
  public Mono<SuccessResponse> search(@RequestParam Map<String, String> params) {
    var query = parser.buildQuery(params);

    return Mono.fromCallable(() -> new SuccessResponse(searcher.search(query)))
        .publishOn(Schedulers.parallel())
        .timeout(timeout);
  }

  @ExceptionHandler({
    IllegalStateException.class,
    ServerWebInputException.class,
    ConstraintViolationException.class,
    SearchParameterException.class,
  })
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  @ResponseBody
  FailureResponse handleQueryBuildError(Exception exc) {
    return FailureResponse.queryError(exc.getMessage());
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.REQUEST_TIMEOUT)
  @ResponseBody
  Mono<FailureResponse> handleTimeout(TimeoutException exc) {
    return Mono.just(FailureResponse.timeoutError(exc.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ResponseBody
  FailureResponse handleException(Exception exc) {
    return FailureResponse.unknownError(exc.getMessage());
  }
}
