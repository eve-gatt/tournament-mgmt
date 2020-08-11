val vertxVersion = "3.9.0"

val USERNAME: String by project
val PASSWORD: String by project

dependencies {
    compile(project(":web"))
}

plugins {
    id("com.google.cloud.tools.jib") version "2.4.0"
}

jib {
    container {
        ports = listOf("6070")
        mainClass = "toys.eve.tournamgmt.SingleProcessMain"
        jvmFlags = listOf(
                "-Dhttp.agent=Gatt2111@tweetfleet"
//                "-Dsun.net.inetaddr.ttl=0"
        )
    }
    to {
        image = "docker.pkg.github.com/eve-gatt/tournament-mgmt/app:1." + System.getenv("GITHUB_RUN_ID")
//        image = "docker.pkg.github.com/eve-gatt/evetoys/app:test-1.0"
        auth {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

