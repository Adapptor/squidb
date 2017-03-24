/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache 2.0 License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.squidb.processor.plugins.defaults.properties.generators;

import com.squareup.javapoet.TypeName;
import com.yahoo.squidb.annotations.tables.defaults.DefaultDouble;
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
 * for handling double fields
 */
public class BasicDoublePropertyGenerator extends BasicTableModelPropertyGenerator {

    public static List<TypeName> handledColumnTypes() {
        return Arrays.asList(TypeName.FLOAT, TypeName.FLOAT.box(),
                TypeName.DOUBLE, TypeName.DOUBLE.box());
    }

    public BasicDoublePropertyGenerator(ModelSpec<?, ?> modelSpec, String columnName, PluginEnvironment pluginEnv) {
        super(modelSpec, columnName, pluginEnv);
    }

    public BasicDoublePropertyGenerator(ModelSpec<?, ?> modelSpec, String columnName,
            String propertyName, PluginEnvironment pluginEnv) {
        super(modelSpec, columnName, propertyName, pluginEnv);
    }

    public BasicDoublePropertyGenerator(ModelSpec<?, ?> modelSpec, VariableElement field, PluginEnvironment pluginEnv) {
        super(modelSpec, field, pluginEnv);
    }

    @Override
    public TypeName getTypeForAccessors() {
        return TypeName.DOUBLE.box();
    }

    @Override
    public TypeName getPropertyType() {
        return TypeConstants.DOUBLE_PROPERTY;
    }

    @Override
    protected DefaultValueAnnotationHandler<?, ?> getDefaultValueAnnotationHandler() {
        return new DefaultValueAnnotationHandler<DefaultDouble, Double>() {
            @Override
            public Class<DefaultDouble> getAnnotationClass() {
                return DefaultDouble.class;
            }

            @Override
            protected Double getPrimitiveDefaultValueFromAnnotation(DefaultDouble annotation) {
                return annotation.value();
            }
        };
    }
}
