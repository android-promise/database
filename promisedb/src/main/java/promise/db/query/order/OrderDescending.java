package promise.db.query.order;

import promise.commons.model.List;
import promise.commons.model.function.MapFunction;
import promise.db.Utils;
import promise.db.query.projection.Projection;

public class OrderDescending extends Order {

  public OrderDescending(Projection projection) {
    super(projection);
  }

  @Override
  public String build() {
    String ret = " DESC";

    if (projection != null) ret = projection.build() + ret;

    return ret;
  }

  @Override
  public List<String> buildParameters() {
    if (projection != null) return projection.buildParameters();
    else
      return Utils.EMPTY_LIST.map(
          String::valueOf);
  }
}
