package eve.toys.tournmgmt.web.stream;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import toys.eve.tournmgmt.db.HistoricalClient;

import java.util.Map;
import java.util.stream.Collectors;

public class TopWinnersOrLosersAmongstCaptains implements Command {
    private final HistoricalClient historical;
    private final Map<Integer, String> tournaments;
    private final String winOrLoss;

    public TopWinnersOrLosersAmongstCaptains(HistoricalClient historical, Map<Integer, String> tournaments, String winOrLoss) {
        this.historical = historical;
        this.tournaments = tournaments;
        this.winOrLoss = winOrLoss;
    }

    public Future<JsonObject> fetchData() {
        Promise<JsonObject> promise = Promise.promise();
        historical.callDb(HistoricalClient.Call.HISTORICAL_WINS_BY_TOURNAMENT_AND_PLAYER, null)
                .onFailure(promise::fail)
                .map(msg -> (JsonArray) msg.body())
                .onSuccess(rows -> {
                    JsonArray out = new JsonArray(rows.stream()
                            .map(o -> (JsonObject) o)
                            .filter(row -> row.getString("winloss").equals(winOrLoss))
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
