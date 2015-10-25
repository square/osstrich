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
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import okio.BufferedSource;
import okio.Okio;
import okio.Sink;
import org.apache.maven.plugin.logging.Log;

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

  public int publishJavadoc(String repoUrl, String groupId) throws IOException {
    initGitDirectory(repoUrl);

    StringBuilder commitMessage = new StringBuilder();
    commitMessage.append("Publish Javadoc\n"
        + "\n"
        + "Artifacts published:");

    int artifactsPublished = 0;

    List<Artifact> artifacts = mavenCentral.latestArtifacts(groupId);
    log.info(String.format("Maven central returned %s artifacts", artifacts.size()));

    for (Artifact artifact : artifacts) {
      if (!artifact.hasJavadoc()) {
        log.info(String.format("Skipping %s, artifact has no Javadoc", artifact));
        continue;
      }

      File artifactDirectory = new File(
          directory + "/" + artifact.latestVersion + "/" + artifact.artifactId);

      if (artifactDirectory.exists()) {
        log.info(String.format("Skipping %s, directory exists", artifactDirectory));
        continue;
      }

      log.info(String.format("Downloading %s to %s", artifact, artifactDirectory));
      downloadJavadoc(artifact, artifactDirectory);
      gitAdd(artifactDirectory);

      commitMessage.append("\n").append(artifact);
      artifactsPublished++;
    }

    if (artifactsPublished > 0) {
      gitCommitAndPush(commitMessage.toString());
    }

    return artifactsPublished;
  }

  public void initGitDirectory(String repoUrl) throws IOException {
    log.info(String.format("Checking out %s to %s", repoUrl, directory));
    cli.exec("rm", "-rf", directory.getAbsolutePath());
    cli.exec("git", "clone",
        "--single-branch",
        "--branch", "gh-pages",
        repoUrl,
        directory.getAbsolutePath());
  }

  public void downloadJavadoc(Artifact artifact, File destination) throws IOException {
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
}
