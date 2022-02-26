/*
 * Copyright 2016 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.inferred.freebuilder.processor.source.testing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import javax.tools.FileObject;

public class InMemoryFile implements FileObject {

  private static class Bytes extends ByteArrayOutputStream {

    private long lastModified = System.currentTimeMillis();

    @Override
    public synchronized void write(int b) {
      super.write(b);
      lastModified = System.currentTimeMillis();
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
      super.write(b, off, len);
      lastModified = System.currentTimeMillis();
    }
  }

  private final String name;
  private Bytes data;

  public InMemoryFile(String name) {
    this.name = name;
  }

  @Override
  public URI toUri() {
    return URI.create("mem://" + name);
  }

  @Override
  public String getName() {
    return name;
  }

  public ByteBuffer getBuffer() {
    if (data == null) {
      return ByteBuffer.allocate(0).asReadOnlyBuffer();
    } else {
      return ByteBuffer.wrap(data.toByteArray()).asReadOnlyBuffer();
    }
  }

  @Override
  public OutputStream openOutputStream() throws IOException {
    if (data != null) {
      throw new FileAlreadyExistsException(toUri().toString());
    }
    data = new Bytes();
    return data;
  }

  @Override
  public Writer openWriter() throws IOException {
    return new OutputStreamWriter(openOutputStream());
  }

  @Override
  public boolean delete() {
    boolean exists = (data != null);
    data = null;
    return exists;
  }

  @Override
  public InputStream openInputStream() throws IOException {
    if (data == null) {
      throw new FileNotFoundException(toUri().toString());
    }
    return new ByteArrayInputStream(data.toByteArray());
  }

  @Override
  public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
    return new InputStreamReader(openInputStream());
  }

  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
    if (data == null) {
      throw new FileNotFoundException(toUri().toString());
    }
    return data.toString();
  }

  @Override
  public long getLastModified() {
    return (data == null) ? 0 : data.lastModified;
  }
}
