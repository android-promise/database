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
import com.squareup.javapoet.JavaFile
import promise.database.TypeConverter
import promise.database.compiler.utils.LogUtil
import promise.database.compiler.utils.Utils
import java.util.*
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

class TypeConverterAnnotatedProcessor(private val processingEnv: ProcessingEnvironment) : AnnotatedClassProcessor() {

  override fun process(environment: RoundEnvironment?): List<JavaFile.Builder?>? {
    val typeConverters = environment?.getElementsAnnotatedWith(TypeConverter::class.java)
    if (typeConverters != null) {
      if (typeConverters.size > 1)
        LogUtil.e(Exception("There can only be one typeConverter in the module"))
      else if (typeConverters.size == 1) try {
        typeConverter = typeConverters.first() as TypeElement
        return Collections.singletonList(processElement(typeConverter!!))
      } catch (e: Throwable) {
        LogUtil.e(e, typeConverter)
      }
    }
    return null
  }

  private fun processElement(element: TypeElement): JavaFile.Builder {
    val className = element.simpleName.toString()
    val pack = processingEnv.elementUtils.getPackageOf(element).toString()
    return Utils.generateInstanceProviderHolder(ClassName.get(pack, className))
  }
  ;
  companion object {
    var typeConverter: TypeElement? = null
  }
}