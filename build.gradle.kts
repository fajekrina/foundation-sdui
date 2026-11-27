// foundation-sdui: the whole SDUI foundation in one library, as packages:
// (the contract — spec, validator, SduiProblem, SduiJson, OpenAPI — is its own library, foundation-sdui-contract, brought along as api)
// - core (no Spring): actor, Ed25519 signer, app version, audit / trace ports
// - web: SduiResponse, the contract's error format (SduiErrors, the @SduiEndpoint advice), ActorResolver, Idempotency-Key, the core beans
// - persistence: the sdui_* tables and *_read views (Flyway H2, Oracle scripts), JDBC audit
// - authoring, delivery, action: the handlers of the three APIs
// No routes: the service writes the controllers. Every part is off until the service turns it on (sdui.authoring.*, sdui.delivery.enabled).
// A standalone build: `./gradlew build publishToMavenLocal`.
plugins {
    `java-library`
    `maven-publish`
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.fajekrina.sduir"
version = findProperty("version") as String

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
    withSourcesJar()
}

repositories {
    // foundation-sdui-contract (../foundation-sdui-contract, published by its own build)
    mavenLocal { content { includeGroup("com.fajekrina.sduir") } }
    mavenCentral()
}

dependencyManagement {
    // Spring is the service's: compiled against Spring Boot 3.3, never bundled
    imports { mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.13") }
}

configurations.all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
}

dependencies {
    api("com.fajekrina.sduir:foundation-sdui-contract:$version")
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-jdbc")
    // the server conformance kit: foundation-sdui (src/test reference/) passes it like any channel's service
    testImplementation(testFixtures("com.fajekrina.sduir:foundation-sdui-contract:$version"))
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> { options.encoding = "UTF-8"; options.compilerArgs.add("-parameters") }
tasks.withType<Test> { useJUnitPlatform() }

publishing {
    publications { create<MavenPublication>("library") { from(components["java"]) } }
}
