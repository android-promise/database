/*
 * Copyright 2017, Peter Vincent
 * Licensed under the Apache License, Version 2.0, Android Promise.
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package promise.db.ompiler

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import promise.db.Persistable
import promise.db.Table
import java.io.File
import java.util.*
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedOptions
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import kotlin.collections.HashMap

@AutoService(Processor::class) // For registering the service
@SupportedSourceVersion(SourceVersion.RELEASE_8) // to support Java 8
@SupportedOptions(EntityProcessor.KAPT_KOTLIN_GENERATED_OPTION_NAME)
class EntityProcessor : AbstractProcessor() {

  override fun process(mutableSet: MutableSet<out TypeElement>?, environment: RoundEnvironment?): Boolean {
    try {
      environment?.getElementsAnnotatedWith(Persistable::class.java)
          ?.forEach {
            if (it.kind != ElementKind.CLASS) {
              processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "Only classes can be annotated")
              return true
            }
            processAnnotation(it)
          }
      return false
    } catch (e: Throwable) {
      processingEnv.messager.printMessage(Diagnostic.Kind.ERROR,
          "EntityProcessor: ${Utils.getStackTraceString(e)} \n ${
          Arrays.toString(e.stackTrace)
          }")
      return false
    }
  }

  private fun processAnnotation(element: Element) {
    val className = element.simpleName.toString()
    val pack = processingEnv.elementUtils.getPackageOf(element).toString()

    val fileName = "${className}FastTable"
    val fileBuilder = FileSpec.builder(pack, fileName)

    val entityTableAnnotation = element.getAnnotation(Persistable::class.java)

    val tableName = if (entityTableAnnotation.name.isEmpty()) "${element.simpleName}"
    else entityTableAnnotation.name

    val tableAnnotationSpec: AnnotationSpec = AnnotationSpec.builder(Table::class.java)
        .addMember(CodeBlock.builder()
            .addStatement("tableName = %S", tableName)
            .build())
        .build()

    fileBuilder.addImport(pack, className)

    val classBuilder = TypeSpec.classBuilder(fileName)
    classBuilder.addAnnotation(tableAnnotationSpec)
        .primaryConstructor(FunSpec.constructorBuilder()
            .addParameter("database",
                ClassName("promise.db", "FastDatabase"))
            .build())
        .addSuperclassConstructorParameter("database")
        .superclass(ClassName("promise.db", "FastTable")
            .parameterizedBy(ClassName(pack, className)))

    // static column block generation
    val columnsGenerator: ColumnsGenerator = ColumnsGenerator(fileBuilder, processingEnv, element.enclosedElements)
    val companionBuilder = TypeSpec.companionObjectBuilder()

    val idColumnSpec = PropertySpec.builder("idColumn", ClassName("promise.db", "Column")
        .parameterizedBy(Int::class.asTypeName()))
        .initializer(CodeBlock.of("""
                id
              """.trimIndent())
        )
        .build()

    companionBuilder.addProperty(idColumnSpec)

    val columnSpecs = columnsGenerator.generate()

    columnSpecs.values.forEach {
      companionBuilder.addProperty(it)
    }

    classBuilder.addType(companionBuilder.build())

    // column register generation
    val columnRegSpecGenerator = RegisterColumnsGenerator(fileBuilder, columnsGenerator.genColValues.map { it.second })
    classBuilder.addProperty(columnRegSpecGenerator.generate())

    // serializer generator
    val serializerGenerator = SerializerGenerator(fileBuilder, pack, className, columnsGenerator.genColValues)
    classBuilder.addFunction(serializerGenerator.generate())

    // deserializer generator
    val deserializerGenerator = DeserializerGenerator(fileBuilder, processingEnv,
        pack, className, columnsGenerator.genColValues)
    classBuilder.addFunction(deserializerGenerator.generate())

    // migrations
    val elemMap = HashMap<Element, String>()
    columnSpecs.forEach {
      elemMap[it.key.first] = it.key.second
    }
    val migrationGenerator = MigrationGenerator(fileBuilder, elemMap)
    val migrationFunc = migrationGenerator.generate()
    if (migrationFunc != null) classBuilder.addFunction(migrationFunc)
    /*


    for (enclosed in element.enclosedElements) {
      if (enclosed.kind == ElementKind.FIELD) {

        fileBuilder.addProperty(
            PropertySpec.builder(enclosed.simpleName.toString(), enclosed.asType().asTypeName(), KModifier.PRIVATE)
                .initializer("null")
                .build()
        )
        fileBuilder.addFunction(
            FunSpec.builder("get${enclosed.simpleName}")
                .returns(enclosed.asType().asTypeName())
                .addStatement("return ${enclosed.simpleName}")
                .build()
        )
        fileBuilder.addFunction(
            FunSpec.builder("set${enclosed.simpleName}")
                .addParameter(ParameterSpec.builder("${enclosed.simpleName}", enclosed.asType().asTypeName()).build())
                .addStatement("this.${enclosed.simpleName} = ${enclosed.simpleName}")
                .build()
        )
      }
    }*/

    fileBuilder.addType(classBuilder.build())
    val file = fileBuilder.build()
    val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]!!
    file.writeTo(File(kaptKotlinGeneratedDir))
  }


  override fun getSupportedAnnotationTypes(): MutableSet<String> = mutableSetOf(
      Persistable::class.java.name
  )

  companion object {
    const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
  }

}