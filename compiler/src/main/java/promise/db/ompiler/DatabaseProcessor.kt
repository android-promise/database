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

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeSpec
import promise.db.DatabaseEntity
import promise.db.ompiler.annotation.DatabaseAnnotationGenerator
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

class DatabaseProcessor(private val processingEnv: ProcessingEnvironment) {
  fun process(_mutableSet: MutableSet<out TypeElement>?, environment: RoundEnvironment?): Boolean {
    environment?.getElementsAnnotatedWith(DatabaseEntity::class.java)
        ?.forEach { element ->
          if (element.kind != ElementKind.CLASS) {
            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "Only classes can be annotated")
            return false
          }
//          val promiseDatabaseInterface = processingEnv.elementUtils.getTypeElement("promise.db.PromiseDatabase")
//          if (JavaUtils.implementsInterface(processingEnv, element as TypeElement, promiseDatabaseInterface.asType())) {
//            processElement(element)
//          }
//          else {
//            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "The database class ${element.simpleName} must implement PromiseDatabase")
//            return false
//          }
          processElement(element)
        }
    return true
  }

  private fun processElement(element: Element) {
    processingEnv.messager.printMessage(Diagnostic.Kind.OTHER, "DatabaseProcessor Processing: ${element.simpleName}")

    val className = element.simpleName.toString()
    val pack = processingEnv.elementUtils.getPackageOf(element).toString()

    val fileName = "${className}_Impl"

    //fileBuilder.addComment("Generated by Promise database compiler")

    //fileBuilder.addImport(pack, className)
    val tableAnnotationSpec = DatabaseAnnotationGenerator(processingEnv, element).generate()

    val classBuilder = TypeSpec.classBuilder(fileName)
    classBuilder.superclass(ClassName.get(pack, className))
        .addAnnotation(tableAnnotationSpec)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addMethod(MethodSpec.constructorBuilder()
            .addParameter(ClassName.get("promise.db", "FastDatabase"),"instance")
            .addModifiers(Modifier.PRIVATE)
            .addStatement("super()")
            .addStatement("this.instance = instance")
            .build())
    DatabaseCompanionPropsGenerator(classBuilder, element, processingEnv).generate()

    val abstractFuncsBuilder = DatabaseAbstractFuncsGenerator((element as TypeElement), processingEnv)

    abstractFuncsBuilder.generate()?.forEach {
      classBuilder.addMethod(it)
    }

    DatabaseCrudStubsGenerator(classBuilder, element, processingEnv).generate()

//    // static column block generation
//    val columnsGenerator: ColumnsGenerator = ColumnsGenerator(fileBuilder, processingEnv, element.enclosedElements)
//    val companionBuilder = TypeSpec.companionObjectBuilder()
//
//    val idColumnSpec = PropertySpec.builder("idColumn", ClassName("promise.db", "Column")
//        .parameterizedBy(Int::class.asTypeName()))
//        .initializer(CodeBlock.of("""
//                id
//              """.trimIndent())
//        )
//        .build()
//
//    companionBuilder.addProperty(idColumnSpec)
//
//    val columnSpecs = columnsGenerator.generate()
//
//    columnSpecs.values.forEach {
//      companionBuilder.addProperty(it)
//    }
//
//    classBuilder.addType(companionBuilder.build())
//
//    // column register generation
//    val columnRegSpecGenerator = RegisterColumnsGenerator(fileBuilder, columnsGenerator.genColValues.map { it.second })
//    classBuilder.addProperty(columnRegSpecGenerator.generate())
//
//    // serializer generator
//    val serializerGenerator = SerializerGenerator(fileBuilder, pack, className, columnsGenerator.genColValues)
//    classBuilder.addFunction(serializerGenerator.generate())
//
//    // deserializer generator
//    val deserializerGenerator = DeserializerGenerator(fileBuilder, processingEnv,
//        pack, className, columnsGenerator.genColValues)
//    classBuilder.addFunction(deserializerGenerator.generate())
//
//    // migrations
//    val elemMap = HashMap<Element, String>()
//    columnSpecs.forEach {
//      elemMap[it.key.first] = it.key.second
//    }
//    val migrationGenerator = MigrationGenerator(fileBuilder, elemMap)
//    val migrationFunc = migrationGenerator.generate()
//    if (migrationFunc != null) classBuilder.addFunction(migrationFunc)

    val fileBuilder = JavaFile.builder(pack, classBuilder.build())
    val file = fileBuilder
        .indent("    ")
        .skipJavaLangImports(true)
        .addFileComment(
            """
            Copyright 2017, Android Promise Database
            Licensed under the Apache License, Version 2.0, Android Promise.
            you may not use this file except in compliance with the License.
            You may obtain a copy of the License at
            http://www.apache.org/licenses/LICENSE-2.0
            Unless required by applicable law or agreed to in writing,
            software distributed under the License is distributed on an AS IS BASIS,
            WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied
            See the License for the specific language governing permissions and
            limitations under the License
            """.trimIndent()
            )
        .build()
    //val kaptKotlinGeneratedDir = processingEnv.options[EntityProcessor.KAPT_JAVA_GENERATED_OPTION_NAME]!!
    file.writeTo(processingEnv.filer)
  }
}