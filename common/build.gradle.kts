val vertxVersion = "3.9.2"

dependencies {
    implementation("io.vertx:vertx-core:$vertxVersion")
    implementation("io.vertx:vertx-web:$vertxVersion")
    implementation("io.vertx:vertx-web-templ-jade:$vertxVersion")
    implementation("io.vertx:vertx-web-client:$vertxVersion")

    implementation("io.vertx:vertx-micrometer-metrics:$vertxVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.4.1")

}

