# osstrich

Open Source Software Task Robot Indestructible Computer Hero

## Publishing Javadoc with Osstrich

#### Release

Osstrich will publish Javadoc for the latest release on Maven Central. It may take a few hours for
the release to be indexed by the Maven Central search engine.


#### Configure Maven

Add Osstrich to your Maven `pom.xml`:

```xml
<project ...>
  ...

  <build>
    <plugins>
      <plugin>
        <groupId>com.squareup.osstrich</groupId>
        <artifactId>osstrich</artifactId>
        <version>1.0.0</version>
      </plugin>
    </plugins>
  </build>
</project>
```


#### Run

Run the Osstrich plugin. This will fetch the release Javadoc and copy it to `gh-pages`.

```
mvn osstrich:publishjavadoc
```
