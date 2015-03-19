/*
 * Copyright 2014 Google Inc. All rights reserved.
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
package org.inferred.freebuilder.processor.util.testing;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.io.Files.createTempDir;
import static javax.tools.ToolProvider.getSystemJavaCompiler;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

/** Implementation of {@link JavaFileManager} that provides its own temporary output storage. */
public class TempJavaFileManager implements JavaFileManager {
  private final StandardJavaFileManager delegate;

  public TempJavaFileManager() {
    delegate = getSystemJavaCompiler().getStandardFileManager(null, null, null);
    try {
      delegate.setLocation(StandardLocation.SOURCE_OUTPUT, ImmutableList.of(createTempDir()));
      delegate.setLocation(StandardLocation.CLASS_OUTPUT, ImmutableList.of(createTempDir()));
    } catch (IOException e) {
      throw new IllegalStateException("Directory rejected by standard file manager", e);
    }
  }
  
  @Override
  public int isSupportedOption(String option) {
    return delegate.isSupportedOption(option);
  }

  @Override
  public ClassLoader getClassLoader(Location location) {
    return delegate.getClassLoader(location);
  }

  @Override
  public boolean isSameFile(FileObject a, FileObject b) {
    return delegate.isSameFile(a, b);
  }

  @Override
  public Iterable<JavaFileObject> list(
      Location location, String packageName, Set<Kind> kinds, boolean recurse) throws IOException {
    return delegate.list(location, packageName, kinds, recurse);
  }

  @Override
  public String inferBinaryName(Location location, JavaFileObject file) {
    return delegate.inferBinaryName(location, file);
  }

  @Override
  public boolean handleOption(String current, Iterator<String> remaining) {
    return delegate.handleOption(current, remaining);
  }

  @Override
  public boolean hasLocation(Location location) {
    return delegate.hasLocation(location);
  }

  @Override
  public JavaFileObject getJavaFileForInput(Location location, String className, Kind kind)
      throws IOException {
    return delegate.getJavaFileForInput(location, className, kind);
  }

  @Override
  public JavaFileObject getJavaFileForOutput(
      Location location, String className, Kind kind, FileObject sibling) throws IOException {
    return delegate.getJavaFileForOutput(location, className, kind, sibling);
  }

  @Override
  public FileObject getFileForInput(Location location, String packageName, String relativeName)
      throws IOException {
    return delegate.getFileForInput(location, packageName, relativeName);
  }

  @Override
  public FileObject getFileForOutput(
      Location location, String packageName, String relativeName, FileObject sibling)
      throws IOException {
    return delegate.getFileForOutput(location, packageName, relativeName, sibling);
  }

  @Override
  public void flush() throws IOException {
    delegate.flush();
  }

  @Override
  public void close() {
    try {
      delegate.close();
    } catch (IOException e) {
      throw Throwables.propagate(e);
    } finally {
      recursiveDelete(getOnlyElement(delegate.getLocation(StandardLocation.SOURCE_OUTPUT)));
      recursiveDelete(getOnlyElement(delegate.getLocation(StandardLocation.CLASS_OUTPUT)));
    }
  }

  private static void recursiveDelete(File file) {
    File[] files = file.listFiles();
    if (files != null) {
      for (File each : files) {
        recursiveDelete(each);
      }
    }
    file.delete();
  }
}
