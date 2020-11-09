package eve.toys.tournmgmt.web.stream;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import toys.eve.tournmgmt.db.HistoricalClient;

import java.util.*;
import java.util.stream.Collectors;

public class CommandShipChoices implements Command {
    private final HistoricalClient historical;
    private final Map<Integer, String> tournaments;

    public CommandShipChoices(HistoricalClient historical, Map<Integer, String> tournaments) {
        this.historical = historical;
        this.tournaments = tournaments;
    }

    @Override
    public Future<JsonObject> fetchData() {
        Promise<JsonObject> promise = Promise.promise();

        historical.callDb(HistoricalClient.Call.HISTORICAL_WIN_LOSS_BY_TOURNAMENT_AND_SHIP,
                new JsonObject().put("whereClause", "where s.Class = 'Command Ship'"))
                .onFailure(promise::fail)
                .map(msg -> (JsonArray) msg.body())
                .onSuccess(rows -> {
                    Set<String> distinctClasses = rows.stream()
                            .map(o -> (JsonObject) o)
                            .map(r -> r.getString("Class"))
                            .collect(Collectors.toSet());
                    List<String> distinctTournaments = rows.stream()
                            .map(o -> (JsonObject) o)
                            .sorted(Comparator.comparing(d -> d.getInteger("Tournament")))
                            .map(r -> tournaments.getOrDefault(r.getInteger("Tournament"), String.valueOf(r.getInteger("Tournament"))))
                            .distinct()
                            .collect(Collectors.toList());
                    Map<Tuple2<String, String>, Integer> tournamentClass = new HashMap<>();
                    rows.stream()
                            .map(o -> (JsonObject) o)
                            .forEach(row -> {
                                String tournamentName = tournaments.getOrDefault(row.getInteger("Tournament"), String.valueOf(row.getInteger("Tournament")));
                                tournamentClass.merge(Tuple.of(tournamentName, row.getString("Class")),
                                        row.getInteger("used"),
                                        Integer::sum);
                            });
                    JsonArray out = new JsonArray(distinctClasses.stream()
                            .map(shipClass -> new JsonObject()
                                    .put("id", shipClass)
                                    .put("values", new JsonArray(distinctTournaments.stream()
                                            .map(t -> new JsonObject()
                                                    .put("tournament", t)
                                                    .put("used", tournamentClass.getOrDefault(Tuple.of(t, shipClass), 0)))
                                            .collect(Collectors.toList()))))
                            .collect(Collectors.toList()));
                    promise.complete(new JsonObject().put("data", out));
                });
        return promise.future();
    }
}
