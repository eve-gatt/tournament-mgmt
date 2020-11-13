package eve.toys.tournmgmt.web.stream;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import toys.eve.tournmgmt.db.DbClient;

public class MatchWinsByTeam implements Command {
    private final DbClient dbClient;

    public MatchWinsByTeam(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public Future<JsonObject> fetchData() {
        Promise<JsonObject> promise = Promise.promise();

        dbClient.callDb(DbClient.DB_WINS_BY_TEAM, new JsonObject())
                .onFailure(promise::fail)
                .onSuccess(msg -> promise.complete(new JsonObject().put("data", msg.body())));

        return promise.future();
    }
}
