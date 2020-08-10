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
package promise.db

import android.annotation.TargetApi
import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.os.Build
import androidx.sqlite.db.SupportSQLiteDatabase
import io.reactivex.Maybe
import io.reactivex.Single
import promise.commons.model.Identifiable
import promise.commons.model.List
import promise.commons.util.DoubleConverter
import promise.model.IdentifiableList

class ReactiveFastDatabase private constructor(
    name: String?,
    version: Int,
    errorHandler: DatabaseErrorHandler) :
    FastDatabaseImpl(name, version, errorHandler),
    ReactiveCrud<SupportSQLiteDatabase> {

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  internal constructor(name: String?, version: Int, listener: Corrupt?) : this(
      name,
      version,
      DatabaseErrorHandler {
        assert(listener != null)
        listener!!.onCorrupt()
      })

  internal constructor(version: Int) : this(DEFAULT_NAME, version)

  constructor(name: String?, version: Int) : this(name, version,  null)

  private fun <T : Identifiable<Int>> dropAsync(tableCrud: TableCrud<T, in SupportSQLiteDatabase>,
                                                database: SupportSQLiteDatabase): Single<Boolean> =
      Single.fromCallable { tableCrud.onDrop(database) }

  override fun queryAsync(queryBuilder: QueryBuilder): Single<Cursor> = Single.fromCallable { query(queryBuilder) }

  @Throws(TableError::class)
  override fun <T : Identifiable<Int>> readAsync(tableCrud: TableCrud<T, in SupportSQLiteDatabase>): ReactiveTable.Extras<T> =
      object : QueryExtras<T>(tableCrud, readableDatabase) {
        override fun serialize(t: T): ContentValues = tableCrud.serialize(t)
        override fun deserialize(e: Cursor): T = tableCrud.deserialize(e)
      }

  override fun <T : Identifiable<Int>> readAllAsync(tableCrud: TableCrud<T, in SupportSQLiteDatabase>): Maybe<IdentifiableList<out T>> =
      Maybe.fromCallable { findAll(tableCrud) }

  override fun <T : Identifiable<Int>> readAllAsync(tableCrud: TableCrud<T, in SupportSQLiteDatabase>, vararg column: Column<*>): Maybe<IdentifiableList<out T>> =
      Maybe.fromCallable { tableCrud.onFindAll(readableDatabase, *column) }

  override fun <T : Identifiable<Int>> updateAsync(t: T, tableCrud: TableCrud<T, in SupportSQLiteDatabase>, column: Column<*>): Maybe<Boolean> =
      Maybe.fromCallable { tableCrud.onUpdate(t, writableDatabase, column) }

  override fun <T : Identifiable<Int>> updateAsync(t: T, tableCrud: TableCrud<T, in SupportSQLiteDatabase>): Maybe<Boolean> =
      Maybe.fromCallable { tableCrud.onUpdate(t, writableDatabase) }

  override fun <T : Identifiable<Int>> deleteAsync(tableCrud: TableCrud<T, in SupportSQLiteDatabase>, column: Column<*>): Maybe<Boolean> =
      Maybe.fromCallable { tableCrud.onDelete(writableDatabase, column) }

  override fun <T : Identifiable<Int>> deleteAsync(tableCrud: TableCrud<T, in SupportSQLiteDatabase>, t: T): Maybe<Boolean> =
      Maybe.fromCallable { tableCrud.onDelete(t, writableDatabase) }

  override fun deleteAsync(tableCrud: TableCrud<*, in SupportSQLiteDatabase>): Maybe<Boolean> =
      Maybe.fromCallable { tableCrud.onDelete(writableDatabase) }

  override fun <C> deleteAsync(tableCrud: TableCrud<*, in SupportSQLiteDatabase>,
                               column: Column<C>,
                               list: List<out C>): Maybe<Boolean> =
      Maybe.fromCallable { tableCrud.onDelete(writableDatabase, column, list) }

  override fun <T : Identifiable<Int>> saveAsync(t: T, tableCrud: TableCrud<T, in SupportSQLiteDatabase>): Single<Long> =
      Single.fromCallable { tableCrud.onSave(t, writableDatabase) }

  override fun <T : Identifiable<Int>> saveAsync(list: IdentifiableList<out T>, tableCrud: TableCrud<T, in SupportSQLiteDatabase>): Single<Boolean> =
      Single.fromCallable { tableCrud.onSave(list, writableDatabase) }

  override fun deleteAllAsync(): Maybe<Boolean> =
      Maybe.zip(tables().map { tableCrud: TableCrud<*, in SupportSQLiteDatabase> -> this.deleteAsync(tableCrud) }
      ) { objects: Array<Any> ->
        List.fromArray<Any>(*objects).allMatch { aBoolean: Any ->
          aBoolean is Boolean && aBoolean
        }
      }

  override fun <T : Identifiable<Int>> getLastIdAsync(tableCrud: TableCrud<T, in SupportSQLiteDatabase>): Maybe<Int> =
      Maybe.fromCallable { tableCrud.onGetLastId(readableDatabase) }

  private abstract inner class QueryExtras<T : Identifiable<Int>> internal constructor(private val tableCrud: TableCrud<T, in SupportSQLiteDatabase>,
                                                                                       private val database: SupportSQLiteDatabase) :
      ReactiveTable.Extras<T>, DoubleConverter<T, Cursor, ContentValues> {

    override fun first(): Maybe<T> = Maybe.fromCallable { tableCrud.onFind(database).first() }

    override fun last(): Maybe<T> = Maybe.fromCallable { tableCrud.onFind(database).last() }

    override fun all(): Maybe<IdentifiableList<out T>> =
        Maybe.fromCallable { tableCrud.onFind(database).all() }

    override fun limit(limit: Int): Maybe<IdentifiableList<out T>> =
        Maybe.fromCallable { tableCrud.onFind(database).limit(limit) }

    override fun paginate(skip: Int, limit: Int): Maybe<IdentifiableList<out T>> =
        Maybe.fromCallable { tableCrud.onFind(database).paginate(skip, limit) }

    override fun paginateDescending(skip: Int, limit: Int): Maybe<IdentifiableList<out T>> =
        Maybe.fromCallable { tableCrud.onFind(database).paginateDescending(skip, limit) }

    override fun between(column: Column<Number>, a: Number, b: Number): Maybe<IdentifiableList<out T>> =
        Maybe.fromCallable { tableCrud.onFind(database).between(column, a, b) }

    override fun where(vararg column: Column<*>): Maybe<IdentifiableList<out T>> =
        Maybe.fromCallable { tableCrud.onFind(database).where(*column) }

    @SafeVarargs
    override fun notIn(column: Column<Number>, vararg bounds: Number): Maybe<IdentifiableList<out T>> =
        Maybe.fromCallable { tableCrud.onFind(database).notIn(column, *bounds) }

    override fun like(vararg column: Column<String>): Maybe<IdentifiableList<out T>> =
        Maybe.fromCallable { tableCrud.onFind(database).like(*column) }

    override fun orderBy(column: Column<*>): Maybe<IdentifiableList<out T>> =
        Maybe.fromCallable { tableCrud.onFind(database).orderBy(column) }

    override fun groupBy(column: Column<*>): Maybe<IdentifiableList<out T>> =
        Maybe.fromCallable { tableCrud.onFind(database).groupBy(column) }

    override fun groupAndOrderBy(column: Column<*>, column1: Column<*>): Maybe<IdentifiableList<out T>> =
        Maybe.fromCallable { tableCrud.onFind(database).groupAndOrderBy(column, column1) }
  }

}