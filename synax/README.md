# Synax

Synax is a high-performance Kotlin syntax highlighter for Android apps.

## Installation

Add the following to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.cognautic:synax:1.0.0")
}
```

## Supported Languages

- **Kotlin**
- **Java**
- **Python**
- **JavaScript**
- **HTML/XML**
- **CSS**

## Quick Start

```kotlin
val code = "val x = 10"
val tokens = Synax.highlight(code)
```

For more details, see [DOCS.md](DOCS.md).

## Development

- `PLAN.md`: Current project roadmap.
- `DOCS.md`: Detailed usage documentation.

## Testing

Run tests with:
```bash
./gradlew test
```
