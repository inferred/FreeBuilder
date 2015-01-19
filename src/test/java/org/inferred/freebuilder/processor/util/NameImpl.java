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
package org.inferred.freebuilder.processor.util;

import javax.lang.model.element.Name;

class NameImpl implements Name {

  private final String delegate;

  NameImpl(String delegate) {
    this.delegate = delegate;
  }

  @Override
  public int length() {
    return delegate.length();
  }

  @Override
  public char charAt(int index) {
    return delegate.charAt(index);
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    return delegate.subSequence(start, end);
  }

  @Override
  public boolean contentEquals(CharSequence cs) {
    return delegate.contentEquals(cs);
  }

  @Override
  public String toString() {
    return delegate;
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof NameImpl) && delegate.equals(((NameImpl) o).delegate);
  }
}
