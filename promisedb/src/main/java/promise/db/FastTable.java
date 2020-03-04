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

package promise.db;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.reactivex.Maybe;
import io.reactivex.Single;
import promise.commons.data.log.LogUtil;
import promise.commons.model.Identifiable;
import promise.commons.model.List;
import promise.commons.util.Conditions;
import promise.commons.util.DoubleConverter;
import promise.db.query.QueryBuilder;
import promise.db.query.criteria.Criteria;
import promise.db.query.projection.Projection;
import promise.model.ITimeAware;
import promise.model.IdentifiableList;
import promise.model.TimeAware;

/**
 * This class models database queries
 *
 * @param <T> {@link Identifiable} instance to be persisted by the model
 */
public abstract class FastTable<T extends Identifiable<Integer>> implements Table<T, SQLiteDatabase> {

  /**
   * The create prefix in a prefix for queries that create a table structure
   */
  private static final String CREATE_PREFIX = "CREATE TABLE IF NOT EXISTS ";
  /**
   * The drop prefix is a prefix for queries that destroy a table in the database
   */
  private static final String DROP_PREFIX = "TRUNCATE TABLE IF EXISTS ";
  /**
   * the name of the primary column
   */
  private static final String ID_COLUMN_NAME = "id";
  /**
   * The name of the timestamp columns
   */
  private static final String CREATED_AT_COLUMN_NAME = "CREATED_AT";
  private static final String UPDATED_AT_COLUMN_NAME = "UPDATED_AT";
  /**
   * Each table must have a primary column as well al timestamp columns
   * see {@link Column} for more info
   */
  public static Column<Integer> id, createdAt, updatedAt;

  /*
   * the primary column of the database if named id and the same name in all tables
   */
  static {
    id = new Column<>(ID_COLUMN_NAME, Column.Type.INTEGER.PRIMARY_KEY_AUTOINCREMENT());
    createdAt = new Column<>(CREATED_AT_COLUMN_NAME, Column.Type.INTEGER.NULLABLE());
    updatedAt = new Column<>(UPDATED_AT_COLUMN_NAME, Column.Type.INTEGER.NULLABLE());
  }

  /**
   *
   */
  private FastDatabase database;
  /**
   * The alter prefix is used to alter the structure of a column when making upgrades
   */
  private String ALTER_COMMAND = "ALTER TABLE";
  /**
   * the name of the table to be created
   * see {@link Table#getName()}
   */
  private String name = "`" + getName() + "`";
  /**
   * The specific tag for logging in this table
   */
  private String TAG = LogUtil.makeTag(FastTable.class).concat(name);
  /**
   * Temporary data holder for holding data during dangerous table structure changes
   */
  private IdentifiableList<T> backup;

  /**
   * @param database
   */
  public FastTable(FastDatabase database) {
    this.database = database;
  }

  /**
   * @return
   */
  public FastDatabase getDatabase() {
    return database;
  }

  /**
   * @return
   */
  public ReactiveFastDatabase getReactiveDatabase() {
    if (database instanceof ReactiveFastDatabase) return (ReactiveFastDatabase) database;
    throw new IllegalStateException("The database is not an instance of ReactiveFastDatabase");
  }

  /**
   * gets all the columns for this model from the child class for creation purposes
   * see {@link #onCreate(SQLiteDatabase)}
   *
   * @return list of columns
   */
  public abstract List<? extends Column> getColumns();

  /**
   * optional to get the number of all the columns in this table
   * noteby the added 3 is for id, and timestamp columns
   *
   * @return the number of the columns in this table
   */
  public int getNumberOfColumns() {
    return getColumns().size() + 3;
  }

