// Ademas del plugin, este modulo comprime la carpeta resourcepack/ en pack.zip
// y lo copia a server/plugins/DolfinPack/, que es de donde lo sirve el plugin.

val empaquetarPack by tasks.registering(Zip::class) {
    group = "dolfin"
    description = "Comprime la carpeta resourcepack en pack.zip"
    from(rootProject.file("../resourcepack"))
    archiveFileName.set("pack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("pack"))
    // Mismo contenido = mismo zip (asi los jugadores no lo descargan de nuevo sin motivo).
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val deployPack by tasks.registering(Copy::class) {
    group = "dolfin"
    description = "Copia pack.zip al servidor"
    from(empaquetarPack)
    into(rootProject.file("../server/plugins/DolfinPack"))
}

tasks.named("deploy") {
    dependsOn(deployPack)
}
