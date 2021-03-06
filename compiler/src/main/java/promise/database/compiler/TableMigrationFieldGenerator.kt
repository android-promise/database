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
import com.squareup.javapoet.MethodSpec
import org.jetbrains.annotations.NotNull
import promise.database.HasMany
import promise.database.Ignore
import promise.database.MigrationOptions
import promise.database.compiler.migration.TableMigration
import promise.database.compiler.migration.VersionChange
import promise.database.compiler.utils.List
import javax.lang.model.element.Element
import javax.lang.model.element.Modifier

class TableMigrationFieldGenerator(
    private val entityElement: Element,
    private val elements: Map<Element, String>) : CodeGenerator<MethodSpec?> {

  private fun buildMigration(
      versionChange: VersionChange,
      tableMigrations: List<out TableMigration>,
      codeBlock: CodeBlock.Builder) {
    codeBlock.add("if (v1 == ${versionChange.fromVersion} && v2 == ${versionChange.toVersion}) {\n")
    codeBlock.indent()
    tableMigrations.groupBy { it.action }
        .forEach { category ->
          when (category.name()) {
            MigrationOptions.CREATE -> {
              val list: StringBuilder = StringBuilder("")
              category.list().forEachIndexed { index, tableMigration ->
                list.append("${tableMigration.field}")
                if (index != category.list().size - 1) list.append(", ")
              }
              codeBlock.addStatement("addColumns(x, $list)")
            }
            MigrationOptions.DROP -> {
              val list: StringBuilder = StringBuilder("")
              category.list().forEachIndexed { index, tableMigration ->
                list.append("${tableMigration.field}")
                if (index != category.list().size - 1) list.append(", ")
              }
              codeBlock.addStatement("dropColumns(x, $list)")
            }
            MigrationOptions.CREATE_INDEX -> category.list().forEach {
              codeBlock.addStatement("addIndex(x, \"${it.field}\")")
            }

          }
        }
    codeBlock.unindent()
    codeBlock.add("}\n")
  }

  override fun generate(): MethodSpec? {
    /**
     * ignore fields with Has many relation and ones with Ignore annotation
     */
    val elements2 = elements.filter {
      it.key.getAnnotation(HasMany::class.java) == null &&
          it.key.getAnnotation(Ignore::class.java) == null
    }
    val tableMetaDataWriter = TableMetaDataWriter(entityElement, elements2)


    val tableMigrations = tableMetaDataWriter.process()
//
//    tableMigrations.addAll(elements2.filter { it.key.getAnnotation(RenameColumn::class.java) != null }
//        .map {
//          val renameColumn = it.key.getAnnotation(RenameColumn::class.java)
//          RenameColumnTableMigration().apply {
//            versionChange = VersionChange().apply {
//              fromVersion = renameColumn.fromVersion
//              toVersion = renameColumn.toVersion
//            }
//            field = it.key.getNameOfColumn()
//            oldColumnName = renameColumn.oldName
//          }
//        })
//
    if (tableMigrations.isNotEmpty()) {
      val codeBlock = CodeBlock.builder()
      tableMigrations.groupBy { it.versionChange }
          .forEach {
            buildMigration(it.name()!!, it.list(), codeBlock)
          }
      return MethodSpec.methodBuilder("onUpgrade")
          .addParameter(
              ClassName.get("androidx.sqlite.db", "SupportSQLiteDatabase")
                  .annotated(AnnotationSpec.builder(NotNull::class.java).build()),
              "x")
          .addParameter(Integer::class.javaPrimitiveType, "v1")
          .addParameter(Integer::class.javaPrimitiveType, "v2")
          .addAnnotation(Override::class.java)
          .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
          .addException(ClassName.get("promise.db", "TableError"))
          .addCode(codeBlock.build())
          .build()
    }
    return null
  }
}