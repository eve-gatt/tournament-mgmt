package eve.toys.tournmgmt.web.stream;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import toys.eve.tournmgmt.db.HistoricalClient;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class TopWinRatioAmongstCaptains implements Command {

    private final HistoricalClient historical;
    private final Map<Integer, String> tournaments;

    public TopWinRatioAmongstCaptains(HistoricalClient historical, Map<Integer, String> tournaments) {
        this.historical = historical;
        this.tournaments = tournaments;
    }

    @Override
    public Future<JsonObject> fetchData() {
        Promise<JsonObject> promise = Promise.promise();
        historical.callDb(HistoricalClient.Call.HISTORICAL_WINS_BY_TOURNAMENT_AND_PLAYER, null)
                .onFailure(promise::fail)
                .map(msg -> (JsonArray) msg.body())
                .onSuccess(rows -> {
                    Map<String, Tuple2<Integer, Integer>> winsAndLosses = new HashMap<>();
                    rows.stream()
                            .map(o -> (JsonObject) o)
                            .forEach(row -> {
                                if (row.getString("winloss").equals("W")) {
                                    winsAndLosses.merge(
                                            row.getString("Player"),
                                            Tuple.of(row.getInteger("count"), 0),
                                            (a, b) -> Tuple.of(a._1 + b._1, a._2 + b._2));
                                } else {
                                    winsAndLosses.merge(
                                            row.getString("Player"),
                                            Tuple.of(0, row.getInteger("count")),
                                            (a, b) -> Tuple.of(a._1 + b._1, a._2 + b._2));
                                }
                            });
                    JsonArray out = new JsonArray(winsAndLosses.keySet().stream()
                            .map(captain -> {
                                Tuple2<Integer, Integer> value = winsAndLosses.get(captain);
                                return new JsonObject()
                                        .put("tournamentName", "all")
                                        .put("captain", captain)
                                        .put("count", value._1 * 100 / (value._1 + value._2));
                            })
                            .collect(Collectors.toList()));
                    promise.complete(new JsonObject().put("data", out));
                });
        return promise.future();
    }

    @Override
    public String getCustom(String customProperty) {
        switch (customProperty) {
            case "grouper":
                return "captain";
            case "sublabel":
                return "win ratio";
            default:
                return null;
        }
    }

}
