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

import com.squareup.moshi.Json;
import java.util.Collections;
import java.util.Set;

/** An artifact search result. */
public final class Artifact {
  private static final String JAVADOC_EXTENSION = "-javadoc.jar";

  @Json(name = "g") String groupId;
  @Json(name = "a") String artifactId;
  String latestVersion;
  @Json(name = "p") String packaging;
  long timestamp;
  @Json(name = "ec") Set<String> extensions;

  public static Artifact create(String groupId, String artifactId, String latestVersion) {
    Artifact result = new Artifact();
    result.groupId = groupId;
    result.artifactId = artifactId;
    result.latestVersion = latestVersion;
    result.extensions = Collections.singleton(JAVADOC_EXTENSION);
    return result;
  }

  public boolean hasJavadoc() {
    return extensions.contains(JAVADOC_EXTENSION);
  }

  @Override public String toString() {
    return String.format("%s:%s:%s", groupId, artifactId, latestVersion);
  }
}
