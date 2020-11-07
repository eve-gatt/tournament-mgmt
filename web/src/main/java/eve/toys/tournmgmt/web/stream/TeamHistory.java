package eve.toys.tournmgmt.web.stream;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;
import toys.eve.tournmgmt.db.HistoricalClient;

import java.util.Map;

public class TeamHistory implements Command {
    private final DbClient dbClient;
    private final HistoricalClient historical;
    private final Map<Integer, String> tournaments;
    private final String colour;
    private final String teamName;

    public TeamHistory(DbClient dbClient, HistoricalClient historical, Map<Integer, String> tournaments, String colour, String teamName) {
        this.dbClient = dbClient;
        this.historical = historical;
        this.tournaments = tournaments;
        this.colour = colour;
        this.teamName = teamName;
    }

    @Override
    public Future<JsonObject> fetchData() {
        Promise<JsonObject> promise = Promise.promise();
        dbClient.callDb(DbClient.DB_LATEST_MATCH, null)
                .map(msg -> (JsonObject) msg.body())
                .map(RenderHelper::formatCreatedAt)
                .onFailure(promise::fail)
                .onSuccess(match -> promise.complete(match));
        return promise.future();
    }

    @Override
    public String getCustom(String customProperty) {
        return colour;
    }

    private JsonObject withTournamentName(JsonObject match) {
        int tournamentId = match.getInteger("Tournament");
        String tournamentName = tournaments.getOrDefault(tournamentId, "unknown");
        return match.put("tournamentName", tournamentName);
    }

}
