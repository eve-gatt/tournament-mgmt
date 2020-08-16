val vertxVersion = "3.9.2"

dependencies {
    compile(project(":common"))
    compile("io.vertx:vertx-core:$vertxVersion")
    compile("io.vertx:vertx-jdbc-client:$vertxVersion")
    compile("io.vertx:vertx-sql-common:$vertxVersion")
    compile("org.ethereum:leveldbjni-all:1.18.3")
    compile("org.flywaydb:flyway-core:6.2.4")
    compile("org.postgresql:postgresql:42.2.10")
}

