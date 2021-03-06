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

package promise.base

import promise.base.comment.Like
import promise.base.comment.PostComment
import promise.base.photo.Photo
import promise.base.post.Post
import promise.base.session.User
import promise.base.todo.Todo
import promise.commons.data.log.LogUtil
import promise.database.DatabaseEntity
import promise.db.FastDatabase
import promise.db.PromiseDatabase
import promise.utils.Visitor

@DatabaseEntity(
    persistableEntities = [
      PostComment::class,
      Photo::class,
      Post::class,
      Todo::class,
      Like::class,
      User::class
    ],
    version = 2
)
abstract class AppDatabase(fastDatabase: FastDatabase)
  : PromiseDatabase(fastDatabase) {

  init {
    fastDatabase.setErrorHandler {
      LogUtil.e(TAG, "database error: ${it.path}")
    }
    fastDatabase.fallBackToDestructiveMigration()
  }

  //abstract fun getPostCommentsDao(): PostCommentDao

  companion object {
    val TAG: String = LogUtil.makeTag(AppDatabase::class.java)
  }
}
