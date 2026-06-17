plugins {
    id("spovishun.kotlin-library")
}

dependencies {
    testImplementation(libs.kotlin.test)
}

// Per-module detekt baseline (ADR-0001: each module carries its own accepted-debt baseline as it
// receives code). Holds the intentional generic catch in ResultContainer.catching.
detekt {
    baseline = file("detekt-baseline.xml")
}

tasks.test {
    useJUnitPlatform()
}
