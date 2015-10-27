/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.osstrich;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Sink;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;

/** Downloads Javadoc from Maven and uploads it to GitHub pages. */
public final class JavadocPublisher {
  final MavenCentral mavenCentral;
  final Cli cli;
  final Log log;
  final File directory;

  public JavadocPublisher(MavenCentral mavenCentral, Cli cli, Log log, File directory) {
    this.mavenCentral = mavenCentral;
    this.cli = cli;
    this.log = log;
    this.directory = directory;
  }

  public int publishLatest(String repoUrl, String groupId) throws IOException {
    List<Artifact> artifacts = mavenCentral.latestArtifacts(groupId);
    log.info(String.format("Maven central returned %s artifacts", artifacts.size()));
    return publishArtifacts(repoUrl, artifacts);
  }

  public int publish(String repoUrl, String groupId, String artifactId, String version)
      throws IOException {
    Artifact artifact = Artifact.create(groupId, artifactId, version);
    return publishArtifacts(repoUrl, Collections.singletonList(artifact));
  }

  private int publishArtifacts(String repoUrl, List<Artifact> artifacts) throws IOException {
    initGitDirectory(repoUrl);

    StringBuilder commitMessage = new StringBuilder();
    commitMessage.append("Publish Javadoc\n"
        + "\n"
        + "Artifacts published:");

    int artifactsPublished = 0;


    for (Artifact artifact : artifacts) {
      if (publish(artifact)) {
        commitMessage.append("\n").append(artifact);
        artifactsPublished++;
      }
    }

    if (artifactsPublished > 0) {
      gitCommitAndPush(commitMessage.toString());
    }

    return artifactsPublished;
  }

  private boolean publish(Artifact artifact) throws IOException {
    if (!artifact.hasJavadoc()) {
      log.info(String.format("Skipping %s, artifact has no Javadoc", artifact));
      return false;
    }

    File artifactDirectory = new File(directory
        + "/" + majorVersion(artifact.latestVersion) + "/" + artifact.artifactId);
    File versionText = new File(artifactDirectory, "version.txt");

    if (versionText.exists() && artifact.latestVersion.equals(readUtf8(versionText))) {
      log.info(String.format("Skipping %s, artifact is up to date", artifactDirectory));
      return false;
    }

    log.info(String.format("Downloading %s to %s", artifact, artifactDirectory));
    downloadJavadoc(artifact, artifactDirectory);
    writeUtf8(versionText, artifact.latestVersion);
    gitAdd(artifactDirectory);
    return true;
  }

  /** Returns a major version string, like {@code 2.x} for {@code 2.5.0}. */
  static String majorVersion(String version) {
    Pattern pattern = Pattern.compile("([^.]+)\\..*");
    Matcher matcher = pattern.matcher(version);
    return matcher.matches() ? matcher.group(1) + ".x" : version;
  }

  private String readUtf8(File file) throws IOException {
    try (BufferedSource source = Okio.buffer(Okio.source(file))) {
      return source.readUtf8();
    }
  }

  private void writeUtf8(File file, String string) throws IOException {
    try (BufferedSink sink = Okio.buffer(Okio.sink(file))) {
      sink.writeUtf8(string);
    }
  }

  public void initGitDirectory(String repoUrl) throws IOException {
    if (directory.exists()) {
      log.info(String.format("Pulling latest from %s to %s", repoUrl, directory));
      cli.withCwd(directory).exec("git", "pull");
    } else {
      log.info(String.format("Checking out %s to %s", repoUrl, directory));
      cli.exec("rm", "-rf", directory.getAbsolutePath());
      cli.exec("git", "clone",
          "--single-branch",
          "--branch", "gh-pages",
          repoUrl,
          directory.getAbsolutePath());
    }
  }

  private void downloadJavadoc(Artifact artifact, File destination) throws IOException {
    try (BufferedSource source = mavenCentral.downloadJavadocJar(artifact);
         ZipInputStream zipIn = new ZipInputStream(source.inputStream())) {
      for (ZipEntry entry; (entry = zipIn.getNextEntry()) != null; ) {
        if (entry.isDirectory()) continue;

        File file = new File(destination + "/" + entry.getName());
        if (!file.getParentFile().mkdirs() && !file.getParentFile().isDirectory()) {
          throw new IOException("failed to mkdirs for " + file);
        }
        try (Sink out = Okio.sink(file)) {
          Okio.buffer(Okio.source(zipIn)).readAll(out);
        }
      }
    }
  }

  private void gitAdd(File file) throws IOException {
    cli.withCwd(directory).exec("git", "add", file.getAbsolutePath());
  }

  private void gitCommitAndPush(String message) throws IOException {
    cli.withCwd(directory).exec("git", "commit", "-m", message);
    cli.withCwd(directory).exec("git", "push", "origin", "gh-pages");
  }

  public static void main(String[] args) throws IOException {
    Log log = new SystemStreamLog();

    if (args.length != 3 && args.length != 5) {
      log.info(String.format(""
          + "Usage: %1$s <directory> <repo URL> <group ID>\n"
          + "       %1$s <directory> <repo URL> <group ID> <artifact ID> <version>\n",
          JavadocPublisher.class.getName()));
      System.exit(1);
      return;
    }

    File directory = new File(args[0]);
    String repoUrl = args[1];
    String groupId = args[2];

    JavadocPublisher javadocPublisher = new JavadocPublisher(
        new MavenCentral(), new Cli(), log, directory);

    int artifactsPublished;
    if (args.length == 3) {
      artifactsPublished = javadocPublisher.publishLatest(repoUrl, groupId);
    } else {
      String artifactId = args[3];
      String version = args[4];
      artifactsPublished = javadocPublisher.publish(repoUrl, groupId, artifactId, version);
    }

    log.info("Published Javadoc for " + artifactsPublished + " artifacts of "
        + groupId + " to " + repoUrl);
  }
}
