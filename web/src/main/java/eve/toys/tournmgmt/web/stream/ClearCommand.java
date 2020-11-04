package eve.toys.tournmgmt.web.stream;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class ClearCommand implements Command {
    @Override
    public Future<JsonObject> fetchData() {
        return Future.succeededFuture(new JsonObject());
    }
}
