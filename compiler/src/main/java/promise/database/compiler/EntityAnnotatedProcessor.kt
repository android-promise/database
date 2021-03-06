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

package promise.database.compiler

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import org.jetbrains.annotations.NotNull
import promise.database.Entity
import promise.database.compiler.utils.JavaUtils
import promise.database.compiler.utils.LogUtil
import promise.database.compiler.utils.checkIfAnyElementNeedsTypeConverter
import promise.database.compiler.utils.getTableClassNameString
import promise.database.compiler.utils.getTableEntities
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.util.ElementFilter

class EntityAnnotatedProcessor(private val processingEnv: ProcessingEnvironment,
                               private val database: TypeElement) : AnnotatedClassProcessor() {

  private val entities = database.getTableEntities(processingEnv)

  override fun process(environment: RoundEnvironment?): List<JavaFile.Builder?>? {
    val javaFiles = ArrayList<JavaFile.Builder?>()
    environment?.getElementsAnnotatedWith(Entity::class.java)
        ?.forEach { element ->
          val identifiableInterface = processingEnv.elementUtils.getTypeElement("promise.commons.model.Identifiable")
          val declaredInterface = JavaUtils.toWildCardType(processingEnv, identifiableInterface, 1)
          if (!JavaUtils.isSubTypeOfDeclaredType(processingEnv, element as TypeElement, declaredInterface))
            LogUtil.e(Exception("The Entity class ${element.simpleName} must implement Identifiable"), element)
          else javaFiles.add(processAnnotation(element))
        }
    return javaFiles
  }

  private fun processAnnotation(element: Element): JavaFile.Builder {
    if (!entities.map { it.asType().toString() }.contains(element.asType().toString()))
      LogUtil.e(Exception("Entity class ${element.simpleName} is not registered in database"), database)

/**
 if (ElementFilter.methodsIn(element.enclosedElements).none {
          it.kind == ElementKind.CONSTRUCTOR && it.parameters.isEmpty()
        }) LogUtil.e(Exception("$element must have a no args constructor"), element)
 */
   
    val className = element.simpleName.toString()
    val pack = processingEnv.elementUtils.getPackageOf(element).toString()

    val fileName = element.getTableClassNameString()

    val tableAnnotationSpec = TableAnnotationGenerator(element, processingEnv).generate()

    val classBuilder = TypeSpec.classBuilder(fileName)
        .addModifiers(Modifier.PUBLIC)
        .superclass(ParameterizedTypeName.get(
            ClassName.get("promise.db", "FastTable"),
            ClassName.get(pack, className))
        )
        .addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(
                ClassName.get("promise.db", "FastDatabase")
                    .annotated(AnnotationSpec.builder(NotNull::class.java).build()),
                "database")
            .addStatement("super(database)")
            .build())

    classBuilder.addAnnotation(tableAnnotationSpec)

    val activeRecordType = processingEnv.elementUtils.getTypeElement("promise.db.ActiveRecord")
    val declaredActiveRecordType = JavaUtils.toWildCardType(processingEnv, activeRecordType, 1)

    if (JavaUtils.isSubTypeOfDeclaredType(processingEnv, (element as TypeElement), declaredActiveRecordType))
      classBuilder.addMethod(MethodSpec.methodBuilder("createEntityInstance")
          .addAnnotation(Override::class.java)
          .addAnnotation(NotNull::class.java)
          .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
          .returns(ClassName.get(pack, className))
          .addCode("return new $className();")
          .build())

    // static column block generation
    val tableColumnPropsGenerator = TableColumnFieldsGenerator(processingEnv, element.enclosedElements)

    if (element.checkIfAnyElementNeedsTypeConverter()) {
      classBuilder.addField(FieldSpec.builder(TypeName.get(TypeConverterAnnotatedProcessor
          .typeConverter!!.asType()), "typeConverter")
          .addModifiers(Modifier.PRIVATE)
          .build())
      classBuilder.addMethod(MethodSpec.methodBuilder("setTypeConverter")
          .addModifiers(Modifier.PUBLIC)
          .addParameter(TypeName.get(TypeConverterAnnotatedProcessor
              .typeConverter!!.asType()), "typeConverter")
          .addStatement("this.typeConverter = typeConverter")
          .build())
    }

    val idColumnSpec = FieldSpec.builder(
        ParameterizedTypeName.get(
            ClassName.get("promise.db", "Column"),
            ClassName.get(Integer::class.java)
        ), "idColumn")
        .addModifiers(Modifier.STATIC, Modifier.PUBLIC, Modifier.FINAL)
        .initializer(CodeBlock.of("FastTable.getId()")).build()

    classBuilder.addField(idColumnSpec)

    val tagSpec = JavaUtils.generateEntityTableLogField(element)

    classBuilder.addField(tagSpec)

    val columnSpecs = tableColumnPropsGenerator.generate()

    columnSpecs.values.forEach {
      classBuilder.addField(it)
    }

    // column register generation
    val columnRegSpecGenerator = TableRegisteredColumnsMethodGenerator(tableColumnPropsGenerator.genColValues.map { it.second })
    classBuilder.addMethod(columnRegSpecGenerator.generate())

    // serializer generator
    val serializerGenerator = TableSerializerMethodGenerator(pack, className, tableColumnPropsGenerator.genColValues)
    classBuilder.addMethod(serializerGenerator.generate())

    // deserializer generator
    val deserializerGenerator = TableDeserializerMethodGenerator(pack, className, tableColumnPropsGenerator.genColValues)
    classBuilder.addMethod(deserializerGenerator.generate())

    // migrations
    val elemMap = HashMap<Element, String>()
    columnSpecs.forEach {
      elemMap[it.key.first] = it.key.second
    }
    val migrationGenerator = TableMigrationFieldGenerator(element, elemMap)
    val migrationFunc = migrationGenerator.generate()
    if (migrationFunc != null) classBuilder.addMethod(migrationFunc)

    return JavaFile.builder(pack, classBuilder.build())
  }

}