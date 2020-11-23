package eve.toys.tournmgmt.web.stream;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import toys.eve.tournmgmt.db.DbClient;

public class MostCustomShip implements Command {
    private final DbClient dbClient;

    public MostCustomShip(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public Future<JsonObject> fetchData() {
        Promise<JsonObject> promise = Promise.promise();
        JsonObject data = new JsonObject()
                .put("Kitsune", 12)
                .put("Blackbird", 12)
                .put("Rook", 7)
                .put("Falcon", 5)
                .put("Widow", 4);

        promise.complete(new JsonObject().put("data", data));
        return promise.future();
    }

}
