val vertxVersion = "3.9.2"

dependencies {
    compile(project(":common"))
    compile("io.vertx:vertx-core:$vertxVersion")
    compile("io.vertx:vertx-jdbc-client:$vertxVersion")
    compile("io.vertx:vertx-sql-common:$vertxVersion")
    compile("org.flywaydb:flyway-core:6.5.5")
    compile("org.postgresql:postgresql:42.2.14")
    compile("org.mariadb.jdbc:mariadb-java-client:2.7.0")
    runtime("org.slf4j:slf4j-simple:1.7.26")
}

