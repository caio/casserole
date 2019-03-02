package co.caio.cerberus.boot;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.trace.http.HttpTrace;
import org.springframework.boot.actuate.trace.http.HttpTraceRepository;
import org.springframework.boot.actuate.trace.http.InMemoryHttpTraceRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

@Configuration
public class AccessLogConfiguration {

  @Bean
  public HttpTraceRepository httpTraceRepository() {
    return new RecipeClickHttpTraceRepository(new InMemoryHttpTraceRepository());
  }

  static class RecipeClickHttpTraceRepository implements HttpTraceRepository {
    private static final Logger logger = LoggerFactory.getLogger("cerberus.click");

    private HttpTraceRepository delegate;

    RecipeClickHttpTraceRepository(HttpTraceRepository delegate) {
      this.delegate = delegate;
    }

    @Override
    public List<HttpTrace> findAll() {
      return delegate.findAll();
    }

    @Override
    public void add(HttpTrace trace) {
      if (logger.isDebugEnabled()) {
        try {
          doLog(trace);
        } catch (Exception e) {
          logger.error("Failed to log trace " + trace, e);
        }
      }
      delegate.add(trace);
    }

    private void doLog(HttpTrace trace) {
      var request = trace.getRequest();
      var uri = request.getUri();
      var rawPath = uri.getRawPath();
      if (trace.getResponse().getStatus() == HttpStatus.PERMANENT_REDIRECT.value()
          && rawPath.startsWith("/go/")) {
        logger.debug(
            String.format(
                "%d\t%s\t%s\t%s",
                trace.getTimestamp().getEpochSecond(),
                request.getRemoteAddress(),
                rawPath,
                uri.getRawQuery()));
      }
    }
  }
}
