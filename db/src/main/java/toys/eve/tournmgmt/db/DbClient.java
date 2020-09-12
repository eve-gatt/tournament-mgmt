package toys.eve.tournmgmt.db;

import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;

public final class DbClient {

    public static final String DB_CREATE_TOURNAMENT = "CREATE_TOURNAMENT";
    public static final String DB_FETCH_TOURNAMENTS = "DB_FETCH_TOURNAMENTS";
    public static final String DB_TOURNAMENT_BY_UUID = "DB_TOURNAMENT_BY_UUID";
    public static final String DB_TEAM_BY_UUID = "DB_TEAM_BY_UUID";
    public static final String DB_WRITE_TEAM_TSV = "DB_WRITE_TEAM_TSV";
    public static final String DB_TEAMS_BY_TOURNAMENT = "DB_TEAMS_BY_TOURNAMENT";
    public static final String DB_DELETE_TEAM_BY_UUID = "DB_DELETE_TEAM_BY_UUID";
    public static final String DB_EDIT_TOURNAMENT = "DB_EDIT_TOURNAMENT";
    public static final String DB_WRITE_TEAM_MEMBERS_TSV = "DB_WRITE_TEAM_MEMBERS_TSV";
    public static final String DB_MEMBERS_BY_TEAM = "DB_MEMBERS_BY_TEAM";
    public static final String DB_LOCK_TEAM_BY_UUID = "DB_LOCK_TEAM_BY_UUID";
    public static final String DB_ALL_TEAMS = "DB_ALL_TEAMS";
    public static final String DB_UPDATE_TEAM_MESSAGE = "DB_UPDATE_TEAM_MESSAGE";
    public static final String DB_ROLES_BY_TOURNAMENT = "DB_ROLES_BY_TOURNAMENT";
    public static final String DB_REPLACE_ROLES_BY_TYPE_AND_TOURNAMENT = "DB_REPLACE_ROLES_BY_TYPE_AND_TOURNAMENT";
    public static final String DB_HAS_ROLE = "DB_HAS_ROLE";
    public static final String DB_PROBLEMS_BY_TOURNAMENT = "DB_PROBLEMS_BY_TOURNAMENT";

    private final EventBus eventBus;

    public DbClient(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public <T> Future<Message<Object>> callDb(String call, T params) {
        return Future.future(promise -> eventBus.request(call, params, promise));
    }
}
