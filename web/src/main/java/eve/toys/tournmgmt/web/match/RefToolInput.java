package eve.toys.tournmgmt.web.match;

import eve.toys.tournmgmt.web.esi.Esi;
import eve.toys.tournmgmt.web.esi.ShipSkillChecker;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.auth.oauth2.KeycloakHelper;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.impl.OAuth2TokenImpl;
import toys.eve.tournmgmt.db.DbClient;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RefToolInput {

    private final DbClient dbClient;
    private final Esi esi;
    private final OAuth2Auth oauth2;

    public RefToolInput(DbClient dbClient, Esi esi, OAuth2Auth oauth2) {
        this.dbClient = dbClient;
        this.esi = esi;
        this.oauth2 = oauth2;
    }

    private static List<Tuple3<String, String, String>> resultsToTuples(CompositeFuture f) {
        return f.list().stream().map(o -> (Tuple3<String, String, String>) o).collect(Collectors.toList());
    }

    public Future<String> validateTeamMembership(String tournamentUuid, String input) {
        if (input.trim().isEmpty()) return Future.succeededFuture("");

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
                        return "All pilots are on the same team: " + distinctTeams.get(0);
                    } else if (distinctTeams.size() > 1) {
                        return "Pilots come from more than one team:\n" +
                               result.stream()
                                       .map(o -> (JsonObject) o)
                                       .map(o -> o.getString("pilot_name") + " -> " + o.getString("team_name"))
                                       .collect(Collectors.joining("\n"));
                    } else {
                        HashSet<String> whoDis = new HashSet<>(nameColumn);
                        whoDis.removeAll(knownPilots);
                        return "Some pilots aren't on any team in this tournament:\n"
                               + String.join(", ", whoDis);
                    }
                });
    }

    public Future<String> validatePilotsCanFlyShips(String input) {
        if (input.trim().isEmpty()) return Future.succeededFuture("");

        Set<Tuple2<String, String>> nameAndShip = Arrays.stream(input.split("\n"))
                .map(row -> row.split("\t"))
                .map(row -> Tuple.of(row[0], row[1]))
                .collect(Collectors.toSet());

        return CompositeFuture.all(nameAndShip.stream()
                .map(this::addRefreshToken)
                .collect(Collectors.toList()))
                .map(RefToolInput::resultsToTuples)
                .compose(this::fetchSkillsAndCreateResponse);
    }

    private Future<Tuple3<String, String, Object>> addRefreshToken(Tuple2<String, String> ns) {
        return dbClient.callDb(DbClient.DB_FETCH_REFRESH_TOKEN, ns._1()).map(msg -> ns.append(msg.body()));
    }

    private Future<String> fetchSkillsAndCreateResponse(List<Tuple3<String, String, String>> nameShipAndRefreshTokens) {
        String cantDo = nameShipAndRefreshTokens.stream()
                .filter(t -> t._3() == null)
                .map(t -> t._1() + " needs to login so eve.toys can fetch skills")
                .collect(Collectors.joining("\n"));
        return CompositeFuture.all(nameShipAndRefreshTokens.stream()
                .filter(t -> t._3() != null)
                .map(t -> {
                    String shipName = t._2();
                    OAuth2TokenImpl token = new OAuth2TokenImpl(oauth2, new JsonObject().put("refresh_token", t._3()));
                    return Future.future(token::refresh)
                            .compose(v -> {
                                JsonObject parsed = KeycloakHelper.parseToken(((AccessToken) token).opaqueAccessToken());
                                int characterId = Integer.parseInt(parsed.getString("sub").split(":")[2]);
                                return esi.lookupShip(shipName)
                                        .compose(json -> ShipSkillChecker.fetchShipDetails(esi, json))
                                        .map(ShipSkillChecker::fetchSkillRequirements)
                                        .compose(skillsAndLevels -> ShipSkillChecker.fetchUsersSkills(esi, token, characterId, skillsAndLevels))
                                        .compose(skillsAndLevels -> ShipSkillChecker.resolveSkillNames(esi, skillsAndLevels))
                                        .map(skillsAndLevels -> skillsAndLevels.stream()
                                                .map(o -> (JsonObject) o)
                                                .filter(sl -> sl.getInteger("toonLevel") < sl.getInteger("level"))
                                                .map(sl -> {
                                                    int requiredLevel = sl.getInteger("level");
                                                    int toonLevel = sl.getInteger("toonLevel");
                                                    return t._1() + " needs " + sl.getString("skill") + " to " + requiredLevel
                                                           + " to fly a " + t._2()
                                                           + " but it is " +
                                                           (toonLevel == 0 ? "untrained" : "only " + toonLevel);
                                                })
                                                .collect(Collectors.joining("\n")));
                            });
                })
                .collect(Collectors.toList()))
                .map(f -> cantDo + "\n" + String.join("\n", f.<String>list()));
    }
}
