/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache 2.0 License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.squidb.processor.plugins.defaults.properties;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.yahoo.squidb.annotations.tables.IndexOrder;
import com.yahoo.squidb.annotations.tables.constraints.IndexedColumn;
import com.yahoo.squidb.annotations.tables.constraints.PrimaryKey;
import com.yahoo.squidb.annotations.tables.constraints.PrimaryKeyColumns;
import com.yahoo.squidb.processor.SqlUtils;
import com.yahoo.squidb.processor.TypeConstants;
import com.yahoo.squidb.processor.data.ModelSpec;
import com.yahoo.squidb.processor.data.TableModelSpecWrapper;
import com.yahoo.squidb.processor.plugins.PluginEnvironment;
import com.yahoo.squidb.processor.plugins.defaults.properties.generators.BasicBlobPropertyGenerator;
import com.yahoo.squidb.processor.plugins.defaults.properties.generators.BasicBooleanPropertyGenerator;
import com.yahoo.squidb.processor.plugins.defaults.properties.generators.BasicDoublePropertyGenerator;
import com.yahoo.squidb.processor.plugins.defaults.properties.generators.BasicIntegerPropertyGenerator;
import com.yahoo.squidb.processor.plugins.defaults.properties.generators.BasicLongPropertyGenerator;
import com.yahoo.squidb.processor.plugins.defaults.properties.generators.BasicStringPropertyGenerator;
import com.yahoo.squidb.processor.plugins.defaults.properties.generators.BasicTableModelPropertyGenerator;
import com.yahoo.squidb.processor.plugins.defaults.properties.generators.RowidPropertyGenerator;
import com.yahoo.squidb.processor.plugins.defaults.properties.generators.interfaces.PropertyGenerator;
import com.yahoo.squidb.processor.plugins.defaults.properties.generators.interfaces.TableModelPropertyGenerator;
import com.yahoo.squidb.processor.writers.TableModelFileWriter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;

/**
 * This plugin controls generating property declarations, getters, and setters for fields in a table model. It can
 * create instances of {@link TableModelPropertyGenerator} for each of the basic supported column types
 * (String, int, long, etc.)
 * <p>
 * Users who want to tweak the default field handling for table models can subclass this plugin and override the
 * protected methods for determining PropertyGenerator subclasses ({@link #getStringPropertyGenerator()},
 * {@link #getLongPropertyGenerator()}, etc.). Such a user plugin should be registered with "high" priority so it takes
 * precedence over the default version of this plugin.
 */
public class TableModelSpecFieldPlugin extends BaseFieldPlugin<TableModelSpecWrapper, TableModelPropertyGenerator> {

    public static final String DEFAULT_ROWID_PROPERTY_NAME = "ROWID";
    private static final String METADATA_KEY_ROWID_ALIAS_PROPERTY_GENERATOR = "rowidAliasPropertyGenerator";
    private static final String METADATA_KEY_HAS_PRIMARY_KEY = "hasPrimaryKey";
    private static final String METADATA_KEY_TABLE_PRIMARY_KEY_NAME = "tablePrimaryKeyName";

    private Map<TypeName, Class<? extends BasicTableModelPropertyGenerator>> generatorMap = new HashMap<>();

    public TableModelSpecFieldPlugin() {
        super();
        registerBasicPropertyGenerators();
    }

    private void registerBasicPropertyGenerators() {
        registerHandledTypes(BasicStringPropertyGenerator.handledColumnTypes(), getStringPropertyGenerator());
        registerHandledTypes(BasicLongPropertyGenerator.handledColumnTypes(), getLongPropertyGenerator());
        registerHandledTypes(BasicIntegerPropertyGenerator.handledColumnTypes(), getIntegerPropertyGenerator());
        registerHandledTypes(BasicDoublePropertyGenerator.handledColumnTypes(), getDoublePropertyGenerator());
        registerHandledTypes(BasicBooleanPropertyGenerator.handledColumnTypes(), getBooleanPropertyGenerator());
        registerHandledTypes(BasicBlobPropertyGenerator.handledColumnTypes(), getBlobPropertyGenerator());
    }

