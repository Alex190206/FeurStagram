group = "com.feurstagram"

patches {
    about {
        name = "Feurstagram Patches"
        description = "Distraction-free Instagram: blocks the feed, stories, explore, reels, ads and suggestions, with a runtime toggle and an optional permanent lock."
        source = "git@github.com:jeanherail/Feurstagram.git"
        author = "Jean Herail"
        contact = "na"
        website = "na"
        license = "GPLv3"
    }
}

dependencies {
    // Provides app.morphe.util.* helpers (DOM, bytecode, references) used by the patches.
    implementation(libs.morphe.patches.library)
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
