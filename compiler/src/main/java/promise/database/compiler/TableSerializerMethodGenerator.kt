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

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.MethodSpec
import promise.database.compiler.utils.ConverterTypes
import promise.database.compiler.utils.JavaUtils
import promise.database.compiler.utils.capitalizeFirst
import promise.database.compiler.utils.checkIfHasTypeConverter
import promise.database.compiler.utils.getConverterCompatibleMethod
import promise.database.compiler.utils.isElementAnnotatedAsRelation
import promise.database.compiler.utils.isSameAs
import promise.database.compiler.utils.toTypeName
import javax.lang.model.element.Element
import javax.lang.model.element.Modifier

class TableSerializerMethodGenerator(
    private val typeDataTypePack: String,
    private val typeDataType: String,
    private val columns: List<Pair<Pair<String, Element>, String>>) : CodeGenerator<MethodSpec> {

  override fun generate(): MethodSpec {

    val codeBlock = CodeBlock.builder()

    codeBlock.addStatement("ContentValues values = new ContentValues()")

    columns.forEach {
      generatePutStatement(codeBlock, it.first.first, it.second, it.first.second)
    }
    codeBlock.addStatement("return values")
    return MethodSpec.methodBuilder("serialize")
        .addModifiers(Modifier.PUBLIC)
        .addParameter(ClassName.get(typeDataTypePack, typeDataType), "t")
        .addAnnotation(Override::class.java)
        .returns(ClassName.get("android.content", "ContentValues"))
        .addCode(codeBlock.build())
        .build()
  }

  private fun generatePutStatement(
      codeBlock: CodeBlock.Builder,
      typeVariable: String, columnName: String, varTypeName: Element) {
    if (varTypeName.toTypeName().isSameAs(Boolean::class.java)) {
      codeBlock.addStatement("values.put(${columnName}.getName(), t.is${typeVariable.capitalizeFirst()}() ? 1 : 0)")
      return
    }
    if (varTypeName.checkIfHasTypeConverter()) {
      val executableFn = varTypeName.getConverterCompatibleMethod(ConverterTypes.SERIALIZER)
      if (executableFn != null)
        codeBlock.addStatement("values.put(${columnName}.getName(), typeConverter.${executableFn.simpleName}(t.get${typeVariable.capitalizeFirst()}()))")
    } else if (varTypeName.isElementAnnotatedAsRelation())
      codeBlock.add(JavaUtils.generateSerializerRelationPutStatement(varTypeName, columnName)) else codeBlock.addStatement("values.put(${columnName}.getName(), t.get${typeVariable.capitalizeFirst()}())")
  }
}