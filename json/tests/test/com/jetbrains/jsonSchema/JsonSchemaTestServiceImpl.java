package com.jetbrains.jsonSchema;

import com.intellij.openapi.project.Project;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;


public class JsonSchemaTestServiceImpl extends JsonSchemaServiceImpl {

  public static void setProvider(JsonSchemaFileProvider newProvider) {
    provider = newProvider;
  }

  private static JsonSchemaFileProvider provider;

  public JsonSchemaTestServiceImpl(@Nullable Project project) {
    super(project);
  }


  @NotNull
  @Override
  protected JsonSchemaProviderFactory[] getProviderFactories() {
    return new JsonSchemaProviderFactory[]{
      new JsonSchemaProviderFactory() {
        @Override
        public List getProviders(@Nullable Project project) {
          return Collections.singletonList(provider);
        }
      }
    };
  }
}
