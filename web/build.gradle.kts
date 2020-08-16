val vertxVersion = "3.9.2"

plugins {
    id("com.moowork.node") version "1.3.1"
}

node {
    download = true
}

dependencies {
    compile(project(":common"))
    compile(project(":db"))

    implementation("io.vertx:vertx-auth-common:$vertxVersion")
    implementation("io.vertx:vertx-circuit-breaker:$vertxVersion")
    implementation("io.vertx:vertx-core:$vertxVersion")
    implementation("io.vertx:vertx-web-client:$vertxVersion")
    implementation("io.vertx:vertx-web-templ-jade:$vertxVersion")
    implementation("io.vertx:vertx-web:$vertxVersion")
    implementation("io.vertx:vertx-web-api-contract:$vertxVersion")
    implementation("io.vertx:vertx-jdbc-client:$vertxVersion")
    implementation("io.vertx:vertx-sql-common:$vertxVersion")

    implementation("me.escoffier.vertx:vertx-completable-future:0.1.2")

    implementation("io.github.resilience4j:resilience4j-all:1.3.1")
    implementation("io.github.resilience4j:resilience4j-micrometer:1.3.1")

    implementation("org.jgrapht:jgrapht-core:1.4.0")
    implementation("org.jgrapht:jgrapht-io:1.4.0")

    testCompile("io.vertx:vertx-unit:$vertxVersion")
}

