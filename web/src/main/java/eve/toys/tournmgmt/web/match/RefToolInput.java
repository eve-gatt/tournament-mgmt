package eve.toys.tournmgmt.web.match;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import toys.eve.tournmgmt.db.DbClient;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class RefToolInput {

    private final DbClient dbClient;

    public RefToolInput(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public Future<String> validateTeamMembership(String tournamentUuid, String input) {
        List<String> nameColumn = Arrays.stream(input.split("\n")).map(row -> row.split("\t")[0]).collect(Collectors.toList());
        return dbClient.callDb(DbClient.DB_TEAMS_FOR_PILOT_LIST, new JsonObject()
                .put("tournamentUuid", tournamentUuid)
                .put("pilots", nameColumn))
                .map(msg -> {
                    JsonArray result = (JsonArray) msg.body();
                    List<String> distinctTeams = result.stream()
                            .map(o -> (JsonObject) o)
                            .map(o -> o.getString("team_name"))
                            .distinct()
                            .collect(Collectors.toList());
                    List<String> knownPilots = result.stream()
                            .map(o -> (JsonObject) o)
                            .map(o -> o.getString("pilot_name"))
                            .collect(Collectors.toList());
                    if (result.size() == nameColumn.size() && distinctTeams.size() == 1) {
                        return "All pilots are on the same team";
                    } else if (distinctTeams.size() > 1) {
                        return "Pilots come from more than one team:\n" +
                                result.stream()
                                        .map(o -> (JsonObject) o)
                                        .map(o -> o.getString("pilot_name") + " -> " + o.getString("team_name"))
                                        .collect(Collectors.joining(", "));
                    } else {
                        HashSet<String> whoDis = new HashSet<>(nameColumn);
                        whoDis.removeAll(knownPilots);
                        System.out.println(result.size() + " == " + nameColumn.size());
                        System.out.println(result);
                        System.out.println(knownPilots);
                        System.out.println(distinctTeams);
                        return "Some pilots aren't on any team in this tournament:\n"
                                + String.join(", ", whoDis);
                    }
                });
    }
}
