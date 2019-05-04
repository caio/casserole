package co.caio.loader;

import co.caio.cerberus.db.ChronicleRecipeMetadataDatabase;
import co.caio.cerberus.db.FlatBufferSerializer;
import co.caio.cerberus.db.RecipeMetadata;
import co.caio.loader.converter.NonExistingPath;
import co.caio.loader.mixin.Source;
import java.nio.file.Path;
import java.util.LongSummaryStatistics;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;

@Command(name = "database")
public class Database implements Runnable {

  @Mixin private Source source;

  @Parameters(
      index = "0",
      description = "File to store the database at",
      converter = NonExistingPath.class)
  private Path destination;

  private LongSummaryStatistics computeStats() {
    return source
        .recipes()
        .map(FlatBufferSerializer.INSTANCE::flattenRecipe)
        .collect(Collectors.summarizingLong(bb -> bb.limit() - bb.position()));
  }

  @Override
  public void run() {

    System.out.println("Computing ChronicleMap creation parameters");
    var stats = computeStats();
    System.err.println(stats);

    System.out.println("Creating database file as " + destination);
    var db =
        ChronicleRecipeMetadataDatabase.create(destination, stats.getAverage(), stats.getCount());

    Flux.fromStream(source.recipes().map(RecipeMetadata::fromRecipe))
        .buffer(100_000)
        .subscribe(
            recipes -> {
              System.out.println("Writing a batch of " + recipes.size() + " recipes");
              db.saveAll(recipes);
            });

    db.close();
    System.out.println("Finished creating database file");
  }
}