  /**
   * this handler created a table in the given database instance passed
   * uses {@link Column#getDescription()} to get the column info
   * adds the id and timestamp in the table
   *
   * @param database writable sql database
   * @return true if table is created
   * @throws TableError if theirs a query error
   */
  @Override
  public boolean onCreate(SQLiteDatabase database) throws TableError {
    String sql = CREATE_PREFIX;
    /*
     * add the opening braces after select prefix, see {@link FastTable#CREATE_PREFIX}
     */
    sql = sql.concat(name + "(");
    List<? extends Column> columns = Conditions.checkNotNull(getColumns());
    /*
     * sorts the column in ascending order, see {@link Column#ascending()} comparator
     */
    Collections.sort(columns, Column.ascending);
    List<Column> columns1 = new List<>();
    /*
     * add the three additional columns to the creation script
     */
    columns1.add(id);
    columns1.addAll(columns);
    columns1.add(createdAt);
    columns1.add(updatedAt);
    for (int i = 0; i < columns1.size(); i++) {
      Column column = columns1.get(i);
      if (i == columns1.size() - 1) sql = sql.concat(column.toString());
      else sql = sql.concat(column.toString() + ", ");
    }
    sql = sql.concat(");");
    try {
      LogUtil.d(TAG, sql);
      database.execSQL(sql);
    } catch (SQLException e) {
      throw new TableError(e);
    }
    return true;
  }

  /*
   * upgrades the table from one version to the next
   * if the table doesn't have the timestamps add them
   * @param database writable sql database
   * @param v1 previous version
   * @param v2 next version
   * @return
   * @throws TableError
   */
  @Override
  public void onUpgrade(SQLiteDatabase database, int v1, int v2) throws TableError {
    QueryBuilder builder = new QueryBuilder().from(this);
    Cursor c = database.rawQuery(builder.build(), builder.buildParameters());
    Set<String> set = new HashSet<>(List.fromArray(c.getColumnNames()));
    if (!set.contains(createdAt.getName())) addColumns(database, createdAt);
    if (!set.contains(updatedAt.getName())) addColumns(database, updatedAt);
  }

  /**
   * adds column to the database
   *
   * @param database writable sql database
   * @param columns  fields to be added must be nullable entry types
   * @throws TableError if theirs an sql error
   */
  public void addColumns(SQLiteDatabase database, Column... columns) throws TableError {
    for (Column column : columns) {
      String alterSql = ALTER_COMMAND + " `" + getName() + "` " + "ADD " + column.toString() + ";";
      try {
        LogUtil.d(TAG, alterSql);
        database.execSQL(alterSql);
      } catch (SQLException e) {
        throw new TableError(e);
      }
    }
  }

  /**
   * drops column from the database
   *
   * @param database writable sql database
   * @param columns  fields to be dropped
   * @return true if fields are dropped successfully
   * @throws TableError if theirs an sql error
   */
  public boolean dropColumns(SQLiteDatabase database, Column... columns) throws TableError {
    for (Column column : columns) {
      String alterSql =
          ALTER_COMMAND + " `" + getName() + "` " + "DROP COLUMN " + column.getName() + ";";
      try {
        database.execSQL(alterSql);
      } catch (SQLException e) {
        throw new TableError(e);
      }
    }
    return true;
  }

  /**
   * @param t
   * @return
   */
  public Long save(T t) {
    return database.save(t, this);
  }


  /**
   * @param t
   * @return
   */
  public Single<Long> saveAsync(T t) {
    return getReactiveDatabase().saveAsync(t, this);
  }

  /**
   * @param list
   * @return
   */
  public boolean save(IdentifiableList<? extends T> list) {
    return database.save(list, this);
  }

  /**
   * @param list
   * @return
   */
  public Single<Boolean> saveAsync(IdentifiableList<? extends T> list) {
    return getReactiveDatabase().saveAsync(list, this);
  }

  /**
   * @param t
   * @return
   */
  public boolean update(T t) {
    return database.update(t, this);
  }

  /**
   * @param t
   * @return
   */
  public Maybe<Boolean> updateAsync(T t) {
    return getReactiveDatabase().updateAsync(t, this);
  }

