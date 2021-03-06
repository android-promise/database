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

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import promise.database.compiler.migration.DatabaseMetaData
import promise.database.compiler.utils.LogUtil
import promise.database.compiler.utils.getDatabaseVersion
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import javax.lang.model.element.TypeElement
import kotlin.math.max

class DatabaseMetaDataWriter
(
    private val databaseElement: TypeElement,
    private val schemasPath: String) {

  init {
    val dir = File(schemasPath)
    dir.parentFile.mkdirs()
    dir.mkdirs()
  }

  var currentDatabaseMetaData: DatabaseMetaData? = null

  @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
  fun loadCurrentMetaData() = try {
    val file = File(schemasPath)
    if (file.exists() && file.isDirectory) {
      var files = file.listFiles { pathname -> pathname.isFile && pathname.name.endsWith(".yml") }
          .toList()
      if (files.isNotEmpty()) {
        val currentFile: File
        if (files.size == 1) currentFile = files[0] else {
          files = files.sortedWith(Comparator<File> { o1, o2 -> o1.name.compareTo(o2.name) })
          currentFile = files.last()
        }
        //LogUtil.n("Current file: ${currentFile.name}")
        val om = ObjectMapper(YAMLFactory())
        //xmlMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        val fileWriter = FileReader(currentFile)
        currentDatabaseMetaData = om.readValue(fileWriter, DatabaseMetaData::class.java)
        fileWriter.close()
      } else currentDatabaseMetaData = DatabaseMetaData()
    } else currentDatabaseMetaData = DatabaseMetaData()
  } catch (e: JsonProcessingException) {
    LogUtil.e(e)
  } catch (e: IOException) {
    LogUtil.e(e)
  }

  fun process(
      //elements: Map<Element, String>
  ) {
    val databaseMetaData = DatabaseMetaData()
    if (TableMetaDataWriter.tableMetaData.isNotEmpty()) {
      databaseMetaData.tableMetaData = TableMetaDataWriter.tableMetaData
      val generatedVersion = TableMetaDataWriter.finalMaxDbVersion()
      databaseMetaData.dbVersion = max(databaseElement.getDatabaseVersion(), generatedVersion)
    }
    serializeToXML(databaseMetaData, databaseMetaData.dbVersion)
  }

  private fun serializeToXML(obj: Any, version: Int) {
    try {
      val file = File(schemasPath + File.separator + version + ".yml")
      if (file.exists()) file.delete()
      file.createNewFile()
      val om = ObjectMapper(YAMLFactory())
      // serialize our Object into XML string
      val ymlString = om.writeValueAsString(obj)
      // write XML string to file
      val fileWriter = FileWriter(file)
      fileWriter.write(ymlString)
      fileWriter.close()
    } catch (e: JsonProcessingException) {
      LogUtil.e(e)
    } catch (e: IOException) {
      LogUtil.e(e)
    }
  }
}