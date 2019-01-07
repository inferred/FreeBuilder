package org.inferred.freebuilder.processor;

import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.util.Excerpts;
import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import java.util.Set;

class GeneratedStub extends GeneratedType {

  private final QualifiedName datatype;
  private final ParameterizedType stub;

  GeneratedStub(QualifiedName datatype, ParameterizedType stub) {
    this.datatype = datatype;
    this.stub = stub;
  }

  @Override
  public QualifiedName getName() {
    return stub.getQualifiedName();
  }

  @Override
  public Set<QualifiedName> getVisibleNestedTypes() {
    return ImmutableSet.of();
  }

  @Override
  public void addTo(SourceBuilder code) {
    code.addLine("/**")
        .addLine(" * Placeholder. Create {@code %s.Builder} and subclass this type.", datatype)
        .addLine(" */")
        .add(Excerpts.generated(Processor.class))
        .addLine("abstract class %s {}", stub.declaration());
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("datatype", datatype);
    fields.add("stub", stub);
  }
}
