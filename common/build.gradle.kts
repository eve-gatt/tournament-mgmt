val vertxVersion = "3.9.0"

dependencies {
    //    compile("com.google.guava:guava:28.1-jre")
    compile("net.troja.eve:eve-esi:3.0.0")
    compile("io.vertx:vertx-circuit-breaker:$vertxVersion")
    compile("io.vertx:vertx-core:$vertxVersion")
    compile("io.vertx:vertx-micrometer-metrics:$vertxVersion")
    compile("io.vertx:vertx-web-client:$vertxVersion")

    compile("io.micrometer:micrometer-registry-prometheus:1.4.1")

}

