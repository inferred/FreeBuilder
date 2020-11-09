package org.inferred.freebuilder.processor;

import org.inferred.freebuilder.processor.Datatype.Visibility;

import java.util.Objects;

public class NameAndVisibility {

  public static NameAndVisibility of(String name, Visibility visibility) {
    return new NameAndVisibility(name, visibility);
  }

  private final String name;
  private final Visibility visibility;

  private NameAndVisibility(String name, Visibility visibility) {
    this.name = name;
    this.visibility = visibility;
  }

  public String name() {
    return name;
  }

  public Visibility visibility() {
    return visibility;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, visibility);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof NameAndVisibility)) {
      return false;
    }
    NameAndVisibility other = (NameAndVisibility) obj;
    return Objects.equals(name, other.name) && visibility == other.visibility;
  }

  @Override
  public String toString() {
    return "NameAndVisibility{name=" + name + ", visibility=" + visibility + "}";
  }
}
