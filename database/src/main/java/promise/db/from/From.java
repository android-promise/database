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

package promise.db.from;

import promise.commons.model.List;
import promise.db.Column;
import promise.db.QueryBuilder;
import promise.db.TableCrud;
import promise.db.criteria.Criteria;
import promise.db.projection.Projection;

public abstract class From {
  public static TableFrom table(TableCrud table) {
    return new TableFrom(table);
  }

  public static SubQueryFrom subQuery(QueryBuilder subQuery) {
    return new SubQueryFrom(subQuery);
  }

  public PartialJoin innerJoin(TableCrud table) {
    return innerJoin(From.table(table));
  }

  public PartialJoin innerJoin(QueryBuilder subQuery) {
    return innerJoin(From.subQuery(subQuery));
  }

  public PartialJoin innerJoin(From table) {
    return new PartialJoin(this, table, "INNER JOIN");
  }

  public PartialJoin leftJoin(TableCrud table) {
    return leftJoin(From.table(table));
  }

  public PartialJoin leftJoin(QueryBuilder subQuery) {
    return leftJoin(From.subQuery(subQuery));
  }

  public PartialJoin leftJoin(From table) {
    return new PartialJoin(this, table, "LEFT JOIN");
  }

  public abstract String build();

  public abstract List<String> buildParameters();

  public static class PartialJoin {
    private String joinType;
    private From left;
    private From right;

    protected PartialJoin(From left, From right, String joinType) {
      this.joinType = joinType;
      this.left = left;
      this.right = right;
    }

    public JoinFrom on(Column leftColumn, Column rightColumn) {
      return on(Criteria.equals(Projection.column(leftColumn), Projection.column(rightColumn)));
    }

    public JoinFrom on(Criteria criteria) {
      return new JoinFrom(left, right, joinType, criteria);
    }
  }
}
