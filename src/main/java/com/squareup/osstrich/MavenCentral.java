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
import java.io.IOException;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.moshi.MoshiConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Programmatic access to search.maven.org.
 * http://search.maven.org/#api
 */
public final class MavenCentral {
  private final MavenDotOrg mavenDotOrg;

  public MavenCentral() {
    Retrofit retrofit = new Retrofit.Builder()
        .baseUrl(HttpUrl.parse("https://search.maven.org/"))
        .addConverterFactory(MoshiConverterFactory.create())
        .build();
    this.mavenDotOrg = retrofit.create(MavenDotOrg.class);
  }

  /** Returns the latest version of up to 20 projects. */
  public List<Artifact> latestArtifacts(String groupId) throws IOException {
    Call<Select> call = mavenDotOrg.latestArtifacts("g:\"" + groupId + "\"");
    Response<Select> execute = call.execute();
    return execute.body().response.artifacts;
  }

  public BufferedSource downloadJavadocJar(Artifact artifact) throws IOException {
    Call<ResponseBody> call = mavenDotOrg.javadoc(
        artifact.groupId, artifact.artifactId, artifact.latestVersion);
    Response<ResponseBody> response = call.execute();

    if (!response.isSuccessful()) {
      String errorBody = response.errorBody().string();
      response.errorBody().close();
      throw new IOException("Failed to download " + artifact
          + " (" + response.code() + " " + response.raw().message() + "):\n" + errorBody);
    }

    return response.body().source();
  }

  interface MavenDotOrg {
    /** Returns up to 20 projects. */
    @GET("classic/solrsearch/select?rows=20&wt=json")
    Call<Select> latestArtifacts(@Query("q") String query);

    @GET("classic/remote_content?c=javadoc")
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
