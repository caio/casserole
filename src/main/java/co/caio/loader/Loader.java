package co.caio.loader;

import co.caio.casserole.IndexFacet;
import co.caio.cerberus.db.ChronicleRecipeMetadataDatabase;
import co.caio.cerberus.db.FlatBufferSerializer;
import co.caio.cerberus.db.RecipeMetadata;
import co.caio.cerberus.model.Recipe;
import co.caio.cerberus.search.Indexer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LongSummaryStatistics;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Command(name = "loader", mixinStandardHelpOptions = true, version = "0.0.1")
public class Loader implements Runnable {

  @Option(
      names = {"-d", "--database"},
      description = "Path to the file that will store the database")
  private Path databasePath;

  @Option(
      names = {"-l", "--lucene"},
      description = "Path to the directory that will store lucene data")
  private Path lucenePath;

  @Parameters(index = "0", description = "Recipes source file. One json-serialized Recipe per line")
  private Path sourceFile;

  @Override
  public void run() {

    if (!sourceFile.toFile().canRead()) {
      System.err.println("Can't read source file: " + sourceFile);
      return;
    }

    if (databasePath == null && lucenePath == null) {
      System.out.println("Nothing to do, computing stats...");
      System.out.println(computeStats());
      return;
    }

    if (databasePath != null) {

      if (databasePath.toFile().exists()) {
        System.err.println("Refusing to overwrite existing database file: " + databasePath);
        return;
      }

      buildDatabase();
    }

    if (lucenePath != null) {

      if (lucenePath.toFile().exists()) {
        System.err.println("Refusing to overwrite existing lucene dir: " + lucenePath);
        return;
      }

      if (!lucenePath.toFile().mkdirs()) {
        System.err.println("Failed to create base lucene dir: " + lucenePath);
        return;
      }

      buildLucene();
    }
  }

  void buildLucene() {
    System.out.println("Initializing index at " + lucenePath);
    var indexer =
        new Indexer.Builder()
            .categoryExtractor(new IndexFacet().getCategoryExtractor())
            .dataDirectory(lucenePath)
            .createMode()
            .build();

    System.out.println("Ingesting all recipes. This will take a while...");

    Flux.fromStream(recipeStream())
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

  void buildDatabase() {

    System.out.println("Computing ChronicleMap creation parameters");
    var stats = computeStats();
    System.err.println(stats);

    System.out.println("Creating database file as " + databasePath);
    var db =
        ChronicleRecipeMetadataDatabase.create(databasePath, stats.getAverage(), stats.getCount());

    Flux.fromStream(recipeStream().map(RecipeMetadata::fromRecipe))
        .buffer(100_000)
        .subscribe(
            recipes -> {
              System.out.println("Writing a batch of " + recipes.size() + " recipes");
              db.saveAll(recipes);
            });

    db.close();
    System.out.println("Finished creating database file");
  }

  LongSummaryStatistics computeStats() {
    return recipeStream()
        .map(FlatBufferSerializer.INSTANCE::flattenRecipe)
        .collect(Collectors.summarizingLong(bb -> bb.limit() - bb.position()));
  }

  private Stream<Recipe> recipeStream() {
    var mapper = new ObjectMapper();
    mapper.registerModule(new Jdk8Module());

    return sourceLines()
        .map(
            line -> {
              try {
                return mapper.readValue(line, Recipe.class);
              } catch (Exception rethrown) {
                throw new RuntimeException("Couldn't read line as recipe: " + line, rethrown);
              }
            });
  }

  private Stream<String> sourceLines() {
    try {
      return Files.lines(sourceFile);
    } catch (IOException wrapped) {
      throw new RuntimeException("Exception thrown reading input file", wrapped);
    }
  }

  public static void main(String[] args) {
    CommandLine.run(new Loader(), args);
    Schedulers.shutdownNow();
  }
}
