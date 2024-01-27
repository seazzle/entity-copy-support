package org.seazzle;

import jakarta.validation.constraints.NotNull;

import java.util.Objects;

public class CopyContext {

    private final Object originParentObject;
    private final Class<?> originParentType;
    private final Object copyOfParentObject;

    public CopyContext(@NotNull Object originParentObject, @NotNull Class<?> originParentType, @NotNull Object copyOfParentObject) {
        this.originParentObject = Objects.requireNonNull(originParentObject, "When creating a copy context, the originParentObject must not be null");
        this.originParentType = Objects.requireNonNull(originParentType, "When creating a copy context, the originParentType must not be null");
        this.copyOfParentObject = Objects.requireNonNull(copyOfParentObject, "When creating a copy context, the copyOfParentObject must not be null");
    }

    public Object getOriginParentObject() {
        return originParentObject;
    }

    public Class<?> getOriginParentType() {
        return originParentType;
    }

    public Object getCopyOfParentObject() {
        return copyOfParentObject;
    }

}
