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
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

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

        try {
            Flyway flyway = Flyway.configure()
                    .baselineVersion("002")
                    .dataSource(URL, USER, PASSWORD)
                    .load();
            if (Boolean.parseBoolean(System.getProperty("db_do_clean", "false"))) {
                LOGGER.info("Flyway cleaning");
                flyway.clean();
            }
            LOGGER.info("Flyway migration");
            flyway.baseline();
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
        vertx.eventBus().consumer(DbClient.DB_LOCK_TEAM_BY_UUID, this::lockTeamByUuid);
        vertx.eventBus().consumer(DbClient.DB_WRITE_TEAM_MEMBERS_TSV, this::writeTeamMembersTsv);
        vertx.eventBus().consumer(DbClient.DB_MEMBERS_BY_TEAM, this::membersByTeam);
        vertx.eventBus().consumer(DbClient.DB_ALL_TEAMS, this::allTeams);
        vertx.eventBus().consumer(DbClient.DB_UPDATE_TEAM_MESSAGE, this::updateTeamMessage);
        vertx.eventBus().consumer(DbClient.DB_ROLES_BY_TOURNAMENT, this::rolesByTournament);
        vertx.eventBus().consumer(DbClient.DB_REPLACE_ROLES_BY_TYPE_AND_TOURNAMENT, this::replaceRolesByTypeAndTournament);
        vertx.eventBus().consumer(DbClient.DB_HAS_ROLE, this::hasRole);
        vertx.eventBus().consumer(DbClient.DB_PROBLEMS_BY_TOURNAMENT, this::problemsByTournament);
        vertx.eventBus().consumer(DbClient.DB_TOURNAMENTS_CHARACTER_CAN_VIEW, this::tournamentsCharacterCanView);

        startPromise.complete();
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
        sqlClient.queryWithParams("select name, uuid, start_date, practice_on_td, play_on_td, " +
                        "(select count(*) from team where tournament_uuid = '" + uuid + "') as team_count, " +
                        "(select count(*) from team_member inner join team on team_member.team_uuid = team.uuid where team.tournament_uuid = '" + uuid + "') as pilot_count, " +
                        "exists(select 1 from team where team.tournament_uuid = tournament.uuid and captain = ?) as is_captain, " +
                        "exists(select 1 from team_member inner join team on team_member.team_uuid = team.uuid where team.tournament_uuid = tournament.uuid and team_member.name = ?) as is_pilot, " +
                        "(select string_agg(type::varchar, ',') as roles from tournament_role where tournament.uuid = tournament_role.tournament_uuid and tournament_role.name = ?) " +
                        "from tournament " +
                        "where uuid = '" + uuid + "'",
                new JsonArray()
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

    private void teamByUuid(Message<String> msg) {
        String uuid = msg.body();
        sqlClient.query("select name, captain, uuid, locked " +
                        "from team " +
                        "where uuid = '" + uuid + "'",
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
                        String character = row.getString(1).replaceAll("'", "''");
                        return "(" +
                                "'" + UUID.randomUUID().toString() + "'" +
                                ", " +
                                "'" + uuid + "'" +
                                ", " +
                                "'" + alliance + "'" +
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
        sqlClient.update("insert into team (uuid, tournament_uuid, name, captain, created_by) " +
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
        sqlClient.query("select name, uuid, captain, locked, msg, " +
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

    private void lockTeamByUuid(Message<String> msg) {
        String uuid = msg.body();
        sqlClient.update("update team set locked = true " +
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
        sqlClient.query("select name, captain, uuid from team", ar -> {
            if (ar.failed()) {
                ar.cause().printStackTrace();
                msg.fail(1, ar.cause().getMessage());
            } else {
                msg.reply(new JsonArray(ar.result().getRows()));
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
        sqlClient.query("select msg " +
                        "from team " +
                        "where tournament_uuid = '" + uuid + "' " +
                        "and (msg = '') is not true",
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
        sqlClient.queryWithParams("select uuid, name, created_by, practice_on_td, play_on_td, teams_locked, " +
                        "exists(select 1 from team where team.tournament_uuid = tournament.uuid and captain = ?) as is_captain, " +
                        "exists(select 1 from team_member inner join team on team_member.team_uuid = team.uuid where team.tournament_uuid = tournament.uuid and team_member.name = ?) as is_pilot, " +
                        "(select string_agg(type::varchar, ',') as roles from tournament_role where tournament.uuid = tournament_role.tournament_uuid and tournament_role.name = ?) " +
                        "from tournament " +
                        "where " +
                        "   ? = ? " +
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
                        .add(System.getenv("SUPERUSER"))
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

    private void rollback(SQLConnection conn) {
        conn.rollback(ar -> {
            if (ar.failed()) {
                ar.cause().printStackTrace();
            }
        });
    }
}
