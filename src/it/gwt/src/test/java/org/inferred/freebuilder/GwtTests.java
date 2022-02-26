package org.inferred.freebuilder;

import com.google.gwt.junit.tools.GWTTestSuite;
import org.inferred.freebuilder.client.rpc.MapTest;
import org.inferred.freebuilder.client.rpc.NestedListTest;
import org.inferred.freebuilder.client.rpc.OptionalTest;
import org.inferred.freebuilder.client.rpc.StringListTest;

public class GwtTests extends GWTTestSuite {

  public GwtTests() {
    addTest(new MapTest());
    addTest(new NestedListTest());
    addTest(new OptionalTest());
    addTest(new StringListTest());
  }
}
