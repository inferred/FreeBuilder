/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.inferred.freebuilder.server.rpc;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import org.inferred.freebuilder.client.rpc.OptionalGwtType;
import org.inferred.freebuilder.client.rpc.OptionalTestService;

public class OptionalTestServiceImpl extends RemoteServiceServlet implements OptionalTestService {

  @Override
  public OptionalGwtType echo(OptionalGwtType mapGwtType) {
    return mapGwtType;
  }
}