  /**
   * @param t
   * @param column
   * @return
   */
  public boolean update(T t, Column column) {
    return database.update(t, this, column);
  }

  /**
   * @param t
   * @param column
   * @return
   */
  public Maybe<Boolean> updateAsync(T t, Column column) {
    return getReactiveDatabase().updateAsync(t, this, column);
  }

  public Cursor query(QueryBuilder queryBuilder) {
    return database.query(queryBuilder);
  }


  public Single<Cursor> queryAsync(QueryBuilder queryBuilder) {
    return getReactiveDatabase().queryAsync(queryBuilder);
  }

  /**
   * @return
   */
  public Table.Extras<T> find() {
    return database.find(this);
  }

  /**
   * @return
   */
  public T findById(long idLong) {
    return database.find(this).where(id.with((int) idLong)).first();
  }

  /**
   * @return
   */
  public T findOne(Column... columns) {
    return database.find(this).where(columns).first();
  }

  /**
   * @return
   * @throws TableError
   */
  public ReactiveTable.Extras<T> findAsync() throws TableError {
    return getReactiveDatabase().readAsync(this);
  }

  /**
   * @return
   */
  public IdentifiableList<? extends T> findAll() {
    return database.findAll(this);
  }

  /**
   * @return
   */
  public Maybe<IdentifiableList<? extends T>> findAllAsync() {
    return getReactiveDatabase().readAllAsync(this);
  }

  /**
   * @param column
   * @return
   */
  public IdentifiableList<? extends T> findAll(Column... column) {
    return database.findAll(this, column);
  }

  /**
   * @param column
   * @return
   */
  public Maybe<IdentifiableList<? extends T>> findAllAsync(Column... column) {
    return getReactiveDatabase().readAllAsync(this, column);
  }

  /**
   * @param column
   * @return
   */
  public boolean delete(Column column) {
    return database.delete(this, column);
  }

  /**
   * @param column
   * @return
   */
  public Maybe<Boolean> deleteAsync(Column column) {
    return getReactiveDatabase().deleteAsync(this, column);
  }

  /**
   * @param t
   * @return
   */
  public boolean delete(T t) {
    return database.delete(this, t);
  }

  /**
   * @param t
   * @return
   */
  public Maybe<Boolean> deleteAsync(T t) {
    return getReactiveDatabase().deleteAsync(this, t);
  }

  /**
   * @param column
   * @param list
   * @param <N>
   * @return
   */
  public <N> boolean delete(Column<N> column, List<? extends N> list) {
    return database.delete(this, column, list);
  }

  /**
   * @param column
   * @param list
   * @param <C>
   * @return
   */
  public <C> Maybe<Boolean> deleteAsync(Column<C> column, List<? extends C> list) {
    return getReactiveDatabase().deleteAsync(this, column, list);
  }

  /**
   * @return
   */
  public boolean clear() {
    return database.delete(this);
  }

  /**
   * @return
   */
  public Maybe<Boolean> clearAsync() {
    return getReactiveDatabase().deleteAsync(this);
  }

  /**
   * @return
   */
  public int getLastId() {
    return database.getLastId(this);
  }

  /**
   * @return
   */
  public Maybe<Integer> getLastIdAsync() {
    return getReactiveDatabase().getLastIdAsync(this);
  }

  /**
   * more verbose readAsync operation against the database
   *
   * @param database readable sql database
   * @return an extras instance for more concise reads
   */

  @Override
  public Extras<T> find(final SQLiteDatabase database) {
    return new QueryExtras<T>(database) {
      @Override
      public ContentValues serialize(T t) {
        return FastTable.this.serialize(t);
      }

      @Override
      public T deserialize(Cursor cursor) {
        return FastTable.this.deserialize(cursor);
      }
    };
  }

