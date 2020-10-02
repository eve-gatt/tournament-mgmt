plugins {
    `java-library`
    `idea`
}

idea {
    module {
        isDownloadJavadoc = false
        isDownloadSources = true
    }
}

repositories {
    jcenter()
    maven {
        url = uri("http://dl.bintray.com/ethereum/maven")
    }
}

subprojects {
    apply(plugin = "java")
    group = "toys.eve.tournmgmt"
    version = "1.0-SNAPSHOT"
    repositories {
        jcenter()
    }
    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    dependencies {
        testCompile("junit:junit:4.12")
    }
}

configurations {
    implementation {
        resolutionStrategy.failOnVersionConflict()
    }
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
