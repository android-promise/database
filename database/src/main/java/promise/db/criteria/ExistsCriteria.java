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

package promise.db.criteria;

import promise.commons.model.List;
import promise.db.QueryBuilder;
import promise.db.Utils;

public class ExistsCriteria extends Criteria {
  private QueryBuilder subQuery;

  public ExistsCriteria(QueryBuilder subQuery) {
    this.subQuery = subQuery;
  }

  @Override
  public String build() {
    String ret = "EXISTS(";

    if (subQuery != null) ret = ret + subQuery.build();

    ret = ret + ")";
    return ret;
  }

  @Override
  public List<String> buildParameters() {
    if (subQuery != null) return List.fromArray(subQuery.buildParameters());
    else {
      return Utils.EMPTY_LIST.map(
          String::valueOf);
    }
  }
}
