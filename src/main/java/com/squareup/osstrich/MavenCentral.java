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
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.ResponseBody;
import java.io.IOException;
import java.util.List;
import okio.BufferedSource;
import retrofit.Call;
import retrofit.MoshiConverterFactory;
import retrofit.Retrofit;
import retrofit.http.GET;
import retrofit.http.Query;

/**
 * Programmatic access to search.maven.org.
 * http://search.maven.org/#api
 */
public final class MavenCentral {
  private final MavenDotOrg mavenDotOrg;

  public MavenCentral() {
    Retrofit retrofit = new Retrofit.Builder()
        .baseUrl(HttpUrl.parse("http://search.maven.org/"))
        .addConverterFactory(MoshiConverterFactory.create())
        .build();
    this.mavenDotOrg = retrofit.create(MavenDotOrg.class);
  }

  /** Returns the latest version of up to 20 projects. */
  public List<Artifact> latestArtifacts(String groupId) throws IOException {
    Call<Select> call = mavenDotOrg.latestArtifacts("g:\"" + groupId + "\"");
    retrofit.Response<Select> execute = call.execute();
    return execute.body().response.artifacts;
  }

  public BufferedSource downloadJavadocJar(Artifact artifact) throws IOException {
    Call<ResponseBody> call = mavenDotOrg.javadoc(
        artifact.groupId, artifact.artifactId, artifact.latestVersion);
    retrofit.Response<ResponseBody> response = call.execute();

    if (!response.isSuccess()) {
      String errorBody = response.errorBody().string();
      response.errorBody().close();
      throw new IOException("Failed to download " + artifact
          + " (" + response.code() + " " + response.raw().message() + "):\n" + errorBody);
    }

    return response.body().source();
  }

  interface MavenDotOrg {
    /** Returns up to 20 projects. */
    @GET("/solrsearch/select?rows=20&wt=json")
    Call<Select> latestArtifacts(@Query("q") String query);

    @GET("/remote_content?c=javadoc")
    Call<ResponseBody> javadoc(
        @Query("g") String groupId, @Query("a") String artifactId, @Query("v") String version);
  }

  static final class Select {
    @Json(name = "response") Response response;

    static final class Response {
      @Json(name = "docs") List<Artifact> artifacts;
    }
  }
}
