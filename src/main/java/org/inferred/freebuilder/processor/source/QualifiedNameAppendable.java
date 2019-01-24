package org.inferred.freebuilder.processor.source;

interface QualifiedNameAppendable {
  void append(char c);
  void append(CharSequence csq);
  void append(CharSequence csq, int start, int end);
  void append(QualifiedName type);
}