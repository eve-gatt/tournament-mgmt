package eve.toys.tournmgmt.web.stream;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface Command {

    Future<JsonObject> fetchData();

    default String getCustom(String customProperty) {
        return null;
    }
}
