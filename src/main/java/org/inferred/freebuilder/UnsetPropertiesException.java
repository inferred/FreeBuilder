package org.inferred.freebuilder;

import com.google.common.collect.ImmutableSet;

import java.util.EnumSet;
import java.util.Set;

public class UnsetPropertiesException extends IllegalStateException {
    private final Set<String> properties;

    public UnsetPropertiesException(String msg, EnumSet<?> unsetProperties) {
        super(msg);
        ImmutableSet.Builder<String> properties = new ImmutableSet.Builder<String>();
        for (Enum<?> unsetProperty : unsetProperties) {
            properties.add(unsetProperty.toString());
        }
        this.properties = properties.build();
    }

    public Set<String> getUnsetProperties() {
        return properties;
    }
}
