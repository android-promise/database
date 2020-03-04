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
 *
 *
 */

package promise.db.query.criteria;

import promise.commons.model.List;

public class OrCriteria extends Criteria {
  private Criteria left;
  private Criteria right;

  public OrCriteria(Criteria left, Criteria right) {
    this.left = left;
    this.right = right;
  }

  @Override
  public String build() {
    String ret = " OR ";

    if (left != null) ret = left.build() + ret;

    if (right != null) ret = ret + right.build();

    return "(" + ret.trim() + ")";
  }

  @Override
  public List<String> buildParameters() {
    List<Object> ret = new List<>();

    if (left != null) ret.addAll(left.buildParameters());

    if (right != null) ret.addAll(right.buildParameters());

    return ret.map(
        String::valueOf);
  }
}