  /**
   * readAsync all the rows in the database
   *
   * @param database readable sql database
   * @param close    close the connection if this is true
   * @return a list of records
   */
  @Override
  public final IdentifiableList<? extends T> onFindAll(SQLiteDatabase database, boolean close) {
    QueryBuilder builder = new QueryBuilder().from(FastTable.this);
    Cursor cursor;
    try {
      cursor = database.rawQuery(builder.build(), builder.buildParameters());
      IdentifiableList<T> ts = new IdentifiableList<>();
      while (cursor.moveToNext() && !cursor.isClosed()) ts.add(getWithId(cursor));
      cursor.close();
      /*if (close) database.close();*/
      return ts;
    } catch (SQLiteException e) {
      return new IdentifiableList<>();
    }
  }

  /**
   * readAsync the rows following the criteria specified in the column provided
   * for each of the columns
   * if {@link Column#value()} is not null, filter with the value
   * if {@link Column#order()} is not null, order by that column too
   *
   * @param database readable sql database
   * @param columns  the fields to infer where and order by conditions
   * @return list of records satisfying the criteria
   */
  @Override
  public IdentifiableList<? extends T> onFindAll(SQLiteDatabase database, Column[] columns) {
    if (columns == null) return onFindAll(database, true);
    QueryBuilder builder = new QueryBuilder().from(FastTable.this).takeAll();
    for (Column column : columns) {
      if (column.value() != null) builder.whereAnd(Criteria.equals(column, column.value()));
      if (column.order() != null) {
        if (column.order().equals(Column.DESCENDING)) {
          builder.orderByDescending(column);
        } else builder.orderByAscending(column);
      }
    }
    Cursor cursor = database.rawQuery(builder.build(), builder.buildParameters());
    IdentifiableList<T> ts = new IdentifiableList<>();
    while (cursor.moveToNext() && !cursor.isClosed()) ts.add(getWithId(cursor));
    cursor.close();
    /*database.close();*/
    return ts;
  }

  /**
   * updateAsync a row in the table
   *
   * @param t        instance to updateAsync
   * @param database writable sql database
   * @param column   field with where condition to updateAsync
   * @return true if instance is updated
   */
  @Override
  public final boolean onUpdate(T t, SQLiteDatabase database, Column column) throws TableError {
    String where;
    if (column != null && column.getOperand() != null && column.value() != null)
      where = column.getName() + column.getOperand() + column.value();
    else throw new TableError("Cant updateAsync the record, missing updating information");
    ContentValues values = serialize(t);
    values.put(updatedAt.getName(), System.currentTimeMillis());
    return database.update(name, values, where, null) >= 0;
  }

  /**
   * updated an instance with an id value more than zero
   *
   * @param t        the instance to updateAsync
   * @param database writable sql database
   * @return true if updated
   */
  @Override
  public boolean onUpdate(T t, SQLiteDatabase database) {
    try {
      return id != null && onUpdate(t, database, id.with(t.getId()));
    } catch (TableError tableError) {
      return false;
    }
  }

  /**
   * deletes multiple rows from the table where the column matches all the
   * given values in list
   *
   * @param database writable sql database
   * @param column   the matching column
   * @param list     values to match with
   * @param <C>      the type of matching, must not be derived data type
   * @return true if all rows are deleted
   */
  @Override
  public final <C> boolean onDelete(SQLiteDatabase database, Column<C> column, List<? extends C> list) {
    boolean deleted;
    String where = "";
    for (int i = 0, listSize = list.size(); i < listSize; i++) {
      C c = list.get(i);
      if (i == listSize - 1) {
        where = column.getName() + " " + column.getOperand() + " " + c;
      } else where = column.getName() + " " + column.getOperand() + " " + c + " OR ";

    }
    deleted = database.delete(name, where, null) >= 0;
    return deleted;
  }

