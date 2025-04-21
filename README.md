# Suspendify Kotlin Compiler Plugin

**Suspendify** is a Kotlin compiler plugin that automatically generates coroutine‑friendly wrappers for your classes. Annotate a class with `@Suspendify`, and the plugin will:

- Generate a nested wrapper class that mirrors the original class’s public API, but with all public methods marked as `suspend`.
- Generate an extension function `suspendify(dispatcher: CoroutineContext)` on the original class, which returns an instance of the nested suspend‑only wrapper.
- Each generated suspend method delegates to the original method inside a `withContext(dispatcher) { … }` block.

---

## Installation

Add the `suspendify` Gradle plugin and the corresponding core library to your `build.gradle.kts` file.
Remember to add a Kotlin coroutines library as well:

```kotlin
plugins {
    kotlin("jvm") version "2.1.20"
    id("io.github.rastiehaiev.suspendify") version "0.0.2"
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("io.github.rastiehaiev:suspendify-core:0.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}

// enabled by default
suspendify {
    enabled = true
}
```

## Usage

1. **Annotate your class**  
    Add `@Suspendify` to any class whose public methods you want to expose as suspend functions:
    
    ```kotlin
    @Suspendify
    class Repository {
       fun save(id: String) { /*…*/ }
       fun find(): Set<String> { /*…*/ }
    }
    ```

2. **Call the generated extension**
    ```kotlin
    fun main() = runBlocking {
        val repo = Repository()
        
        // 'suspendify' is auto‑generated:
        val suspendifiedRepository = repo.suspendify(with = Dispatchers.IO)
        
        // Now you can call the original methods as suspend functions:
        suspendifiedRepository.save("123")
   }
   ```

See the [sample project](./sample/src/main/kotlin/io/github/rastiehaiev/Main.kt).
