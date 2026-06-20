plugins {
    java
    id("org.springframework.boot") version "3.4.3" apply false
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")

    group = "com.stripe.platform"
    version = "0.0.1-SNAPSHOT"

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    repositories {
        mavenCentral()
    }

    configurations {
        compileOnly {
            extendsFrom(configurations.annotationProcessor.get())
        }
    }

    dependencies {
        compileOnly("org.projectlombok:lombok:1.18.36")
        annotationProcessor("org.projectlombok:lombok:1.18.36")

        implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))

        implementation("org.springframework.boot:spring-boot-starter-web")
        implementation("org.springframework.boot:spring-boot-starter-data-jpa")
        implementation("org.springframework.boot:spring-boot-starter-validation")
        implementation("org.springframework.boot:spring-boot-starter-actuator")
        implementation("org.springframework.kafka:spring-kafka")
        implementation("org.flywaydb:flyway-core")
        implementation("org.flywaydb:flyway-database-postgresql")
        implementation("io.micrometer:micrometer-registry-prometheus")

        runtimeOnly("org.postgresql:postgresql")

        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.testcontainers:postgresql:1.20.4")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
