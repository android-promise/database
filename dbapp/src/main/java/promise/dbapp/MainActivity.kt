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

package promise.dbapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import promise.commons.model.Result
import promise.dbapp.model.ComplexRecord
import promise.dbapp.model.AppDatabase
import promise.model.IdentifiableList

class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    setSupportActionBar(toolbar)

    fab.setOnClickListener { view ->
      Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
          .setAction("Action", null).show()
    }
  }

  override fun onPostCreate(savedInstanceState: Bundle?) {
    super.onPostCreate(savedInstanceState)

    val database = AppDatabase()
    database.allComplexModels(Result<IdentifiableList<out ComplexRecord>, Throwable>()
        .withCallBack {
          if (it.isNotEmpty()) {
            complex_values_textview.text = it.toString()
          } else complex_values_textview.text = "empty list"
        }
        .withErrorCallBack { complex_values_textview.text = it.message })

    clear_button.setOnClickListener {
      database.deleteAllAsync()
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe {
            complex_values_textview.text = ""
          }
    }

    val complexRecordTable = AppDatabase.complexModelTable
    var items = complexRecordTable.findAll()
    if (items.isEmpty()) {
      complexRecordTable.save(IdentifiableList(ComplexRecord.someModels()))
      items = complexRecordTable.findAll()
    }
    complex_values_textview.text = items.toString()
  }
}
