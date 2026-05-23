# infra-redis

Thin Infra-Core library module that exposes [Jedis](https://github.com/redis/jedis) as a transitive `api` dependency for consumer modules.

## What it provides

- Jedis client on the consumer's compile classpath (no need to redeclare).
- Consistent Jedis version pin across all Infra-Core consumers (`jedisVersion` in root `build.gradle.kts`).
- A future home for shared Redis helpers (connection factory, key-namespacing utilities, Lua-script loaders) when more than one module needs them. Empty for now by design.

## Consuming this module

In a `design-lab` (or other) module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("org.infra:infra-redis:1.0.0")
}
```

Then in Java:

```java
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
// ... use Jedis directly
```

## Publishing

From `Infra-Core/` root:

```
./gradlew clean build publishToMavenLocal
```
