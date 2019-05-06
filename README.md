# Casserole

Casserole is the Web backend part of [gula.recipes][gula]. It connects
`cerberus` with `tablier` and all recipes to provide the user
experience you have at the website. It's a [Spring Boot][boot] app
using the [WebFlux][] framework.

There aren't many interesting things to see here, but at a few that
might be are:

- Support for rendering [Rocker][] templates at
  [RockerModelHttpMessageWriter][writer]
- A custom [Lucene][] query that attemps to detect expensive queries
  at [PerformanceInspectorQuery][query]


[gula]: https://gula.recipes
[boot]: https://spring.io/projects/spring-boot
[WebFlux]: https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html
[Rocker]: https://github.com/fizzed/rocker
[writer]: src/main/java/co/caio/casserole/ext/RockerModelHttpMessageWriter.java
[Lucene]: http://lucene.apache.org/core/
[query]: src/main/java/co/caio/casserole/ext/PerformanceInspectorQuery.java

## Assemble fat jar

    mvn package

## Run server locally

    mvn spring-boot:run

## Test

    mvn test

## Load data

Check the help output from:

    mvn exec:java -Dexec.mainClass=co.caio.loader.Loader -Dexec.args="--help"

