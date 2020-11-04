package eve.toys.tournmgmt.web.stream;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class TeamHistory implements Command {
    private final String colour;

    public TeamHistory(String colour) {
        this.colour = colour;
    }

    @Override
    public Future<JsonObject> fetchData() {
        return null;
    }

    @Override
    public String getCustom(String customProperty) {
        return colour;
    }
}
