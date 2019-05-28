package co.caio.loader;

import co.caio.casserole.index.Facet;
import co.caio.cerberus.search.Indexer;
import co.caio.loader.converter.NonExistingPath;
import co.caio.loader.mixin.Source;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Command(name = "lucene")
public class Lucene implements Runnable {

  @Mixin private Source source;

  @Parameters(
      index = "0",
      description = "Base directory to store the index",
      converter = NonExistingPath.class)
  private Path destination;

  @Override
  public void run() {

    Path target;
    try {
      target = Files.createDirectories(destination);
    } catch (IOException wrapped) {
      throw new RuntimeException(wrapped);
    }

    System.out.println("Initializing index at " + target);
    var indexer = Indexer.Factory.open(target, new Facet().getCategoryExtractor());

    System.out.println("Ingesting all recipes. This will take a while...");

    Flux.fromStream(source.recipes())
        .parallel()
        .runOn(Schedulers.parallel())
        .doOnNext(
            recipe -> {
              try {
                indexer.addRecipe(recipe);
              } catch (IOException rethrown) {
                throw new RuntimeException(rethrown);
              }
            })
        .sequential()
        .blockLast();

    try {
      System.out.println("Committing changes to disk");
      indexer.commit();
      System.out.println("Optimizing index for read-only usage");
      indexer.mergeSegments();
      indexer.close();
    } catch (IOException rethrown) {
      throw new RuntimeException(rethrown);
    }

    System.out.println("Finished creating lucene index");
  }
}
