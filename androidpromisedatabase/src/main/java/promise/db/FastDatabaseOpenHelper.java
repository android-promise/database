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

import android.content.Context;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import promise.commons.AndroidPromise;


public abstract class FastDatabaseOpenHelper extends SQLiteOpenHelper {

  private Context context;

  public FastDatabaseOpenHelper(@Nullable String name,
                                @Nullable SQLiteDatabase.CursorFactory factory,
                                int version,
                                @Nullable DatabaseErrorHandler errorHandler) {
    super(AndroidPromise.instance().context(), name, factory, version, errorHandler);
    this.context = AndroidPromise.instance().context();
  }

  @Override
  public final void onConfigure(SQLiteDatabase db) {
    super.onConfigure(db);
  }

  @Override
  public final void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    super.onDowngrade(db, oldVersion, newVersion);
  }

  @Override
  public final void onOpen(SQLiteDatabase db) {
    super.onOpen(db);
    db.execSQL("PRAGMA foreign_keys = ON");
  }

  @Override
  public final void setIdleConnectionTimeout(long idleConnectionTimeoutMs) {
    super.setIdleConnectionTimeout(idleConnectionTimeoutMs);
  }

  @Override
  public final void setLookasideConfig(int slotSize, int slotCount) {
    super.setLookasideConfig(slotSize, slotCount);
  }

  @Override
  public final void setOpenParams(@NonNull SQLiteDatabase.OpenParams openParams) {
    super.setOpenParams(openParams);
  }

  @Override
  public final void setWriteAheadLoggingEnabled(boolean enabled) {
    super.setWriteAheadLoggingEnabled(enabled);
  }

  @Override
  public final SQLiteDatabase getReadableDatabase() {
    return super.getReadableDatabase();
  }

  @Override
  public final SQLiteDatabase getWritableDatabase() {
    return super.getWritableDatabase();
  }

  @Override
  public final String getDatabaseName() {
    return super.getDatabaseName();
  }

  @Override
  public final synchronized void close() {
    super.close();
  }
}
