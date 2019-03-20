# Casserole

Casserole is the Web facing part of [gula.recipes][gula]. It connects
`cerberus` with `tablier` and all recipes to provide the user
experience you have at the website. It's a [Spring Boot][boot] app
using the WebFlux framework.

[gula]: https://gula.recipes
[boot]: https://spring.io/projects/spring-boot

## Assemble fat jar

    mvn package

## Run server locally

    mvn spring-boot:run

## Test

    mvn test

