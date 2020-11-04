package eve.toys.tournmgmt.web.stream;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class TeamHistory implements Command {
    public TeamHistory(int colour) {

    }

    @Override
    public Future<JsonObject> fetchData() {
        return null;
    }
}
