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
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;

/** Makes shelling out to the command line slightly neater. */
public final class Cli {
  private final File directory;

  public Cli() {
    this.directory = null;
  }

  private Cli(File directory) {
    this.directory = directory.getAbsoluteFile();
  }

  public Cli withCwd(File directory) {
    return new Cli(directory);
  }

  public void exec(String... command) throws IOException {
    ProcessBuilder processBuilder = new ProcessBuilder(command);

    if (directory != null) {
      processBuilder.directory(directory);
    }

    processBuilder.redirectErrorStream(true);
    Process process = processBuilder.start();

    Buffer buffer = new Buffer();
    try (BufferedSource source = Okio.buffer(Okio.source(process.getInputStream()))) {
      source.timeout().timeout(30, TimeUnit.SECONDS);
      source.timeout().deadline(5, TimeUnit.MINUTES);
      source.readAll(buffer);
    } catch (IOException e) {
      throw new IOException("Failed to execute " + Arrays.toString(command) + ":\n"
          + buffer.readUtf8());
    } finally {
      process.destroy();
    }
    try {
      process.waitFor(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      throw new IOException(e);
    }
    int exitValue = process.exitValue();
    if (exitValue != 0) {
      throw new IOException("Process returned " + exitValue + ":\n"
          + Arrays.toString(command) + ":\n"
          + buffer.readUtf8());
    }
  }
}
