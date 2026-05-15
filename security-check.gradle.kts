// OWASP Dependency Check — standalone task
// Run: ./gradlew dependencyCheckAnalyze
plugins {
    id("org.owasp.dependencycheck") version "9.0.9"
}

dependencyCheck {
    failBuildOnCVSS = 7.0f  // Fail on HIGH or CRITICAL vulnerabilities
    formats = listOf("HTML", "JSON")
}
