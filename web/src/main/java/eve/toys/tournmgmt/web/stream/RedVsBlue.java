package eve.toys.tournmgmt.web.stream;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import toys.eve.tournmgmt.db.DbClient;

import java.util.Optional;
import java.util.stream.Collectors;

public class RedVsBlue implements Command {
    private final DbClient dbClient;

    public RedVsBlue(DbClient dbClient) {
        this.dbClient = dbClient;
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
                    previous.stream()
                            .map(o -> (JsonObject) o)
                            .forEach(previousMatch -> {
                                String previousColour = previousMatch.getString("blueteam").equals(teamUuid) ? "blue" : "red";
                                team.getJsonArray("comp").stream()
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
                    promise.complete(team);
                });
        return promise.future();
    }
}
