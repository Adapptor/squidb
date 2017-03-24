/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache 2.0 License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.squidb.processor.plugins.defaults.properties.generators;

import com.squareup.javapoet.TypeName;
import com.yahoo.squidb.annotations.tables.defaults.DefaultLong;
import com.yahoo.squidb.processor.TypeConstants;
import com.yahoo.squidb.processor.data.ModelSpec;
import com.yahoo.squidb.processor.plugins.PluginEnvironment;
import com.yahoo.squidb.processor.plugins.defaults.constraints.DefaultValueAnnotationHandler;

import java.util.Arrays;
import java.util.List;

import javax.lang.model.element.VariableElement;

/**
 * An implementation of
 * {@link com.yahoo.squidb.processor.plugins.defaults.properties.generators.interfaces.TableModelPropertyGenerator}
 * for handling long fields
 */
public class BasicLongPropertyGenerator extends BasicTableModelPropertyGenerator {

    public static List<TypeName> handledColumnTypes() {
        return Arrays.asList(TypeName.LONG, TypeName.LONG.box());
    }

    public BasicLongPropertyGenerator(ModelSpec<?, ?> modelSpec, String columnName, PluginEnvironment pluginEnv) {
        super(modelSpec, columnName, pluginEnv);
    }

    public BasicLongPropertyGenerator(ModelSpec<?, ?> modelSpec, String columnName,
            String propertyName, PluginEnvironment pluginEnv) {
        super(modelSpec, columnName, propertyName, pluginEnv);
    }

    public BasicLongPropertyGenerator(ModelSpec<?, ?> modelSpec, VariableElement field, PluginEnvironment pluginEnv) {
        super(modelSpec, field, pluginEnv);
    }

    @Override
    public TypeName getTypeForAccessors() {
        return TypeName.LONG.box();
    }

    @Override
    public TypeName getPropertyType() {
        return TypeConstants.LONG_PROPERTY;
    }

    @Override
    protected DefaultValueAnnotationHandler<?, ?> getDefaultValueAnnotationHandler() {
        return new DefaultValueAnnotationHandler<DefaultLong, Long>() {
            @Override
            public Class<DefaultLong> getAnnotationClass() {
                return DefaultLong.class;
            }

            @Override
            protected Long getPrimitiveDefaultValueFromAnnotation(DefaultLong annotation) {
                return annotation.value();
            }
        };
    }
}
