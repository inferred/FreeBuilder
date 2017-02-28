package org.inferred.freebuilder.processor;

import org.inferred.freebuilder.processor.Metadata.Property;

/** Utility methods for method names used in builders. */
public class BuilderMethods {

  public static String getter(Property property) {
    return property.getGetterName();
  }

  public static String setter(Property property) {
    if (property.isUsingBeanConvention()) {
      return "set" + property.getCapitalizedName();
    } else {
      return property.getName();
    }
  }

  public static String nullableSetter(Property property) {
    if (property.isUsingBeanConvention()) {
      return "setNullable" + property.getCapitalizedName();
    } else {
      return "nullable" + property.getCapitalizedName();
    }
  }

  public static String getBuilderMethod(Property property) {
    if (property.isUsingBeanConvention()) {
      return "get" + property.getCapitalizedName() + "Builder";
    } else {
      return property.getName() + "Builder";
    }
  }

  public static String addMethod(Property property) {
    return "add" + property.getCapitalizedName();
  }

  public static String addAllMethod(Property property) {
    return "addAll" + property.getCapitalizedName();
  }

  public static String addCopiesMethod(Property property) {
    return "addCopiesTo" + property.getCapitalizedName();
  }

  public static String putMethod(Property property) {
    return "put" + property.getCapitalizedName();
  }

  public static String putAllMethod(Property property) {
    return "putAll" + property.getCapitalizedName();
  }

  public static String removeMethod(Property property) {
    return "remove" + property.getCapitalizedName();
  }

  public static String removeAllMethod(Property property) {
    return "removeAll" + property.getCapitalizedName();
  }

  public static String setComparatorMethod(Property property) {
    return "setComparatorFor" + property.getCapitalizedName();
  }

  public static String setCountMethod(Property property) {
    return "setCountOf" + property.getCapitalizedName();
  }

  public static String mapper(Property property) {
    return "map" + property.getCapitalizedName();
  }

  public static String mutator(Property property) {
    return "mutate" + property.getCapitalizedName();
  }

  public static String clearMethod(Property property) {
    return "clear" + property.getCapitalizedName();
  }

  private BuilderMethods() {}
}
