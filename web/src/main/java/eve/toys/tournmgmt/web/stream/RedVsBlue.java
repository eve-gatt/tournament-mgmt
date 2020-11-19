package eve.toys.tournmgmt.web.stream;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import toys.eve.tournmgmt.db.DbClient;
import toys.eve.tournmgmt.db.HistoricalClient;

import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class RedVsBlue implements Command {
    private final DbClient dbClient;
    private final HistoricalClient historicalClient;
    private final Map<Integer, String> tournaments;

    public RedVsBlue(DbClient dbClient, HistoricalClient historicalClient, Map<Integer, String> tournaments) {
        this.dbClient = dbClient;
        this.historicalClient = historicalClient;
        this.tournaments = tournaments;
    }

    @Override
    public Future<JsonObject> fetchData() {
        Promise<JsonObject> promise = Promise.promise();

        dbClient.callDb(DbClient.DB_LATEST_MATCH, new JsonObject())
                .compose(msg -> {
                    JsonObject latest = (JsonObject) msg.body();
                    return CompositeFuture.all(
                            fetchTeam(latest, "red"),
                            fetchTeam(latest, "blue"));
                })
                .map(f -> new JsonObject()
                        .put("red", f.list().get(0))
                        .put("blue", f.list().get(1)))
                .onSuccess(promise::complete)
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<JsonObject> fetchTeam(JsonObject match, String colour) {
        Promise<JsonObject> promise = Promise.promise();
        JsonObject team = new JsonObject()
                .put("logo", match.getString(colour + "_team_logo"))
                .put("name", match.getString(colour + "_team_name"))
                .put("captain", match.getString(colour + "_team_captain"))
                .put("comp", new JsonArray(
                        new JsonObject(match.getString(colour + "json"))
                                .getJsonArray("comp").stream()
                                .map(o -> (JsonObject) o)
                                .map(row -> new JsonObject()
                                        .put("pilot", row.getString("pilot"))
                                        .put("ship", row.getString("ship"))
                                        .put("history", new JsonArray()))
                                .collect(Collectors.toList())));
        String teamUuid = match.getString(colour + "team");
        dbClient.callDb(DbClient.DB_MATCHES_FOR_TEAM, teamUuid)
                .onFailure(promise::fail)
                .onSuccess(msg2 -> {
                    JsonArray previous = (JsonArray) msg2.body();
                    addHistory(team, teamUuid, previous);
                    historicalClient.callDb(HistoricalClient.Call.HISTORICAL_PILOT_WIN_LOSS,
                            new JsonArray(team.getJsonArray("comp").stream()
                                    .map(o -> (JsonObject) o)
                                    .map(d -> d.getString("pilot"))
                                    .collect(Collectors.toList())))
                            .onFailure(promise::fail)
                            .onSuccess(msg3 -> {
                                JsonArray history = new JsonArray(((JsonArray) msg3.body()).stream().map(o -> (JsonObject) o)
                                        .map(o -> o.put("name", tournaments.get(o.getInteger("Tournament")))).collect(Collectors.toList()));
                                team.getJsonArray("comp").stream().map(o -> (JsonObject) o)
                                        .forEach(pilot -> {
                                            List<JsonObject> thisPilotsTournaments = history.stream().map(o -> (JsonObject) o)
                                                    .filter(d -> d.getString("Player").equals(pilot.getString("pilot")))
                                                    .collect(Collectors.toList());
                                            HashMap<String, Tuple2<Integer, Integer>> out = thisPilotsTournaments.stream()
                                                    .collect(pilotWinLossesByTournament());
                                            JsonArray out2 = new JsonArray(out.keySet().stream()
                                                    .sorted(Comparator.comparing(name -> new Integer(name.substring(2))))
                                                    .map(k -> new JsonObject()
                                                            .put("tournament", k)
                                                            .put("W", out.get(k)._1)
                                                            .put("L", out.get(k)._2))
                                                    .collect(Collectors.toList()));
                                            pilot.put("tournaments", out2);
                                        });
                                promise.complete(team);
                            });
                });
        return promise.future();
    }

    private Collector<JsonObject, HashMap<String, Tuple2<Integer, Integer>>, HashMap<String, Tuple2<Integer, Integer>>> pilotWinLossesByTournament() {
        return Collector.of(HashMap::new,
                (hashmap, entry) -> hashmap.merge(
                        entry.getString("name"),
                        entry.getString("winloss").equals("W")
                                ? Tuple.of(entry.getInteger("count"), 0)
                                : Tuple.of(0, entry.getInteger("count")),
                        (t1, t2) -> Tuple.of(t1._1 + t2._1, t1._2 + t2._2)),
                (a, b) -> {
                    b.forEach((k, v) -> a.merge(k, v, (v1, v2) -> Tuple.of(v1._1 + v2._1, v1._2 + v2._2)));
                    return a;
                });
    }

    private void addHistory(JsonObject output, String teamUuid, JsonArray previousMatches) {
        previousMatches.stream()
                .map(o -> (JsonObject) o)
                .forEach(previousMatch -> {
                    String previousColour = previousMatch.getString("blueteam").equals(teamUuid) ? "blue" : "red";
                    output.getJsonArray("comp").stream()
                            .map(o -> (JsonObject) o)
                            .forEach(pilot -> {
                                Optional<JsonObject> matchingPilot = new JsonObject(previousMatch.getString(previousColour + "json"))
                                        .getJsonArray("comp").stream()
                                        .map(o -> (JsonObject) o)
                                        .filter(row -> row.getString("pilot").equals(pilot.getString("pilot")))
                                        .findFirst();
                                if (!matchingPilot.isPresent()) {
                                    pilot.getJsonArray("history").add(0);
                                } else if (previousMatch.getString("winner") == null) {
                                    pilot.getJsonArray("history").add(-2);
                                } else {
                                    int winLoss = previousMatch.getString("winner").equals(previousMatch.getString(previousColour + "_team_name")) ? 1 : -1;
                                    pilot.getJsonArray("history").add(winLoss);
                                }
                            });
                });
    }
}
