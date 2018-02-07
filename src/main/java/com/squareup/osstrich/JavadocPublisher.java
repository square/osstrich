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

import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.google.common.io.Files;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Sink;

import static java.nio.charset.StandardCharsets.UTF_8;

/** Downloads Javadoc from Maven and uploads it to GitHub pages. */
public final class JavadocPublisher {
  final MavenCentral mavenCentral;
  final Cli cli;
  final Log log;
  final File directory;
  final boolean dryRun;
  final boolean force;

  public JavadocPublisher(MavenCentral mavenCentral, Cli cli, Log log, File directory) {
    this(mavenCentral, cli, log, directory, false, false);
  }

  public JavadocPublisher(MavenCentral mavenCentral,
      Cli cli,
      Log log,
      File directory,
      boolean dryRun,
      boolean force) {
    this.mavenCentral = mavenCentral;
    this.cli = cli;
    this.log = log;
    this.directory = directory;
    this.dryRun = dryRun;
    this.force = force;
  }

  public int publishLatest(String repoUrl, String groupId) throws IOException {
    List<Artifact> artifacts = mavenCentral.latestArtifacts(groupId);
    log.info(String.format("Maven central returned %s artifacts", artifacts.size()));
    return publishArtifacts(repoUrl, groupId, artifacts);
  }

  public int publish(String repoUrl, String groupId, String artifactId, String version)
      throws IOException {
    Artifact artifact = Artifact.create(groupId, artifactId, version);
    return publishArtifacts(repoUrl, groupId, Collections.singletonList(artifact));
  }

  private int publishArtifacts(String repoUrl, String groupId, List<Artifact> artifacts)
      throws IOException {
    initGitDirectory(repoUrl);

    StringBuilder commitMessage = new StringBuilder();
    commitMessage.append("Publish Javadoc\n"
        + "\n"
        + "Artifacts published:");

    Multimap<String, Artifact> published = TreeMultimap.create();
    for (Artifact artifact : artifacts) {
      if (fetchJavadoc(artifact)) {
        published.put(majorVersion(artifact.latestVersion), artifact);
        commitMessage.append("\n").append(artifact);
      }
    }

    writeIndexFiles(groupId, published);

    if (!published.isEmpty()) {
      gitCommitAndPush(commitMessage.toString());
    }

    return published.size();
  }

  private boolean fetchJavadoc(Artifact artifact) throws IOException {
    if (!artifact.hasJavadoc()) {
      log.info(String.format("Skipping %s, artifact has no Javadoc", artifact));
      return false;
    }

    File artifactDirectory = new File(directory
        + "/" + majorVersion(artifact.latestVersion) + "/" + artifact.artifactId);
    File versionText = new File(artifactDirectory, "version.txt");

    if (versionText.exists() && artifact.latestVersion.equals(readUtf8(versionText))) {
      if (force) {
        log.info(String.format("%s artifact is up to date, but downloading anyway due to --force",
            artifactDirectory));
      } else {
        log.info(String.format("Skipping %s, artifact is up to date", artifactDirectory));
        return false;
      }
    }

    log.info(String.format("Downloading %s to %s", artifact, artifactDirectory));
    downloadJavadoc(artifact, artifactDirectory);
    writeUtf8(versionText, artifact.latestVersion);
    gitAdd(artifactDirectory);
    return true;
  }

  private void writeIndexFiles(String groupId, Multimap<String, Artifact> artifacts) throws IOException {
    for (String majorVersion : artifacts.keySet()) {
      StringBuilder html = new StringBuilder();
      html.append("<!DOCTYPE html>\n<html><head><title>")
          .append(groupId)
          .append("</title></head>\n<body>\n<h1>")
          .append(groupId)
          .append("</h1>\n<ul>\n");
      File workingDir = new File(directory, majorVersion);
      for (Artifact artifact : artifacts.get(majorVersion)) {
        html.append("<li><a href=\"")
            .append(findRelativePath(new File(workingDir, artifact.artifactId), artifact.artifactId))
            .append("\">")
            .append(artifact.artifactId)
            .append("</li>\n");
      }
      html.append("</ul>\n</body>\n</html>");

      File indexHtml = new File(directory + "/" + majorVersion + "/index.html");
      Files.write(html, indexHtml, UTF_8);
      //gitAdd(indexHtml);
    }
  }

  private String findRelativePath(File directory, String artifactId) {
    if (!directory.isDirectory()) {
      throw new IllegalArgumentException(directory + " is not a directory!");
    }
    if (new File(directory, "index.html").exists()) {
      return directory.getName();
    } else {
      // Look for subdirectories of the same name as the artifact and go a level deeper
      File[] subfiles = Objects.requireNonNull(directory.listFiles());
      for (File subfile : subfiles) {
        if (subfile.isDirectory() && artifactId.equals(subfile.getName())) {
          return directory.getName() + File.separator + findRelativePath(subfile, artifactId);
        }
      }
      throw new RuntimeException("Could not find a valid indexed path for " + artifactId + ". Files are " + Arrays.toString(subfiles));
    }
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
    if (dryRun) {
      log.info(String.format("DRY-RUN: git commit -m %s", message));
      log.info("DRY-RUN: git push origin gh-pages");
    } else {
      cli.withCwd(directory).exec("git", "commit", "-m", message);
      cli.withCwd(directory).exec("git", "push", "origin", "gh-pages");
    }
  }

  public static void main(String[] args) throws IOException {
    Log log = new SystemStreamLog();

    boolean force = false;
    boolean dryRun = false;
    List<String> strippedArgs = new ArrayList<>(args.length);
    for (String arg : args) {
      if ("--dry-run".equals(arg)) {
        dryRun = true;
      } else if ("force".equals(arg)) {
        force = true;
      } else {
        strippedArgs.add(arg);
      }
    }
    String[] finalArgs = strippedArgs.toArray(new String[strippedArgs.size()]);

    if (finalArgs.length != 3 && finalArgs.length != 5) {
      log.info(String.format(""
          + "Usage: %1$s <directory> <repo URL> <group ID>\n"
          + "       %1$s <directory> <repo URL> <group ID> <artifact ID> <version>\n",
          JavadocPublisher.class.getName()));
      System.exit(1);
      return;
    }

    File directory = new File(finalArgs[0]);
    String repoUrl = finalArgs[1];
    String groupId = finalArgs[2];

    JavadocPublisher javadocPublisher = new JavadocPublisher(
        new MavenCentral(), new Cli(), log, directory, dryRun, force);

    int artifactsPublished;
    if (finalArgs.length == 3) {
      artifactsPublished = javadocPublisher.publishLatest(repoUrl, groupId);
    } else {
      String artifactId = finalArgs[3];
      String version = finalArgs[4];
      artifactsPublished = javadocPublisher.publish(repoUrl, groupId, artifactId, version);
    }

    log.info("Published Javadoc for " + artifactsPublished + " artifacts of "
        + groupId + " to " + repoUrl);
  }
}
