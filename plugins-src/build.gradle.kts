// Configuracion comun para todos los plugins (subproyectos).
subprojects {
    apply(plugin = "java")

    group = "com.dolfin"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
    }

    dependencies {
        // La API de Paper 26.3: solo se usa para compilar, el servidor ya la trae.
        "compileOnly"("io.papermc.paper:paper-api:26.3.build.+")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    // Copia ${version} de build.gradle.kts dentro de plugin.yml
    tasks.named<ProcessResources>("processResources") {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }

    // "gradlew deploy": compila y copia el .jar a server/plugins
    tasks.register<Copy>("deploy") {
        group = "dolfin"
        description = "Compila el plugin y lo copia al servidor"
        from(tasks.named("jar"))
        into(rootProject.file("../server/plugins"))
    }
}
