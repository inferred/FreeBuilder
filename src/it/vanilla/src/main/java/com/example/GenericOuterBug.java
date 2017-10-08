package com.example;

import org.inferred.freebuilder.FreeBuilder;

public class GenericOuterBug<T> 
{
    @FreeBuilder
    public static abstract class Buildable
    {   
        public static class Builder extends GenericOuterBug_Buildable_Builder {}
        public abstract Sibling fieldWithError();
    }
    public static interface Sibling {}
}