  /**
   * deleteAsync a row in the table matching condition in the column
   *
   * @param database writable sql database
   * @param column   field to match
   * @return true if row is deleted
   */
  @Override
  public final boolean onDelete(SQLiteDatabase database, Column column) {
    if (column == null) return false;
    String where = column.getName() + column.getOperand() + column.value();
    return database.delete(name, where, null) >= 0;
  }

  /**
   * deleteAsync an instance from the table
   *
   * @param t        instance to be removed must have an id more than zero
   * @param database writable sql database
   * @return true if item is deleted
   */
  @Override
  public boolean onDelete(T t, SQLiteDatabase database) {
    return onDelete(database, id.with(t.getId()));
  }

  /**
   * deleteAsync all rows in the table
   *
   * @param database writable sql database
   * @return true if all rows are deleted
   */
  @Override
  public final boolean onDelete(SQLiteDatabase database) {
    return !TextUtils.isEmpty(name) && database.delete(name, null, null) >= 0;
  }

  /**
   * saveAsync an instance to the database
   * serialize it to content values
   *
   * @param t        instance to be saved
   * @param database writable sql database
   * @return id of the row affected
   */
  @Override
  public final long onSave(T t, SQLiteDatabase database) {
    if (t.getId() != 0 && onUpdate(t, database)) return t.getId();
    ContentValues values = serialize(t);
    values.put(createdAt.getName(), System.currentTimeMillis());
    values.put(updatedAt.getName(), System.currentTimeMillis());
    return database.insert(name, null, values);
  }

  /**
   * saveAsync a list of items in the database
   *
   * @param list     items to be saved
   * @param database writable sql database
   * @return true if all the items are saved
   */
  @Override
  public final boolean onSave(IdentifiableList<? extends T> list, SQLiteDatabase database) {
    boolean saved = true;
    int i = 0, listSize = list.size();
    while (i < listSize) {
      T t = list.get(i);
      saved = saved && onSave(t, database) > 0;
      i++;
    }
    /*if (close) database.close();*/
    return saved;
  }

  /**
   * drop this table from the database
   *
   * @param database writable sql database
   * @return true if the table is dropped
   * @throws TableError if theirs an sql error
   */
  @Override
  public final boolean onDrop(SQLiteDatabase database) throws TableError {
    String sql = DROP_PREFIX + name + ";";
    try {
      database.execSQL(sql);
    } catch (SQLException e) {
      throw new TableError(e);
    }
    return true;
  }

  /**
   * get the last id of the last row in the table
   * uses projection to count the id column as num
   *
   * @param database readable sql database
   * @return the id
   */
  @Override
  public final int onGetLastId(SQLiteDatabase database) {
    if (id == null) return 0;
    QueryBuilder builder = new QueryBuilder().from(this).select(Projection.count(id).as("num"));
    @SuppressLint("Recycle") Cursor cursor = database.rawQuery(builder.build(), builder.buildParameters());
    return cursor.getInt(FastTable.id.getIndex());
  }

  /**
   * backup all the items in the table during dangerous upgrdes
   *
   * @param database readable database instance
   */

  public void backup(SQLiteDatabase database) {
    backup = new IdentifiableList<>();
    backup.addAll(onFindAll(database, false));
  }

  /**
   * saveAsync back the backed items to the database
   *
   * @param database writable database
   */

  public void restore(SQLiteDatabase database) {
    if (backup != null && !backup.isEmpty()) {
      onSave(backup, database);
      backup.clear();
    }
    backup = null;
  }

  /**
   * gets a single from the cursor pre populated with id and timestamps
   *
   * @param cursor the serialized version of the instance
   * @return instance from the cursor
   */
  T getWithId(Cursor cursor) {
    T t = deserialize(cursor);
    t.setId(cursor.getInt(id.getIndex()));
    if (t instanceof ITimeAware) {
      ITimeAware iTimeAware = (ITimeAware) t;
      iTimeAware.setCreatedAt(cursor.getInt(createdAt.getIndex(cursor)));
      iTimeAware.setUpdatedAt(cursor.getInt(updatedAt.getIndex(cursor)));
      t = (T) iTimeAware;
    }
    return t;
  }

