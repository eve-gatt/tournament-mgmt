package eve.toys.tournmgmt.web.stream;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import toys.eve.tournmgmt.db.DbClient;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MostPickedShips implements Command {
    private final DbClient dbClient;
    private final String grouper;
    private final Predicate<JsonObject> compFilter;

    public MostPickedShips(DbClient dbClient, String grouper) {
        this(dbClient, grouper, o -> true);
    }

    public MostPickedShips(DbClient dbClient, String grouper, Predicate<JsonObject> compFilter) {
        this.dbClient = dbClient;
        this.grouper = grouper;
        this.compFilter = compFilter;
    }

    @Override
    public Future<JsonObject> fetchData() {
        Promise<JsonObject> promise = Promise.promise();
        dbClient.callDb(DbClient.DB_ALL_MATCHES, new JsonObject())
                .map(msg -> (JsonArray) msg.body())
                .map(matches -> matches.stream()
                        .map(o -> (JsonObject) o)
                        .flatMap(match -> Stream.concat(
                                shipsFielded(new JsonObject(match.getString("bluejson"))).stream(),
                                shipsFielded(new JsonObject(match.getString("redjson"))).stream()))
                        .collect(Collectors.toList()))
                .map(ships -> ships.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting())))
                .onFailure(promise::fail)
                .onSuccess(data -> promise.complete(new JsonObject().put("data", data)));
        return promise.future();
    }

    private List<String> shipsFielded(JsonObject teamJson) {
        return teamJson.getJsonArray("comp").stream()
                .map(o -> (JsonObject) o)
                .filter(compFilter)
                .map(fielded -> fielded.getString(grouper) != null ? fielded.getString(grouper) : fielded.getString("ship"))
                .collect(Collectors.toList());
    }
}
