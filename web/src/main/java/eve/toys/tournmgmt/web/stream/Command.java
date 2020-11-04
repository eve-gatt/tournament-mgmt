package eve.toys.tournmgmt.web.stream;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface Command {
    static int RED = 0;
    static int BLUE = 1;

    Future<JsonObject> fetchData();

}
