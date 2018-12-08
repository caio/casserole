package co.caio.cerberus.boot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import co.caio.cerberus.boot.FailureResponse.ErrorKind;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

class SearchControllerTimeoutTest extends BaseSearchControllerTest {

  @TestConfiguration
  static class TestConfig {
    @Bean("searchTimeout")
    public Duration timeout() {
      return Duration.ofMillis(100);
    }
  }

  @Test
  void timedOutRequestReturnsJson() {
    given(searcher.search(any()))
        .will(
            (Answer<Void>)
                invocation -> {
                  try {
                    // Sleep for 2s, which should be interrupted in 100ms
                    Thread.sleep(2000);
                  } catch (InterruptedException expected) {
                    // nothing to do
                  }
                  return null;
                });

    expectFailure(
        "/search?q=this will timeout", HttpStatus.REQUEST_TIMEOUT, ErrorKind.TIMEOUT_ERROR);
  }
}
