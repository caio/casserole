package co.caio.loader;

import co.caio.cerberus.model.Recipe;
import co.caio.cerberus.search.Searcher;
import co.caio.loader.converter.ExistingDirectoryPath;
import co.caio.loader.converter.NonZeroPositiveInt;
import co.caio.loader.mixin.Source;
import java.nio.file.Path;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Command(name = "similarity")
public class Similarity implements Runnable {

  @Mixin private Source source;

  @Option(
      names = {"-l", "--lucene"},
      description = "Path to existing lucene index (built via the `lucene` command)",
      converter = ExistingDirectoryPath.class,
      required = true)
  private Path lucene;

  @Option(
      names = {"-n"},
      description = "Number of similarities to fetch per recipe",
      converter = NonZeroPositiveInt.class,
      defaultValue = "20")
  private int numSimilarities;

  @Override
  public void run() {

    var searcher = new Searcher.Builder().dataDirectory(lucene).build();

    var total = new AtomicInteger(0);
    var missing = new AtomicInteger(0);

    System.err.println("Started!");

    Flux.fromStream(source.recipes())
        .parallel()
        .runOn(Schedulers.parallel())
        .doOnNext(
            recipe -> {
              var result = searcher.findSimilar(recipeAsText(recipe), numSimilarities);

              if (total.addAndGet(1) % 10000 == 0) {
                dumpStats(total, missing);
              }

              if (result.recipeIds().isEmpty()) {
                missing.addAndGet(1);
                return;
              }

              var joiner = new StringJoiner(",");
              joiner.add(Long.toString(recipe.recipeId()));
              result.recipeIds().stream().map(Object::toString).forEach(joiner::add);

              System.out.println(joiner.toString());
            })
        .sequential()
        .blockLast();

    dumpStats(total, missing);
    System.err.println("Done!");
  }

  private void dumpStats(AtomicInteger total, AtomicInteger missing) {
    System.err.println(
        "Processed " + total.get() + " recipes. " + missing.get() + " without similarities");
  }

  private String recipeAsText(Recipe recipe) {
    return String.join(
        "\n",
        recipe.name(),
        String.join("\n", recipe.ingredients()),
        String.join("\n", recipe.instructions()));
  }
}