    private void registerHandledTypes(List<TypeName> handledTypes,
            Class<? extends BasicTableModelPropertyGenerator> generatorClass) {
        for (TypeName type : handledTypes) {
            generatorMap.put(type, generatorClass);
        }
    }

    @Override
    public boolean init(ModelSpec<?, ?> modelSpec, PluginEnvironment pluginEnv) {
        boolean result = super.init(modelSpec, pluginEnv);
        if (result) {
            initTablePrimaryKeyColumns();
        }
        return result;
    }

    private void initTablePrimaryKeyColumns() {
        PrimaryKeyColumns tablePrimaryKey = modelSpec.getModelSpecElement().getAnnotation(PrimaryKeyColumns.class);
        if (tablePrimaryKey != null) {
            IndexedColumn[] indexedColumns = tablePrimaryKey.indexedColumns();
            String[] columnNames = tablePrimaryKey.columns();
            if (indexedColumns.length == 1) {
                modelSpec.putMetadata(METADATA_KEY_TABLE_PRIMARY_KEY_NAME, indexedColumns[0].name());
            } else if (columnNames.length == 1) {
                modelSpec.putMetadata(METADATA_KEY_TABLE_PRIMARY_KEY_NAME, columnNames[0]);
            }

            modelSpec.putMetadata(METADATA_KEY_HAS_PRIMARY_KEY, true);
        }
    }

    @Override
    protected Class<TableModelSpecWrapper> getHandledModelSpecClass() {
        return TableModelSpecWrapper.class;
    }

    @Override
    protected boolean hasPropertyGeneratorForField(VariableElement field, TypeName fieldType) {
        return !TypeConstants.isConstant(field) && generatorMap.containsKey(fieldType);
    }

    @Override
    public boolean processVariableElement(VariableElement field, TypeName fieldType) {
        if (field.getAnnotation(PrimaryKey.class) != null) {
            return handlePrimaryKeyField(field, fieldType);
        } else {
            return super.processVariableElement(field, fieldType);
        }
    }

