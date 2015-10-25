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
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "publishjavadoc", defaultPhase = LifecyclePhase.NONE, aggregator = true)
public final class PublishJavadocMojo extends AbstractMojo {
  private static final String SCM_PREFIX = "scm:git:";

  @Parameter(defaultValue = "${project}", required = true, readonly = true)
  private MavenProject project;

  @Override public void execute() throws MojoExecutionException, MojoFailureException {
    String groupId = project.getGroupId();
    String developerConnection = project.getScm().getDeveloperConnection();
    if (!developerConnection.startsWith(SCM_PREFIX)) {
      throw new MojoFailureException("Unexpected developer connection: " + developerConnection);
    }
    String repoUrl = developerConnection.substring(SCM_PREFIX.length());
    File directory = new File(project.getBuild().getDirectory() + "/osstrich");

    JavadocPublisher javadocPublisher =
        new JavadocPublisher(new MavenCentral(), new Cli(), getLog(), directory);

    try {
      int artifactsPublished = javadocPublisher.publishJavadoc(repoUrl, groupId);
      getLog().info("Published Javadoc of " + artifactsPublished + " artifacts for "
          + groupId + " to " + repoUrl);
    } catch (IOException e) {
      throw new MojoExecutionException(
          "Failed to publish Javadoc for " + groupId + " to " + repoUrl, e);
    }
  }
}
