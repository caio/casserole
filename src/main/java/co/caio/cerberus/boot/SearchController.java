package co.caio.cerberus.boot;

import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchQuery.SortOrder;
import co.caio.cerberus.search.Searcher;
import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolationException;
import javax.validation.constraints.Size;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class SearchController {

  private final Searcher searcher;

  public SearchController(Searcher injectedSearcher) {
    searcher = injectedSearcher;
  }

  @GetMapping("search")
  public SuccessResponse search(
      @RequestParam("q")
          @Size(
              min = 3,
              max = 1000,
              message = "Query must be of at least two characters (max 1000)")
          String fulltext,
      @RequestParam(value = "n", defaultValue = "10") int maxResults,
      @RequestParam(value = "sort", required = false) SortOrder order) {
    var builder = new SearchQuery.Builder();

    if (fulltext != null) builder.fulltext(fulltext);
    if (order != null) builder.sort(order);

    builder.maxResults(maxResults);

    return new SuccessResponse(searcher.search(builder.build()));
  }

  @ExceptionHandler({
    IllegalStateException.class,
    ConversionFailedException.class,
    ConstraintViolationException.class,
    ServletRequestBindingException.class
  })
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  @ResponseBody
  FailureResponse handleQueryBuildError(HttpServletRequest req, Exception exc) {
    return FailureResponse.queryError(req.getRequestURI(), exc.getMessage());
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ResponseBody
  FailureResponse handleException(HttpServletRequest req, Exception exc) {
    return FailureResponse.unknownError(req.getRequestURI(), exc.getMessage());
  }
}
