package co.caio.loader;

import co.caio.cerberus.db.RecipeMetadata;
import co.caio.cerberus.db.SimpleRecipeMetadataDatabase;
import co.caio.loader.converter.NonExistingPath;
import co.caio.loader.mixin.Source;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;

@Command(name = "database")
public class Database implements Runnable {

  @Mixin private Source source;

  @Parameters(
      index = "0",
      description = "Base directory to store the metadata database",
      converter = NonExistingPath.class)
  private Path destination;

  @Override
  public void run() {

    System.out.println("Creating database at " + destination);

    var writer = new SimpleRecipeMetadataDatabase.Writer(destination);

    Flux.fromStream(source.recipes().map(RecipeMetadata::fromRecipe))
        .buffer(100_000)
        .subscribe(
            recipes -> {
              System.out.println("Writing a batch of " + recipes.size() + " recipes");
              recipes.forEach(writer::addRecipe);
            });

    writer.close();
    System.out.println("Finished creating database");
  }
}
