package toys.eve.tournmgmt.db;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class DbVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbVerticle.class.getName());
    private static final String URL = System.getenv("POSTGRES_URL");
    private static final String USER = System.getenv("POSTGRES_USER");
    private static final String PASSWORD = System.getenv("POSTGRES_PASSWORD");
    private SQLClient sqlClient;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        LOGGER.info("Initialising Flyway");

        Objects.requireNonNull(URL, "Please supply POSTGRES_URL");
        Objects.requireNonNull(USER, "Please supply POSTGRES_USER");
        Objects.requireNonNull(PASSWORD, "Please supply POSTGRES_PASSWORD");

        try {
            Flyway flyway = Flyway.configure()
//                    .baselineVersion("002")
                    .dataSource(URL, USER, PASSWORD)
                    .load();
            if (Boolean.parseBoolean(System.getProperty("db_do_clean", "false"))) {
                LOGGER.info("Flyway cleaning");
                flyway.clean();
            }
            LOGGER.info("Flyway migration");
//            flyway.baseline();
            flyway.migrate();
        } catch (FlywayException e) {
            startPromise.fail(e);
            return;
        }

        LOGGER.info("Successfully migrated database schema");

        JsonObject config = new JsonObject()
                .put("driver_class", "org.postgresql.Driver")
                .put("url", URL)
                .put("user", USER)
                .put("password", PASSWORD);
        sqlClient = JDBCClient.createShared(vertx, config);

        vertx.eventBus().consumer(DbClient.DB_CREATE_TOURNAMENT, this::createTournament);
        vertx.eventBus().consumer(DbClient.DB_EDIT_TOURNAMENT, this::editTournament);
        vertx.eventBus().consumer(DbClient.DB_FETCH_TOURNAMENTS, this::fetchAllTournaments);
        vertx.eventBus().consumer(DbClient.DB_TOURNAMENT_BY_UUID, this::tournamentByUuid);
        vertx.eventBus().consumer(DbClient.DB_TEAM_BY_UUID, this::teamByUuid);
        vertx.eventBus().consumer(DbClient.DB_PILOT_BY_UUID, this::pilotByUuid);
        vertx.eventBus().consumer(DbClient.DB_KICK_PILOT_BY_UUID, this::kickPilotByUuid);
        vertx.eventBus().consumer(DbClient.DB_WRITE_TEAM_TSV, this::writeTeamTsv);
        vertx.eventBus().consumer(DbClient.DB_TEAMS_BY_TOURNAMENT, this::teamsByTournament);
        vertx.eventBus().consumer(DbClient.DB_DELETE_TEAM_BY_UUID, this::deleteTeamByUuid);
        vertx.eventBus().consumer(DbClient.DB_TOGGLE_LOCK_TEAM_BY_UUID, this::toggleLockTeamByUuid);
        vertx.eventBus().consumer(DbClient.DB_WRITE_TEAM_MEMBERS_TSV, this::writeTeamMembersTsv);
        vertx.eventBus().consumer(DbClient.DB_MEMBERS_BY_TEAM, this::membersByTeam);
        vertx.eventBus().consumer(DbClient.DB_ALL_TEAMS, this::allTeams);
        vertx.eventBus().consumer(DbClient.DB_CLEAR_ALL_TOURNAMENT_MSGS, this::clearAllTournamentMsgs);
        vertx.eventBus().consumer(DbClient.DB_UPDATE_TEAM_MESSAGE, this::updateTeamMessage);
        vertx.eventBus().consumer(DbClient.DB_ROLES_BY_TOURNAMENT, this::rolesByTournament);
        vertx.eventBus().consumer(DbClient.DB_REPLACE_ROLES_BY_TYPE_AND_TOURNAMENT, this::replaceRolesByTypeAndTournament);
        vertx.eventBus().consumer(DbClient.DB_HAS_ROLE, this::hasRole);
        vertx.eventBus().consumer(DbClient.DB_PROBLEMS_BY_TOURNAMENT, this::problemsByTournament);
        vertx.eventBus().consumer(DbClient.DB_TOURNAMENTS_CHARACTER_CAN_VIEW, this::tournamentsCharacterCanView);
        vertx.eventBus().consumer(DbClient.DB_ALL_CAPTAINS, this::allCaptains);
        vertx.eventBus().consumer(DbClient.DB_ALL_PILOTS, this::allPilots);
        vertx.eventBus().consumer(DbClient.DB_TD_SUMMARY_BY_TOURNAMENT, this::tdSummaryByTournament);
        vertx.eventBus().consumer(DbClient.DB_RECORD_NAME_IN_USE, this::recordNameInUse);
        vertx.eventBus().consumer(DbClient.DB_CHECK_NAME_IN_USE_REPORTS, this::checkNameInUse);
        vertx.eventBus().consumer(DbClient.DB_TEAMS_BY_PILOT, this::teamsByPilot);
        vertx.eventBus().consumer(DbClient.DB_PILOTS_AND_CAPTAIN_BY_TEAM, this::pilotsAndCaptainByTeam);
        vertx.eventBus().consumer(DbClient.DB_MAYBE_ALLOCATE_TD_ACCOUNT, this::maybeAllocateTdAccount);
        vertx.eventBus().consumer(DbClient.DB_SELECT_TD_BY_PILOT, this::tdByPilot);
        vertx.eventBus().consumer(DbClient.DB_RECORD_REFTOOL_INPUTS, this::recordReftoolInputs);
        vertx.eventBus().consumer(DbClient.DB_CLEAR_PROBLEMS, this::clearProblems);
        vertx.eventBus().consumer(DbClient.DB_ADD_PROBLEM, this::addProblem);
        vertx.eventBus().consumer(DbClient.DB_PILOT_NAMES_IN_USE, this::pilotNamesInUse);
        vertx.eventBus().consumer(DbClient.DB_TEAMS_FOR_PILOT_LIST, this::teamsForPilotList);
        vertx.eventBus().consumer(DbClient.DB_WRITE_LOGIN, this::writeLogin);
        vertx.eventBus().consumer(DbClient.DB_FETCH_REFRESH_TOKEN, this::fetchRefreshToken);

        startPromise.complete();
    }

    private void fetchRefreshToken(Message<String> msg) {
        String user = msg.body();
        sqlClient.queryWithParams("select refresh_token from logins where character_name = ?",
                new JsonArray().add(user),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        ResultSet result = ar.result();
                        if (result.getNumRows() == 1) {
                            msg.reply(result.getResults().get(0).getString(0));
                        } else {
                            msg.reply(null);
                        }
                    }
                });
    }

    private void createTournament(Message<JsonObject> msg) {
        JsonObject input = msg.body();
        sqlClient.updateWithParams("insert into tournament(uuid, name, start_date, practice_on_td, play_on_td, created_by) " +
                                   "values ('" + UUID.randomUUID().toString() + "', ?, ?, ?, ?, ?)",
                new JsonArray()
                        .add(input.getString("name").trim())
                        .add(input.getInstant("parsedStartDate"))
                        .add(input.getBoolean("practiceOnTd"))
                        .add(input.getBoolean("playOnTd"))
                        .add(input.getString("createdBy")),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(input);
                    }
                });
    }

    private void editTournament(Message<JsonObject> msg) {
        JsonObject input = msg.body();
        sqlClient.updateWithParams("update tournament " +
                                   "set name = ?, " +
                                   "start_date = ?, " +
                                   "practice_on_td = ?, " +
                                   "play_on_td = ? " +
                                   "where uuid = '" + input.getString("uuid") + "'",
                new JsonArray()
                        .add(input.getString("name").trim())
                        .add(input.getInstant("parsedStartDate"))
                        .add(input.getBoolean("practiceOnTd"))
                        .add(input.getBoolean("playOnTd")),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(input);
                    }
                });
    }

    private void fetchAllTournaments(Message<JsonObject> msg) {
        sqlClient.query(
                "select uuid, name, created_by, practice_on_td, play_on_td, teams_locked " +
                "from tournament",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                        return;
                    }
                    msg.reply(new JsonArray(ar.result().getRows()));
                }
        );
    }

    private void tournamentByUuid(Message<JsonObject> msg) {
        String uuid = msg.body().getString("uuid");
        String characterName = msg.body().getString("characterName");
        sqlClient.queryWithParams("select uuid, name, start_date, created_by, practice_on_td, play_on_td, teams_locked, " +
                                  "(created_by = ?) as is_creator, " +
                                  "(select count(*) from team where tournament_uuid = '" + uuid + "') as team_count, " +
                                  "(select count(*) from team where tournament_uuid = '" + uuid + "' and locked = true) as team_locked_count, " +
                                  "(select count(*) from team_member inner join team on team_member.team_uuid = team.uuid where team.tournament_uuid = '" + uuid + "') as pilot_count, " +
                                  "exists(select 1 from team where team.tournament_uuid = tournament.uuid and captain = ?) as is_captain, " +
                                  "exists(select 1 from team_member inner join team on team_member.team_uuid = team.uuid where team.tournament_uuid = tournament.uuid and team_member.name = ?) as is_pilot, " +
                                  "(select string_agg(type::varchar, ',') as roles from tournament_role where tournament.uuid = tournament_role.tournament_uuid and tournament_role.name = ?) " +
                                  "from tournament " +
                                  "where uuid = '" + uuid + "'",
                new JsonArray()
                        .add(characterName)
                        .add(characterName)
                        .add(characterName)
                        .add(characterName),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                        return;
                    }
                    if (ar.result().getRows().size() != 1) {
                        msg.fail(2, "No tournament found for uuid: " + uuid);
                        return;
                    }
                    msg.reply(ar.result().getRows().get(0));
                });
    }

    private void teamByUuid(Message<JsonObject> msg) {
        String uuid = msg.body().getString("uuid");
        String characterName = msg.body().getString("characterName");
        sqlClient.queryWithParams("select name, captain, logo, uuid, locked, " +
                                  "(captain = ?) as is_captain " +
                                  "from team " +
                                  "where uuid = '" + uuid + "'",
                new JsonArray().add(characterName),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                        return;
                    }
                    if (ar.result().getRows().size() != 1) {
                        msg.fail(2, "No team found for uuid: " + uuid);
                        return;
                    }
                    msg.reply(ar.result().getRows().get(0));
                });
    }

    private void pilotByUuid(Message<String> msg) {
        String uuid = msg.body();
        sqlClient.query("select uuid, name " +
                        "from team_member " +
                        "where uuid = '" + uuid + "'",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                        return;
                    }
                    if (ar.result().getRows().size() != 1) {
                        msg.fail(2, "No pilot found for uuid: " + uuid);
                        return;
                    }
                    msg.reply(ar.result().getRows().get(0));
                });
    }

    private void kickPilotByUuid(Message<String> msg) {
        String uuid = msg.body();
        sqlClient.update("delete from team_member " +
                         "where uuid = '" + uuid + "'",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                        return;
                    }
                    msg.reply(null);
                });
    }

    private void writeTeamTsv(Message<JsonObject> msg) {
        JsonArray tsv = msg.body().getJsonArray("tsv");
        String createdBy = msg.body().getString("createdBy");
        String uuid = msg.body().getString("uuid");
        String values = tsv.stream()
                .map(row -> (JsonArray) row)
                .map(row -> {
                    try {
                        String alliance = row.getString(0).replaceAll("'", "''");
                        String logo = row.getString(1);
                        String character = row.getString(2).replaceAll("'", "''");
                        return "(" +
                               "'" + UUID.randomUUID().toString() + "'" +
                               ", " +
                               "'" + uuid + "'" +
                               ", " +
                               "'" + alliance + "'" +
                               ", " +
                               "'" + logo + "'" +
                               ", " +
                               "'" + character + "'" +
                               ", " +
                               "'" + createdBy + "'" +
                               ")";
                    } catch (Exception e) {
                        e.printStackTrace();
                        msg.fail(2, e.getMessage());
                        return null;
                    }
                })
                .collect(Collectors.joining(","));
        sqlClient.update("insert into team (uuid, tournament_uuid, name, logo, captain, created_by) " +
                         "values " + values,
                ar -> {
                    if (ar.failed()) {
                        if (!ar.cause().getMessage().contains("duplicate key value violates unique constraint"))
                            ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                        return;
                    }
                    msg.reply(null);
                });
    }

    private void teamsByTournament(Message<JsonObject> msg) {
        String uuid = msg.body().getString("uuid");
        sqlClient.query("select name, logo, uuid, captain, locked, " +
                        "(select string_agg(message, ', ') from problems where type = 'team' and referenced_entity = team.uuid) as message, " +
                        "(select count(*) from team_member where team.uuid = team_uuid) as member_count " +
                        "from team " +
                        "where tournament_uuid = '" + uuid + "'",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                        return;
                    }
                    msg.reply(new JsonArray(ar.result().getRows()));
                });
    }

    private void deleteTeamByUuid(Message<String> msg) {
        String uuid = msg.body();
        sqlClient.update("delete from team where uuid = '" + uuid + "'",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                        return;
                    }
                    msg.reply(null);
                });
    }

    private void toggleLockTeamByUuid(Message<String> msg) {
        String uuid = msg.body();
        sqlClient.update("update team set locked = not locked " +
                         "where uuid = '" + uuid + "'",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                        return;
                    }
                    msg.reply(null);
                });
    }

    private void writeTeamMembersTsv(Message<JsonObject> msg) {
        JsonArray tsv = msg.body().getJsonArray("tsv");
        String addedBy = msg.body().getString("addedBy");
        String uuid = msg.body().getString("uuid");
        String values = tsv.stream()
                .map(o -> (JsonArray) o)
                .map(row -> {
                    String character = row.getString(0).trim().replaceAll("'", "''");
                    return "(" +
                           "'" + UUID.randomUUID().toString() + "'" +
                           ", " +
                           "'" + uuid + "'" +
                           ", " +
                           "'" + character + "'" +
                           ", " +
                           "'" + addedBy + "'" +
                           ")";
                })
                .collect(Collectors.joining(","));
        sqlClient.update("insert into team_member (uuid, team_uuid, name, added_by) " +
                         "values " + values,
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                        return;
                    }
                    msg.reply(null);
                });
    }

    private void membersByTeam(Message<String> msg) {
        String uuid = msg.body();
        sqlClient.query("select name, uuid " +
                        "from team_member " +
                        "where team_uuid = '" + uuid + "'",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                        return;
                    }
                    msg.reply(new JsonArray(ar.result().getRows()));
                });
    }

    private void allTeams(Message<JsonObject> msg) {
        sqlClient.query("select name, captain, uuid, tournament_uuid from team", ar -> {
            if (ar.failed()) {
                ar.cause().printStackTrace();
                msg.fail(1, ar.cause().getMessage());
            } else {
                msg.reply(new JsonArray(ar.result().getRows()));
            }
        });
    }

    private void clearAllTournamentMsgs(Message<Void> msg) {
        sqlClient.update("update tournament set msg = null",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(null);
                    }
                });
    }

    private void updateTeamMessage(Message<JsonObject> msg) {
        String message = msg.body().getString("message");
        String uuid = msg.body().getString("uuid");
        sqlClient.updateWithParams("update team set msg = ? " +
                                   "where uuid = '" + uuid + "'",
                new JsonArray().add(message),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(null);
                    }
                });
    }

    private void rolesByTournament(Message<String> msg) {
        String uuid = msg.body();
        sqlClient.query("select * from tournament_role where tournament_uuid = '" + uuid + "'",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                        return;
                    }
                    msg.reply(new JsonArray(ar.result().getRows()));
                });
    }

    private void replaceRolesByTypeAndTournament(Message<JsonObject> msg) {
        String type = msg.body().getString("type");
        String tournamentUuid = msg.body().getString("tournamentUuid");
        JsonArray names = msg.body().getJsonArray("names");

        Future<SQLConnection> getConnection = Future.future(promise -> sqlClient.getConnection(promise));

        getConnection.onSuccess(conn -> {
            conn.setAutoCommit(false, ar -> {
                if (ar.failed()) {
                    ar.cause().printStackTrace();
                    msg.fail(1, "1. " + ar.cause().getMessage());
                } else {
                    conn.updateWithParams("delete from tournament_role " +
                                          "where tournament_uuid = '" + tournamentUuid + "' " +
                                          "and type::text = ?",
                            new JsonArray().add(type),
                            ar2 -> {
                                if (ar2.failed()) {
                                    rollback(conn);
                                    msg.fail(1, "2. " + ar2.cause().getMessage());
                                }
                            })
                            .batchWithParams("insert into tournament_role(tournament_uuid, type, name) " +
                                             "values ('" + tournamentUuid + "', ?::role_type, ?)",
                                    names.stream()
                                            .map(o -> (JsonArray) o)
                                            .map(name -> new JsonArray()
                                                    .add(type)
                                                    .add(name.getString(0)))
                                            .collect(Collectors.toList()),
                                    ar3 -> {
                                        if (ar3.failed()) {
                                            rollback(conn);
                                            msg.fail(1, "3. " + ar3.cause().getMessage());
                                        } else {
                                        }
                                    })
                            .commit(ar4 -> {
                                if (ar4.failed()) {
                                    rollback(conn);
                                    msg.fail(1, "4. " + ar4.cause().getMessage());
                                } else {
                                    msg.reply(new JsonObject());
                                }
                            });
                }
            });
        });
    }

    private void hasRole(Message<JsonObject> msg) {
        String name = msg.body().getString("name");
        String uuid = msg.body().getString("uuid");
        String type = msg.body().getString("type");
        sqlClient.queryWithParams("select * from tournament_role " +
                                  "where tournament_uuid = '" + uuid + "' " +
                                  "and name = ? " +
                                  "and type = ?::role_type",
                new JsonArray().add(name).add(type),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(ar.result().getNumRows() > 0);
                    }
                });
    }

    private void problemsByTournament(Message<String> msg) {
        String uuid = msg.body();
        sqlClient.query("select message " +
                        "from problems " +
                        "where tournament_uuid = '" + uuid + "'",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                        return;
                    }
                    msg.reply(new JsonArray(ar.result().getRows()));
                });
    }

    private void tournamentsCharacterCanView(Message<String> msg) {
        String characterName = msg.body();
        sqlClient.queryWithParams("select uuid, name, start_date, created_by, practice_on_td, play_on_td, teams_locked, " +
                                  "(created_by = ?) as is_creator, " +
                                  "exists(select 1 from team where team.tournament_uuid = tournament.uuid and captain = ?) as is_captain, " +
                                  "exists(select 1 from team_member inner join team on team_member.team_uuid = team.uuid where team.tournament_uuid = tournament.uuid and team_member.name = ?) as is_pilot, " +
                                  "(select string_agg(type::varchar, ',') as roles from tournament_role where tournament.uuid = tournament_role.tournament_uuid and tournament_role.name = ?) " +
                                  "from tournament " +
                                  "where " +
                                  "   ? = ? " +
                                  "   or created_by = ? " +
                                  "   or exists(select 1 " +
                                  "             from team " +
                                  "             where team.tournament_uuid = tournament.uuid " +
                                  "               and captain = ?) " +
                                  "   or exists(select 1 " +
                                  "             from team_member " +
                                  "                      inner join team on team_member.team_uuid = team.uuid " +
                                  "             where team.tournament_uuid = tournament.uuid " +
                                  "               and team_member.name = ?) " +
                                  "   or exists(select 1 " +
                                  "             from tournament_role " +
                                  "             where tournament_role.tournament_uuid = tournament.uuid " +
                                  "               and tournament_role.name = ? " +
                                  "               and tournament_role.type in ('organiser', 'referee', 'staff')) ",
                new JsonArray()
                        .add(characterName)
                        .add(characterName)
                        .add(characterName)
                        .add(characterName)
                        .add(System.getenv("SUPERUSER"))
                        .add(characterName)
                        .add(characterName)
                        .add(characterName)
                        .add(characterName)
                        .add(characterName),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(new JsonArray(ar.result().getRows()));
                    }
                });
    }

    private void allCaptains(Message<Void> msg) {
        sqlClient.query("select tournament_uuid, uuid as team_uuid, captain as name from team",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(new JsonArray(ar.result().getRows()));
                    }
                });
    }

    private void allPilots(Message<Void> msg) {
        sqlClient.query("select " +
                        "tournament_uuid, " +
                        "team.uuid as team_uuid, " +
                        "team.name as team_name, " +
                        "team_member.name as name " +
                        "from team_member inner join team on team_member.team_uuid = team.uuid",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(new JsonArray(ar.result().getRows()));
                    }
                });
    }

    private void tdSummaryByTournament(Message<String> msg) {
        String uuid = msg.body();
        sqlClient.query("select " +
                        "    (select count(*) from thunderdome where tournament_uuid = '" + uuid + "' and allocated_to is not null) as allocated, " +
                        "    (select count(*) from thunderdome where tournament_uuid = '" + uuid + "') as total",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(ar.result().getRows().get(0));
                    }
                });
    }

    private void recordNameInUse(Message msg) {
        String name = (String) msg.body();
        sqlClient.updateWithParams("insert into name_in_use_reports (uuid, name) " +
                                   "values ('" + UUID.randomUUID().toString() + "', ?)",
                new JsonArray().add(name),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(null);
                    }
                });
    }

    private void checkNameInUse(Message<String> msg) {
        String name = msg.body();
        sqlClient.queryWithParams("select reported_at, resolved_at, resolved_by " +
                                  "from name_in_use_reports " +
                                  "where name = ?",
                new JsonArray().add(name),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(new JsonArray(ar.result().getRows()));
                    }
                });
    }

    private void teamsByPilot(Message<String> msg) {
        String pilotName = msg.body();
        sqlClient.queryWithParams("select team.name team_name, " +
                                  "       team.uuid team_uuid, " +
                                  "       t.name    tournament_name, " +
                                  "       t.uuid    tournament_uuid " +
                                  "from team " +
                                  "         inner join tournament t on team.tournament_uuid = t.uuid " +
                                  "where team.captain = ? " +
                                  "   or exists(select 1 " +
                                  "             from team_member " +
                                  "                      inner join team t2 on team_member.team_uuid = t2.uuid " +
                                  "             where team.tournament_uuid = t.uuid " +
                                  "               and team_member.name = ? " +
                                  "               and t2.uuid = team.uuid)",
                new JsonArray().add(pilotName).add(pilotName),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(new JsonArray(ar.result().getRows()));
                    }
                });
    }

    private void pilotsAndCaptainByTeam(Message<String> msg) {
        String uuid = msg.body();
        sqlClient.query("select captain as pilot " +
                        "from team " +
                        "where uuid = '" + uuid + "' " +
                        "union " +
                        "select name as pilot " +
                        "from team_member " +
                        "where team_uuid = '" + uuid + "' ", ar -> {
            if (ar.failed()) {
                ar.cause().printStackTrace();
                msg.fail(1, ar.cause().getMessage());
            } else {
                msg.reply(new JsonArray(ar.result().getRows()));
            }
        });
    }

    private void maybeAllocateTdAccount(Message<JsonObject> msg) {
        String tournamentUuid = msg.body().getString("tournamentUuid");
        String pilot = msg.body().getString("pilot");
        sqlClient.updateWithParams("update thunderdome " +
                                   "set allocated_to = ? " +
                                   "where tournament_uuid = ?::uuid " +
                                   "  and not exists(select 1 " +
                                   "                 from thunderdome " +
                                   "                 where tournament_uuid = ?::uuid " +
                                   "                   and allocated_to = ?) " +
                                   "  and id = (select min(id) " +
                                   "            from thunderdome " +
                                   "            where tournament_uuid = ?::uuid " +
                                   "              and allocated_to is null) ",
                new JsonArray()
                        .add(pilot)
                        .add(tournamentUuid)
                        .add(tournamentUuid)
                        .add(pilot)
                        .add(tournamentUuid),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(ar.result().toJson());
                    }
                });
    }

    private void tdByPilot(Message<String> msg) {
        String characterName = msg.body();
        sqlClient.queryWithParams("select username, password from thunderdome where allocated_to = ? ",
                new JsonArray().add(characterName),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        List<JsonObject> rows = ar.result().getRows();
                        if (rows.size() == 0) {
                            msg.reply(new JsonObject());
                        } else {
                            msg.reply(rows.get(0));
                        }
                    }
                });
    }

    private void recordReftoolInputs(Message<JsonObject> msg) {
        JsonObject input = msg.body();
        sqlClient.updateWithParams("insert into reftool_inputs (red, blue, added_by) " +
                                   "values (?, ?, ?)",
                new JsonArray()
                        .add(input.getString("red"))
                        .add(input.getString("blue"))
                        .add(input.getString("addedBy")),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(input);
                    }
                });
    }

    private void clearProblems(Message<JsonObject> msg) {
        String problemType = msg.body().getString("type");
        String validationIdentifier = msg.body().getString("name");
        sqlClient.updateWithParams("delete from problems " +
                                   "where type = ?::problem_type " +
                                   "and validation_identifier = ?",
                new JsonArray().add(problemType).add(validationIdentifier),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(msg.body());
                    }
                });
    }

    private void addProblem(Message<JsonObject> msg) {
        String problemType = msg.body().getString("type");
        String tournamentUuid = msg.body().getString("tournamentUuid");
        String validationIdentifier = msg.body().getString("validationIdentifier");
        String referencedEntity = msg.body().getString("referencedEntity");
        String message = msg.body().getString("message");
        sqlClient.updateWithParams("insert into problems " +
                                   "(type, tournament_uuid, validation_identifier, referenced_entity, message, created_by) " +
                                   "values(?::problem_type, ?::uuid, ?, ?::uuid, ?, ?)",
                new JsonArray()
                        .add(problemType)
                        .add(tournamentUuid)
                        .add(validationIdentifier)
                        .add(referencedEntity)
                        .add(message)
                        .add("system"),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(msg.body());
                    }
                });
    }

    private void pilotNamesInUse(Message<JsonObject> msg) {
        sqlClient.query("select reported_at, name, username, resolved " +
                        "from name_in_use_reports " +
                        "         inner join thunderdome on name_in_use_reports.name = thunderdome.allocated_to " +
                        "where not resolved " +
                        "   or resolved_at > CURRENT_DATE - interval '1 month' " +
                        "order by resolved, reported_at desc " +
                        "limit 100",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(new JsonArray(ar.result().getRows()));
                    }
                });
    }

    private void teamsForPilotList(Message<JsonObject> msg) {
        String tournamentUuid = msg.body().getString("tournamentUuid");
        JsonArray pilots = msg.body().getJsonArray("pilots");
        sqlClient.queryWithParams("select captain as pilot_name, team.name as team_name " +
                                  "from team " +
                                  "where " +
                                  "tournament_uuid = ?::uuid " +
                                  "and captain in (" + placeholders(pilots.size()) + ") " +
                                  "union " +
                                  "select team_member.name as pilot_name, team.name as team_name " +
                                  "from team " +
                                  "         left join team_member on team_member.team_uuid = team.uuid " +
                                  "where " +
                                  "tournament_uuid = ?::uuid " +
                                  "and team_member.name in (" + placeholders(pilots.size()) + ")",
                new JsonArray()
                        .add(tournamentUuid)
                        .addAll(pilots)
                        .add(tournamentUuid)
                        .addAll(pilots),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(new JsonArray(ar.result().getRows()));
                    }
                });
    }

    private void writeLogin(Message<JsonObject> msg) {
        sqlClient.updateWithParams("insert into logins " +
                                   "(character_id, character_name, scopes, refresh_token, last_seen) " +
                                   "values(?, ?, ?, ?, ?) " +
                                   "on conflict (character_id) " +
                                   "do update set " +
                                   "   character_name = ?, " +
                                   "   scopes = ?, " +
                                   "   refresh_token = ?, " +
                                   "   last_seen = ? ",
                new JsonArray()
                        .add(msg.body().getInteger("characterId"))
                        .add(msg.body().getString("characterName"))
                        .add(msg.body().getString("scopes"))
                        .add(msg.body().getString("refreshToken"))
                        .add(msg.body().getInstant("lastSeen"))
                        // update
                        .add(msg.body().getString("characterName"))
                        .add(msg.body().getString("scopes"))
                        .add(msg.body().getString("refreshToken"))
                        .add(msg.body().getInstant("lastSeen")),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(null);
                    }
                });
    }

    private void rollback(SQLConnection conn) {
        conn.rollback(ar -> {
            if (ar.failed()) {
                ar.cause().printStackTrace();
            }
        });
    }

    private String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
