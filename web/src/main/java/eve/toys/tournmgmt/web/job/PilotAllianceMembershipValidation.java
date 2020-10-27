package eve.toys.tournmgmt.web.job;

import eve.toys.tournmgmt.web.AppStreamHelpers;
import eve.toys.tournmgmt.web.esi.Esi;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import toys.eve.tournmgmt.db.DbClient;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PilotAllianceMembershipValidation implements Validation {
    private final DbClient dbClient;
    private final Esi esi;

    public PilotAllianceMembershipValidation(DbClient dbClient, Esi esi) {
        this.dbClient = dbClient;
        this.esi = esi;
    }

    @Override
    public Future<JsonArray> run() {
        return dbClient.callDb(DbClient.DB_ALL_PILOTS, new JsonObject())
                .map(result -> (JsonArray) result.body())
                .compose(body -> CompositeFuture.all(body.stream()
                        .map(o -> (JsonObject) o)
                        .map(this::buildAsyncCalls)
                        .collect(Collectors.toList())))
                .map(AppStreamHelpers::compositeFutureToJsonObjects)
                .map(teams -> new JsonArray(teams.flatMap(json -> {
                    String uuid = json.getString("uuid");
                    String tournamentUuid = json.getString("tournamentUuid");
                    JsonArray expected = json.getJsonObject("expectedAlliance").getJsonArray("result");
                    String error = "";
                    if (expected != null && !expected.getInteger(0).equals(json.getInteger("actualAlliance"))) {
                        error = json.getJsonObject("character").getString("name")
                                + " is not in "
                                + json.getJsonObject("expectedAlliance").getString("alliance");
                    }
                    return error.isEmpty() ?
                            Stream.empty() :
                            Stream.of(new JsonObject()
                                    .put("type", getProblemType().name())
                                    .put("tournamentUuid", tournamentUuid)
                                    .put("validationIdentifier", getName())
                                    .put("referencedEntity", uuid)
                                    .put("message", error));
                }).collect(Collectors.toList())));
    }

    private Future<JsonObject> buildAsyncCalls(JsonObject row) {
        return esi.checkMembership(
                row.getString("team_uuid"),
                row.getString("tournament_uuid"),
                esi.lookupAlliance(row.getString("team_name")),
                esi.fetchExactMatchCharacter(row.getString("name")));
    }

    @Override
    public ProblemType getProblemType() {
        return ProblemType.team;
    }

    @Override
    public String getName() {
        return "pilot-alliance-membership";
    }

}