    private boolean handlePrimaryKeyField(VariableElement field, TypeName fieldType) {
        if (modelSpec.isVirtualTable()) {
            modelSpec.logError("Virtual tables cannot declare a custom primary key", field);
        } else if (modelSpec.hasMetadata(METADATA_KEY_HAS_PRIMARY_KEY)) {
            modelSpec.logError("Only a single primary key per table can be specified. Make sure you did not declare a "
                    + "table level primary key using @PrimaryKeyColumns on your model spec, and that no other field in "
                    + "this spec is annotated with @PrimaryKey. If you want a multi-column primary key, you can "
                    + "specify one by annotating your spec class with @PrimaryKeyColumns.", field);
        } else {
            boolean result = false;
            if (isIntegerPrimaryKey(field, fieldType)) {
                PropertyGenerator propertyGenerator = getPropertyGenerator(field, fieldType);
                if (propertyGenerator != null) {
                    modelSpec.putMetadata(METADATA_KEY_ROWID_ALIAS_PROPERTY_GENERATOR, propertyGenerator);
                    result = true;
                }
            } else {
                if (TypeConstants.isIntegerType(fieldType) &&
                        field.getAnnotation(PrimaryKey.class).order() == IndexOrder.DESC) {
                    pluginEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "INTEGER PRIMARY KEY DESC columns " +
                            "do not act as a rowid alias due to an obscure SQLite corner case behavior. This column " +
                            "will still be a primary key but will not be a rowid alias. Any autoincrement setting " +
                            "will also be ignored.", field);
                }
                result = super.processVariableElement(field, fieldType);
            }
            if (result) {
                modelSpec.putMetadata(METADATA_KEY_HAS_PRIMARY_KEY, true);
            }
            return result;
        }
        return false;
    }

    @Override
    protected TableModelPropertyGenerator getPropertyGenerator(VariableElement field, TypeName fieldType) {
        Class<? extends BasicTableModelPropertyGenerator> generatorClass;
        if (isIntegerPrimaryKey(field, fieldType)) {
            // Force INTEGER PRIMARY KEY properties to be LongProperty, even if declared as e.g. int
            generatorClass = getRowidPropertyGenerator();
        } else {
            generatorClass = generatorMap.get(fieldType);
        }
        return getPropertyGenerator(generatorClass, field, fieldType);
    }

    private BasicTableModelPropertyGenerator getPropertyGenerator(
            Class<? extends BasicTableModelPropertyGenerator> generatorClass,
            VariableElement field, TypeName fieldType) {
        try {
            BasicTableModelPropertyGenerator propertyGenerator = generatorClass.getConstructor(ModelSpec.class,
                    VariableElement.class, PluginEnvironment.class).newInstance(modelSpec, field, pluginEnv);
            if (DEFAULT_ROWID_PROPERTY_NAME.equalsIgnoreCase(propertyGenerator.getColumnName()) ||
                    DEFAULT_ROWID_PROPERTY_NAME.equalsIgnoreCase(propertyGenerator.getPropertyName())) {
                modelSpec.logError("Columns in a table model spec cannot be named rowid, as "
                        + "they would clash with the SQLite rowid column used for SquiDB bookkeeping", field);
                return null;
            }

            String columnName = propertyGenerator.getColumnName();
            if (!SqlUtils.checkIdentifier(columnName, "column", modelSpec, field, pluginEnv.getMessager())) {
                return null;
            }

            // Check to see if this column is an INTEGER PRIMARY KEY
            // that was declared at the table level using @PrimaryKeyColumns
            if (modelSpec.hasMetadata(METADATA_KEY_TABLE_PRIMARY_KEY_NAME) &&
                    columnName.equals(modelSpec.getMetadata(METADATA_KEY_TABLE_PRIMARY_KEY_NAME)) &&
                    TypeConstants.isIntegerType(fieldType) &&
                    !(getRowidPropertyGenerator().isAssignableFrom(generatorClass))) {
                propertyGenerator = getPropertyGenerator(getRowidPropertyGenerator(), field, fieldType);
                if (propertyGenerator != null) {
                    modelSpec.putMetadata(METADATA_KEY_ROWID_ALIAS_PROPERTY_GENERATOR, propertyGenerator);
                }
            }

            return propertyGenerator;
        } catch (Exception e) {
            e.printStackTrace();
            modelSpec.logError("Exception instantiating PropertyGenerator: " + generatorClass + ", " + e, field);
        }
        return null;
    }

    private boolean isIntegerPrimaryKey(VariableElement field, TypeName fieldType) {
        return TypeConstants.isIntegerType(fieldType) &&
                field.getAnnotation(PrimaryKey.class) != null &&
                field.getAnnotation(PrimaryKey.class).order() != IndexOrder.DESC;
    }

    @Override
    public void afterProcessVariableElements() {
        RowidPropertyGenerator rowidPropertyGenerator;
        if (modelSpec.hasMetadata(METADATA_KEY_ROWID_ALIAS_PROPERTY_GENERATOR)) {
            rowidPropertyGenerator = modelSpec.getMetadata(METADATA_KEY_ROWID_ALIAS_PROPERTY_GENERATOR);
        } else {
            rowidPropertyGenerator = new RowidPropertyGenerator(modelSpec, "rowid",
                    DEFAULT_ROWID_PROPERTY_NAME, pluginEnv);
            modelSpec.putMetadata(METADATA_KEY_ROWID_ALIAS_PROPERTY_GENERATOR, rowidPropertyGenerator);
        }
        // Make sure rowid or rowid alias is at the front of the list
        modelSpec.getPropertyGenerators().remove(rowidPropertyGenerator);
        modelSpec.getPropertyGenerators().add(0, rowidPropertyGenerator);

        // Sanity check to make sure there is exactly 1 RowidPropertyGenerator
        RowidPropertyGenerator foundRowidPropertyGenerator = null;
        for (PropertyGenerator generator : modelSpec.getPropertyGenerators()) {
            if (generator instanceof RowidPropertyGenerator) {
                if (foundRowidPropertyGenerator != null) {
                    modelSpec.logError("Found redundant rowid property generator for property"
                            + generator.getPropertyName() + ". Rowid property generator " +
                            foundRowidPropertyGenerator.getPropertyName() + " already exists", generator.getField());
                } else {
                    foundRowidPropertyGenerator = (RowidPropertyGenerator) generator;
                }
            }
        }
    }

    @Override
    public void afterDeclareSchema(TypeSpec.Builder builder) {
        RowidPropertyGenerator rowidPropertyGenerator = modelSpec.getMetadata(METADATA_KEY_ROWID_ALIAS_PROPERTY_GENERATOR);
        writeRowidSupportMethods(builder, rowidPropertyGenerator.getPropertyName());
    }

    private void writeRowidSupportMethods(TypeSpec.Builder builder, String propertyName) {
        // Write TABLE.setRowIdProperty call
        CodeBlock block = CodeBlock.of("$L.setRowIdProperty($L);\n", TableModelFileWriter.TABLE_NAME, propertyName);
        builder.addStaticBlock(block);

        // Write getRowIdProperty() method
        MethodSpec.Builder params = MethodSpec.methodBuilder("getRowIdProperty")
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeConstants.LONG_PROPERTY)
                .addStatement("return $L", propertyName)
                .addAnnotation(Override.class)
                .addAnnotation(Nonnull.class);
        builder.addMethod(params.build());
    }

    @Override
    public void declareMethodsOrConstructors(TypeSpec.Builder builder) {
        // If rowid property generator hasn't already done it, need to generate
        // overridden setRowId with appropriate return type
        if (!pluginEnv.hasSquidbOption(PluginEnvironment.OPTIONS_DISABLE_DEFAULT_GETTERS_AND_SETTERS)) {
            RowidPropertyGenerator rowidPropertyGenerator = modelSpec
                    .getMetadata(METADATA_KEY_ROWID_ALIAS_PROPERTY_GENERATOR);
            if (rowidPropertyGenerator != null && !"setRowId".equals(rowidPropertyGenerator.setterMethodName())) {
                MethodSpec.Builder params = MethodSpec.methodBuilder("setRowId")
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(TypeName.LONG, "rowid")
                        .returns(modelSpec.getGeneratedClassName())
                        .addAnnotation(Override.class)
                        .addAnnotation(Nonnull.class)
                        .addStatement("super.setRowId(rowid)")
                        .addStatement("return this");
                builder.addMethod(params.build());
            }
        }
    }

    /**
     * @return the generator class this plugin should use for handling String fields
     */
    protected Class<? extends BasicStringPropertyGenerator> getStringPropertyGenerator() {
        return BasicStringPropertyGenerator.class;
    }

    /**
     * @return the generator class this plugin should use for handling long fields
     */
    protected Class<? extends BasicLongPropertyGenerator> getLongPropertyGenerator() {
        return BasicLongPropertyGenerator.class;
    }

    /**
     * @return the generator class this plugin should use for handling integer primary key fields
     */
    protected Class<? extends RowidPropertyGenerator> getRowidPropertyGenerator() {
        return RowidPropertyGenerator.class;
    }

    /**
     * @return the generator class this plugin should use for handling integer fields
     */
    protected Class<? extends BasicIntegerPropertyGenerator> getIntegerPropertyGenerator() {
        return BasicIntegerPropertyGenerator.class;
    }

    /**
     * @return the generator class this plugin should use for handling double or float fields
     */
    protected Class<? extends BasicDoublePropertyGenerator> getDoublePropertyGenerator() {
        return BasicDoublePropertyGenerator.class;
    }

    /**
     * @return the generator class this plugin should use for handling boolean fields
     */
    protected Class<? extends BasicBooleanPropertyGenerator> getBooleanPropertyGenerator() {
        return BasicBooleanPropertyGenerator.class;
    }

    /**
     * @return the generator class this plugin should use for handling blob fields
     */
    protected Class<? extends BasicBlobPropertyGenerator> getBlobPropertyGenerator() {
        return BasicBlobPropertyGenerator.class;
    }
}
