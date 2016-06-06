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
package org.inferred.freebuilder.client.rpc;

import com.google.gwt.core.client.GWT;
import com.google.gwt.junit.client.GWTTestCase;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.ServiceDefTarget;

public class NestedListTest extends GWTTestCase {

    private static final int RPC_TIMEOUT = 15000;
    private static final String MODULE_NAME = "org.inferred.freebuilder.TestServer";
    private static final NestedListGwtType VALUE = new NestedListGwtType.Builder()
            .setItem(new StringListGwtType.Builder()
                    .addNames("john", "alice", "sam"))
            .build();

    private NestedListTestServiceAsync customFieldSerializerTestService;

    @Override
    public String getModuleName() {
        return MODULE_NAME;
    }

    @Override
    protected void gwtSetUp() throws Exception {
        super.gwtSetUp();
        delayTestFinish(RPC_TIMEOUT);
    }

    public void testSerialization() {
        NestedListTestServiceAsync service = getServiceAsync();
        service.echo(VALUE, new AsyncCallback<NestedListGwtType>() {
            @Override
            public void onFailure(Throwable caught) {
                throw new AssertionError(caught);
            }

            @Override
            public void onSuccess(NestedListGwtType result) {
                assertEquals(VALUE, result);
                finishTest();
            }
        });
    }

    private NestedListTestServiceAsync getServiceAsync() {
        if (customFieldSerializerTestService == null) {
            customFieldSerializerTestService =
                    (NestedListTestServiceAsync) GWT.create(NestedListTestService.class);
            ((ServiceDefTarget) customFieldSerializerTestService)
                    .setServiceEntryPoint(GWT.getModuleBaseURL() + "nested-list");
        }
        return customFieldSerializerTestService;
    }
}
