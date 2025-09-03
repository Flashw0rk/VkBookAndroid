plugins {
    id("java-library")


}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}



dependencies {
    // Для чтения Excel (.xlsx)
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    // Для работы с JSON (файл armature_coords.json)
    implementation("com.google.code.gson:gson:2.10.1")
}