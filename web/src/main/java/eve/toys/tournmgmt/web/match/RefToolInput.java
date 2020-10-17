package eve.toys.tournmgmt.web.match;

import eve.toys.tournmgmt.web.esi.Esi;
import eve.toys.tournmgmt.web.esi.ShipSkillChecker;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.auth.oauth2.KeycloakHelper;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.impl.OAuth2TokenImpl;
import toys.eve.tournmgmt.db.DbClient;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/*
{
    red; {
        input: "",
        noEsi: [ "player1", "player2"],
        teams: [ "Team A", 9 ]
        comp: [
            { pilot: "player1", ship: "svipul", "category": "destroyer", hasSkill: true, tooManyOfCategory: false, onTeam: true },
            { pilot: "player2",
                ship: "nightmare",
                category: "battleship",
                hasSkill: false,
                tooManyOfCategory: false,
                onTeam: false,
                skillsMsg: "Valador Solette needs Caldari Frigate to 5 to fly a Harpy but it is only 3" },
            { pilot: "player1", ship: "svipul", "category": "destroyer", hasSkill: true, tooManyOfCategory: false }
        ]
    },
    blue; {
        input: "",
        ...
    }
}
 */
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
        return f.list().stream().map(o -> (Tuple3<String, String, String>) o).collect(toList());
    }

    public Future<String> validateTeamMembership(String tournamentUuid, String input) {
        if (input.trim().isEmpty()) return Future.succeededFuture("");

        List<String> nameColumn = Arrays.stream(input.split("\n")).map(row -> row.split("\t")[0]).collect(toList());
        return dbClient.callDb(DbClient.DB_TEAMS_FOR_PILOT_LIST, new JsonObject()
                .put("tournamentUuid", tournamentUuid)
                .put("pilots", nameColumn))
                .map(msg -> {
                    JsonArray result = (JsonArray) msg.body();
                    List<String> distinctTeams = result.stream()
                            .map(o -> (JsonObject) o)
                            .map(o -> o.getString("team_name"))
                            .distinct()
                            .collect(toList());
                    List<String> knownPilots = result.stream()
                            .map(o -> (JsonObject) o)
                            .map(o -> o.getString("pilot_name"))
                            .collect(toList());
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
                .collect(toList()))
                .map(RefToolInput::resultsToTuples)
                .compose(this::fetchSkillsAndCreateResponse);
    }

    private Future<Tuple3<String, String, Object>> addRefreshToken(Tuple2<String, String> ns) {
        return dbClient.callDb(DbClient.DB_FETCH_REFRESH_TOKEN, ns._1()).map(msg -> ns.append(((JsonObject) msg.body()).getString("refresh_token")));
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
                .collect(toList()))
                .map(f -> cantDo + "\n" + String.join("\n", f.<String>list()));
    }

    public Future<JsonObject> process(String input, JsonObject output, String tournamentUuid) {
        return Future.future(promise -> {
            output.put("input", input);
            List<String> pilots = pilotsFromInput(input);
            CompositeFuture.all(
                    pilotsWithoutEsi(pilots).onSuccess(list -> output.put("noEsi", list)),
                    guessTeams(tournamentUuid, pilots).onSuccess(teams -> output.put("teams", teams)))
                    .compose(f ->
                            comp(input).compose(comp -> {
                                output.put("comp", comp);
                                String mainTeam = output.getJsonArray("teams").getJsonObject(0).getString("team_name");
                                forEachPilot(comp, pilotAndShip -> pilotIsOnTeam(pilotAndShip.getString("pilot"), mainTeam, tournamentUuid)
                                        .onSuccess(isOnTeam -> pilotAndShip.put("onTeam", isOnTeam)));
                                return CompositeFuture.all(
                                        Future.succeededFuture(),
                                        Future.succeededFuture()
                                );
                            }))
                    .onFailure(promise::fail)
                    .onSuccess(f -> {
                        promise.complete(output);
                    });
        });
    }

    private void forEachPilot(JsonArray comp, Function<JsonObject, Future<?>> fn) {
        CompositeFuture.all(comp.stream()
                .map(o -> (JsonObject) o)
                .map(fn)
                .collect(Collectors.toList()));
    }

    private Future<Boolean> pilotIsOnTeam(String pilot, String team, String tournamentUuid) {
        return dbClient.callDb(DbClient.DB_TEAMS_BY_PILOT, pilot)
                .map(msg -> msg.body())
                .map(teams -> ((JsonArray) teams).stream()
                        .map(o -> (JsonObject) o)
                        .filter(t -> t.getString("tournament_uuid").equals(tournamentUuid))
                        .anyMatch(t -> t.getString("team_name").equals(team)));
    }

    private Future<JsonArray> comp(String input) {
        return Future.succeededFuture(new JsonArray(Arrays.stream(input.split("\n"))
                .map(row -> row.split("\t"))
                .map(split -> new JsonObject()
                        .put("pilot", split[0])
                        .put("ship", split[1]))
                .collect(toList())));
    }

    private Future<JsonArray> guessTeams(String tournamentUuid, List<String> pilots) {
        return dbClient.callDb(DbClient.DB_TEAMS_FOR_PILOT_LIST, new JsonObject()
                .put("tournamentUuid", tournamentUuid)
                .put("pilots", new JsonArray(pilots)))
                .map(msg -> (JsonArray) msg.body())
                .map(teams -> withMissing(teams, pilots))
                .map(pilotAndTeam -> pilotAndTeam.stream()
                        .map(o -> (JsonObject) o)
                        .collect(Collectors.groupingBy(pt -> pt.getString("team_name"), Collectors.counting())))
                .map(map -> map.entrySet().stream()
                        .map(entry -> new JsonObject().put("team_name", entry.getKey()).put("count", entry.getValue()))
                        .sorted(Comparator.comparing(row -> -row.getInteger("count")))
                        .collect(Collectors.toList()))
                .map(JsonArray::new);
    }

    private JsonArray withMissing(JsonArray teams, List<String> pilots) {
        Set<String> foundPilots = teams.stream()
                .map(o -> (JsonObject) o)
                .map(t -> t.getString("pilot_name"))
                .collect(Collectors.toSet());
        HashSet<String> notFoundPilots = new HashSet<>(pilots);
        notFoundPilots.removeAll(foundPilots);
        JsonArray extras = new JsonArray(notFoundPilots.stream()
                .map(notFound -> new JsonObject().put("pilot_name", notFound).put("team_name", "unknown"))
                .collect(toList()));
        return teams.addAll(extras);
    }

    private Future<List<String>> pilotsWithoutEsi(List<String> pilots) {
        return CompositeFuture.all(pilots.stream().map(pilot -> dbClient.callDb(DbClient.DB_FETCH_REFRESH_TOKEN, pilot)).collect(toList()))
                .map(f -> f.list().stream()
                        .map(msg -> (Message<JsonObject>) msg)
                        .map(msg -> msg.body())
                        .filter(json -> json.getString("refresh_token") == null)
                        .map(json -> json.getString("character_name"))
                        .collect(toList()));
    }

    private List<String> pilotsFromInput(String input) {
        return Arrays.stream(input.split("\n"))
                .map(row -> row.split("\t")[0])
                .collect(toList());
    }
}
