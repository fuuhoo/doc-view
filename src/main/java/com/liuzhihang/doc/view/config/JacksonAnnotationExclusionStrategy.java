package com.liuzhihang.doc.view.config;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

public class JacksonAnnotationExclusionStrategy implements ExclusionStrategy {
    @Override
    public boolean shouldSkipField(FieldAttributes f) {
        return f.getAnnotation(com.fasterxml.jackson.annotation.JsonIgnore.class) != null;
    }

    @Override
    public boolean shouldSkipClass(Class<?> clazz) {
        return false;
    }
}