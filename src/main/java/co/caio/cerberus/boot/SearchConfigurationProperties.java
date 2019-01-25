package co.caio.cerberus.boot;

import java.time.Duration;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cerberus.search")
public class SearchConfigurationProperties {

  @NotNull @NotBlank private String location;
  @NotNull private Duration timeout;
  @NotNull @Positive private int pageSize;

  public String getLocation() {
    return location;
  }

  public void setLocation(String loc) {
    location = loc;
  }

  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(Duration duration) {
    timeout = duration;
  }

  public int getPageSize() {
    return pageSize;
  }

  public void setPageSize(int size) {
    pageSize = size;
  }
}
