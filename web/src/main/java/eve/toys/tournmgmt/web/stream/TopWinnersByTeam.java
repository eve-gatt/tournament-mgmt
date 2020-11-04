package eve.toys.tournmgmt.web.stream;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import toys.eve.tournmgmt.db.HistoricalClient;

import java.util.Map;
import java.util.stream.Collectors;

public class TopWinnersByTeam implements Command {
    private final HistoricalClient historical;
    private final Map<Integer, String> tournaments;

    public TopWinnersByTeam(HistoricalClient historical, Map<Integer, String> tournaments) {
        this.historical = historical;
        this.tournaments = tournaments;
    }

    @Override
    public Future<JsonObject> fetchData() {
        Promise<JsonObject> promise = Promise.promise();
        historical.callDb(HistoricalClient.Call.HISTORICAL_WINS_BY_TOURNAMENT_AND_TEAM, null)
                .onFailure(promise::fail)
                .map(msg -> (JsonArray) msg.body())
                .onSuccess(rows -> {
                    JsonArray out = new JsonArray(rows.stream()
                            .map(o -> (JsonObject) o)
                            .filter(row -> row.getString("winloss").equals("W"))
                            .map(this::withTournamentName)
                            .collect(Collectors.toList()));
                    promise.complete(new JsonObject().put("data", out));
                });
        return promise.future();
    }

    private JsonObject withTournamentName(JsonObject match) {
        int tournamentId = match.getInteger("Tournament");
        String tournamentName = tournaments.getOrDefault(tournamentId, "unknown");
        return match.put("tournamentName", tournamentName);
    }

}
