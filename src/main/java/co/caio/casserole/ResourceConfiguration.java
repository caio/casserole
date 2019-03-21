package co.caio.casserole;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.resource.AbstractResourceResolver;
import org.springframework.web.reactive.resource.EncodedResourceResolver;
import org.springframework.web.reactive.resource.PathResourceResolver;
import org.springframework.web.reactive.resource.ResourceResolverChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class ResourceConfiguration implements WebFluxConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/css/*.css")
        .addResourceLocations("classpath:/tablier/css/")
        .resourceChain(true)
        .addResolver(new EncodedResourceResolver())
        .addResolver(new PathResourceResolver());

    registry
        .addResourceHandler("/page/*")
        .addResourceLocations("classpath:/tablier/pages/")
        .setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
        .resourceChain(true)
        .addResolver(new EncodedResourceResolver())
        .addResolver(new StaticHtmlResolver());

    registry
        .addResourceHandler("/img/*")
        .addResourceLocations("classpath:/tablier/img/")
        .resourceChain(true)
        .addResolver(new PathResourceResolver());
  }

  @Override
  public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
    configurer.customCodecs().writer(new RockerModelHttpMessageWriter());
  }

  static class StaticHtmlResolver extends AbstractResourceResolver {

    StaticHtmlResolver() {}

    @Override
    protected Mono<Resource> resolveResourceInternal(
        ServerWebExchange exchange,
        String requestPath,
        List<? extends Resource> locations,
        ResourceResolverChain chain) {
      return chain.resolveResource(exchange, requestPath + ".html", locations);
    }

    @Override
    protected Mono<String> resolveUrlPathInternal(
        String resourceUrlPath, List<? extends Resource> locations, ResourceResolverChain chain) {
      return chain.resolveUrlPath(resourceUrlPath, locations);
    }
  }
}
