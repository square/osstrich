# osstrich

Open Source Software Task Robot Indestructible Computer Hero

## Publishing Javadoc with Osstrich

Osstrich will publish Javadoc for the latest release on Maven Central. It may take a few hours for
the release to be indexed by the Maven Central search engine.

Publish the latest artifacts of a given group ID:

```
java -jar target/osstrich-cli.jar temp/moshi git@github.com:square/moshi.git com.squareup.moshi
```

Or a specific versioned artifact. Prefer this for groups like `com.squareup` that contain unrelated
projects.

```
java -jar target/osstrich-cli.jar temp/javapoet git@github.com:square/javapoet.git com.squareup javapoet 1.3.0
```
