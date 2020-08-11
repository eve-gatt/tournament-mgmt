val vertxVersion = "3.9.0"

plugins {
    id("com.moowork.node") version "1.3.1"
}

node {
    download = true
}

dependencies {
    compile(project(":common"))
    compile("io.vertx:vertx-auth-common:$vertxVersion")
    compile("io.vertx:vertx-circuit-breaker:$vertxVersion")
    compile("io.vertx:vertx-core:$vertxVersion")
    compile("io.vertx:vertx-web-client:$vertxVersion")
    compile("io.vertx:vertx-web-templ-jade:$vertxVersion")
    compile("io.vertx:vertx-web:$vertxVersion")
    compile("io.vertx:vertx-jdbc-client:$vertxVersion")
    compile("io.vertx:vertx-sql-common:$vertxVersion")

    compile("me.escoffier.vertx:vertx-completable-future:0.1.2")

    compile("io.github.resilience4j:resilience4j-all:1.3.1")
    compile("io.github.resilience4j:resilience4j-micrometer:1.3.1")

    compile("org.jgrapht:jgrapht-core:1.4.0")
    compile("org.jgrapht:jgrapht-io:1.4.0")

    testCompile("io.vertx:vertx-unit:$vertxVersion")
}

