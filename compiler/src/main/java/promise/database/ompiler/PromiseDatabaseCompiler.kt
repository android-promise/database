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

package promise.database.ompiler

import com.google.auto.service.AutoService
import com.squareup.javapoet.JavaFile
import promise.database.DAO
import promise.database.DatabaseEntity
import promise.database.Entity
import promise.database.TypeConverter
import promise.database.ompiler.utils.LogUtil
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedOptions
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement

@AutoService(Processor::class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions(PromiseDatabaseCompiler.KAPT_JAVA_GENERATED_OPTION_NAME)
class PromiseDatabaseCompiler : AbstractProcessor() {

  override fun init(processingEnv: ProcessingEnvironment?) {
    super.init(processingEnv)
    LogUtil.initLogger(processingEnv)
  }

  override fun process(mutableSet: MutableSet<out TypeElement>?, environment: RoundEnvironment?): Boolean {
    if (mutableSet == null || mutableSet.isEmpty()) return false
    try {
      val javaFiles: ArrayList<JavaFile.Builder> = ArrayList()
      val processors: ArrayList<AnnotatedClassProcessor> = ArrayList()
      processors.add(TypeConverterAnnotatedProcessor(processingEnv))
      processors.add(EntityAnnotatedProcessor(processingEnv))
      processors.add(RelationsDaoProcessor(processingEnv))
      //processors.add(DAOAnnotatedProcessor(processingEnv))
      processors.add(DatabaseEntityAnnotatedProcessor(processingEnv))
      processors.forEach {
        val builders = it.process(environment)
        if (builders != null) javaFiles.addAll(builders.filterNotNull())
      }
      javaFiles.forEach {
        it
            .indent("\t")
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
                
                Resource https://github.com/android-promise/database
                
                Generated by Android Promise Database Compiler, do not modify
              
            """.trimIndent()
            )
            .build()
            .writeTo(processingEnv.filer)
      }
      return true
    } catch (e: Throwable) {
      LogUtil.e(e)
      return false
    }
  }

  override fun getSupportedAnnotationTypes(): MutableSet<String> = mutableSetOf(
      Entity::class.java.name,
      DatabaseEntity::class.java.name,
      TypeConverter::class.java.name,
      DAO::class.java.name
  )

  companion object {
    const val KAPT_JAVA_GENERATED_OPTION_NAME = "kapt.java.generated"
  }

}