  /**
   * This class contains special queries for reading from the table
   * see {@link TimeAware} for encapsulating id and timestamps
   * see {@link DoubleConverter} for serializing and de-serializing
   *
   * @param <Q> The type of the items in the table
   */
  private abstract class QueryExtras<Q extends Identifiable<Integer>>
      implements Extras<Q>, DoubleConverter<Q, Cursor, ContentValues> {
    /**
     * the database instance to readAsync fromm
     */
    private SQLiteDatabase database;

    QueryExtras(SQLiteDatabase database) {
      this.database = database;
    }

    public SQLiteDatabase database() {
      return database;
    }

    /**
     * get a record pre populated with id and timestamps from each readAsync
     *
     * @param cursor serialized version of Q
     * @return Q the de-serialized output of reading the cursor
     */
    Q getWithId(Cursor cursor) {
      Q t = deserialize(cursor);
      t.setId(cursor.getInt(id.getIndex()));
      if (t instanceof ITimeAware) {
        ITimeAware iTimeAware = (ITimeAware) t;
        iTimeAware.setCreatedAt(cursor.getInt(createdAt.getIndex(cursor)));
        iTimeAware.setUpdatedAt(cursor.getInt(updatedAt.getIndex(cursor)));
        t = (Q) iTimeAware;
      }
      return t;
    }

    /**
     * get the first record in the table
     *
     * @return the first records or null if theirs none in the table
     */
    @Nullable
    @Override
    public Q first() {
      Cursor cursor;
      try {
        QueryBuilder builder = new QueryBuilder().from(FastTable.this).take(1);
        cursor = database.rawQuery(builder.build(), builder.buildParameters());
        return getWithId(cursor);
      } catch (SQLiteException e) {
        LogUtil.e(TAG, e);
        return null;
      }
    }

    /**
     * get the last record in the table
     *
     * @return an item or null if theirs none stored in the table
     */
    @Nullable
    @Override
    public Q last() {
      Cursor cursor;
      try {
        QueryBuilder builder = new QueryBuilder().from(FastTable.this).orderByDescending(id).take(1);
        cursor = database.rawQuery(builder.build(), builder.buildParameters());
        return getWithId(cursor);
      } catch (SQLiteException e) {
        LogUtil.e(TAG, e);
        return null;
      }
    }

    /**
     * get all the items in the table
     *
     * @return the items or an empty list if theirs none
     */
    @Override
    public IdentifiableList<? extends Q> all() {
      Cursor cursor;
      try {
        QueryBuilder builder = new QueryBuilder().from(FastTable.this).takeAll();
        cursor = database.rawQuery(builder.build(), builder.buildParameters());
        IdentifiableList<Q> ts = new IdentifiableList<>();
        while (cursor.moveToNext() && !cursor.isClosed()) ts.add(getWithId(cursor));
        return ts;
      } catch (SQLiteException e) {
        return new IdentifiableList<>();
      }
    }

    /**
     * readAsync the top items in the table
     *
     * @param limit the number of records to readAsync
     * @return a list of the items
     */
    @Override
    public IdentifiableList<? extends Q> limit(int limit) {
      Cursor cursor;
      try {
        QueryBuilder builder = new QueryBuilder().from(FastTable.this).take(limit);
        cursor = database.rawQuery(builder.build(), builder.buildParameters());
        IdentifiableList<Q> ts = new IdentifiableList<>();
        while (cursor.moveToNext() && !cursor.isClosed()) ts.add(getWithId(cursor));
        return ts;
      } catch (SQLiteException e) {
        LogUtil.e(TAG, e);
        return new IdentifiableList<>();
      }
    }

    /**
     * reads the records between the skip and limit in the table
     *
     * @param skip  of set from the top to not readAsync
     * @param limit items to load after skip
     * @return a list of records
     */
    @Override
    public IdentifiableList<? extends Q> paginate(int skip, int limit) {
      Cursor cursor;
      try {
        QueryBuilder builder = new QueryBuilder().from(FastTable.this).take(limit).skip(skip);
        cursor = database.rawQuery(builder.build(), builder.buildParameters());
        IdentifiableList<Q> ts = new IdentifiableList<>();
        while (cursor.moveToNext() && !cursor.isClosed()) ts.add(getWithId(cursor));
        return ts;
      } catch (SQLiteException e) {
        LogUtil.e(TAG, e);
        return new IdentifiableList<>();
      }
    }

    @Override
    public IdentifiableList<? extends Q> paginateDescending(int skip, int limit) {
      Cursor cursor;
      try {
        QueryBuilder builder = new QueryBuilder().from(FastTable.this).orderByDescending(id).take(limit).skip(skip);
        cursor = database.rawQuery(builder.build(), builder.buildParameters());
        IdentifiableList<Q> ts = new IdentifiableList<>();
        while (cursor.moveToNext() && !cursor.isClosed()) ts.add(getWithId(cursor));
        return ts;
      } catch (SQLiteException e) {
        LogUtil.e(TAG, e);
        return new IdentifiableList<>();
      }
    }

    /**
     * gets all items that match in between the int left and right
     *
     * @param column column to match between
     * @param a      lower between bound
     * @param b      upper between bound
     * @return a list of items
     */
    @Override
    public <N extends Number> IdentifiableList<? extends Q> between(Column<N> column, N a, N b) {
      Cursor cursor;
      try {
        QueryBuilder builder = new QueryBuilder().from(FastTable.this).takeAll().whereAnd(Criteria.between(column, a, b));
        cursor = database.rawQuery(builder.build(), builder.buildParameters());
        IdentifiableList<Q> ts = new IdentifiableList<>();
        while (cursor.moveToNext() && !cursor.isClosed()) ts.add(getWithId(cursor));
        return ts;
      } catch (SQLiteException e) {
        LogUtil.e(TAG, e);
        return new IdentifiableList<>();
      }
    }

    /**
     * gets all items matching the multiple columns
     *
     * @param column fields to match their values
     * @return a list of items
     */
    @Override
    public IdentifiableList<? extends Q> where(Column[] column) {
      Cursor cursor;
      try {
        QueryBuilder builder = new QueryBuilder().from(FastTable.this).takeAll();
        for (Column column1 : column)
          if (column1.value() != null) builder.whereAnd(Criteria.equals(column1, column1.value()));
        cursor = database.rawQuery(builder.build(), builder.buildParameters());
        IdentifiableList<Q> ts = new IdentifiableList<>();
        while (cursor.moveToNext() && !cursor.isClosed()) ts.add(getWithId(cursor));
        return ts;
      } catch (SQLiteException e) {
        LogUtil.e(TAG, e);
        return new IdentifiableList<>();
      }
    }

    /**
     * gets all the items matching not in any of the columns
     *
     * @param column field to match
     * @param bounds not in bounds
     * @return a list of items
     */
    @SafeVarargs
    @Override
    public final <N extends Number> IdentifiableList<? extends Q> notIn(Column<N> column, N... bounds) {
      Cursor cursor;
      Object[] items = new Object[bounds.length];
      System.arraycopy(bounds, 0, items, 0, bounds.length);
      QueryBuilder builder = new QueryBuilder().from(FastTable.this).takeAll()
          .whereAnd(Criteria.notIn(column, items));
      try {
        cursor = database.rawQuery(builder.build(), builder.buildParameters());
        IdentifiableList<Q> ts = new IdentifiableList<>();
        while (cursor.moveToNext() && !cursor.isClosed()) {
          Q t = getWithId(cursor);
          ts.add(t);
        }
        cursor.close();
        /*database.close();*/
        return ts;
      } catch (SQLiteException e) {
        LogUtil.e(TAG, e);
        return new IdentifiableList<>();
      }
    }

    /**
     * get all the rows where the column is like the columns values
     *
     * @param column the fields to compute like from
     * @return a list of columns
     */
    @Override
    public IdentifiableList<? extends Q> like(Column[] column) {
      Cursor cursor;
      QueryBuilder builder = new QueryBuilder().from(FastTable.this).takeAll();
      for (Column column1 : column)
        builder.whereAnd(Criteria.contains(column1, String.valueOf(column1.value())));
      try {
        cursor = database.rawQuery(builder.build(), builder.buildParameters());
        IdentifiableList<Q> ts = new IdentifiableList<>();
        while (cursor.moveToNext() && !cursor.isClosed()) {
          Q t = getWithId(cursor);
          ts.add(t);
        }
        cursor.close();
        /*database.close();*/
        return ts;
      } catch (SQLiteException e) {
        LogUtil.e(TAG, e);
        return new IdentifiableList<>();
      }
    }

    /**
     * get all the rows in the oder specified by the column
     *
     * @param column field to order by
     * @return a list of ordered items
     */
    @Override
    public IdentifiableList<? extends Q> orderBy(Column column) {
      Cursor cursor;
      QueryBuilder builder = new QueryBuilder().from(FastTable.this).takeAll();
      if (column.order().equals(Column.DESCENDING)) {
        builder.orderByDescending(column);
      } else builder.orderByAscending(column);
      try {
        cursor = database.rawQuery(builder.build(), builder.buildParameters());
        IdentifiableList<Q> ts = new IdentifiableList<>();
        while (cursor.moveToNext() && !cursor.isClosed()) {
          Q t = getWithId(cursor);
          ts.add(t);
        }
        cursor.close();
        /*database.close();*/
        return ts;
      } catch (SQLiteException e) {
        LogUtil.e(TAG, e);
        return new IdentifiableList<>();
      }
    }

    /**
     * gets all the items grouped by the column
     *
     * @param column field to group by
     * @return a list of grouped items
     */
    @Override
    public IdentifiableList<? extends Q> groupBy(Column column) {
      Cursor cursor;
      QueryBuilder builder = new QueryBuilder().from(FastTable.this).takeAll().groupBy(column);
      try {
        cursor = database.rawQuery(builder.build(), builder.buildParameters());
        IdentifiableList<Q> ts = new IdentifiableList<>();
        while (cursor.moveToNext() && !cursor.isClosed()) {
          Q t = getWithId(cursor);
          ts.add(t);
        }
        cursor.close();
        /*database.close();*/
        return ts;
      } catch (SQLiteException e) {
        LogUtil.e(TAG, e);
        return new IdentifiableList<>();
      }
    }

    /**
     * gets all the items grouped and ordered by the two columns
     *
     * @param column  group by field
     * @param column1 order by fields
     * @return a list of items
     */
    @Override
    public IdentifiableList<? extends Q> groupAndOrderBy(Column column, Column column1) {
      Cursor cursor;
      QueryBuilder builder = new QueryBuilder().from(FastTable.this).takeAll().groupBy(column);
      if (column1.order().equals(Column.DESCENDING)) {
        builder.orderByDescending(column1);
      } else builder.orderByAscending(column1);
      try {
        cursor = database.rawQuery(builder.build(), builder.buildParameters());
        IdentifiableList<Q> ts = new IdentifiableList<>();
        while (cursor.moveToNext() && !cursor.isClosed()) {
          Q t = getWithId(cursor);
          ts.add(t);
        }
        cursor.close();
        /*database.close();*/
        return ts;
      } catch (SQLiteException e) {
        LogUtil.e(TAG, e);
        return new IdentifiableList<>();
      }
    }
  }
}