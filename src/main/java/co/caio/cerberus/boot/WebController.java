package co.caio.cerberus.boot;

import co.caio.cerberus.boot.SearchParameterParser.SearchParameterException;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.search.Searcher;
import io.micrometer.core.annotation.Timed;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Controller
public class WebController {

  private final Searcher searcher;
  private final Duration timeout;
  private final SearchParameterParser parser;

  public WebController(Searcher searcher, @Qualifier("searchTimeout") Duration timeout) {
    this.searcher = searcher;
    this.timeout = timeout;
    this.parser = new SearchParameterParser();
  }

  @GetMapping("/")
  @ResponseBody
  public String index() {
    return views.index.template("Cerberus").render().toString();
  }

  @GetMapping("/search")
  @Timed
  @ResponseBody
  public Mono<String> search(@RequestParam Map<String, String> params) {
    SearchQuery query = parser.buildQuery(params);

    return Mono.fromCallable(
            () -> {
              // System.out.println(Thread.currentThread().getName());
              return views.search.template(searcher.search(query)).render().toString();
            })
        .subscribeOn(Schedulers.parallel())
        .timeout(timeout);
  }

  @ExceptionHandler({
    IllegalStateException.class,
    ServerWebInputException.class,
    SearchParameterException.class
  })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  ResponseEntity<String> handleBadParameters(Exception ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.TEXT_HTML)
        .body(views.index.template("Bad parameter: " + ex.getMessage()).render().toString());
  }

  @ExceptionHandler
  @ResponseBody
  ResponseEntity<String> handleTimeout(TimeoutException ex) {
    return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
        .contentType(MediaType.TEXT_HTML)
        .body(views.index.template("Timeout: " + ex.getMessage()).render().toString());
  }

  @ExceptionHandler
  @ResponseBody
  ResponseEntity<String> handleUnknown(Exception ex) {
    var msg = ex.getMessage();
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.TEXT_HTML)
        .body(views.index.template(msg != null ? msg : ex.toString()).render().toString());
  }
}
