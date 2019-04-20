package co.caio.loader;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.RunAll;
import reactor.core.scheduler.Schedulers;

@Command(
    name = "loader",
    version = "0.0.2",
    subcommands = {Lucene.class, Database.class},
    mixinStandardHelpOptions = true)
public class Loader implements Runnable {

  @Override
  public void run() {
    // Nothing to do
  }

  public static void main(String[] args) {
    var cli = new CommandLine(new Loader());
    cli.parseWithHandler(new RunAll(), args);

    if (args.length == 0) {
      cli.usage(System.out);
    }

    Schedulers.shutdownNow();
  }
}
