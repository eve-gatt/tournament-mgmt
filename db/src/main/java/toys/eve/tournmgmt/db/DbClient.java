package toys.eve.tournmgmt.db;

import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;

import java.util.regex.Pattern;

public class DbClient {

    public static final Pattern DUPE_REGEX = Pattern.compile(".*Detail: Key \\([^=]+=\\(([^,]+), ([^\\)]+)\\) already exists\\.");

    public static final String DB_CREATE_TOURNAMENT = "CREATE_TOURNAMENT";
    public static final String DB_FETCH_TOURNAMENTS = "DB_FETCH_TOURNAMENTS";
    public static final String DB_TOURNAMENT_BY_UUID = "DB_TOURNAMENT_BY_UUID";
    public static final String DB_TOURNAMENTS_CHARACTER_CAN_VIEW = "DB_TOURNAMENTS_CHARACTER_CAN_VIEW";
    public static final String DB_TEAM_BY_UUID = "DB_TEAM_BY_UUID";
    public static final String DB_WRITE_TEAM_TSV = "DB_WRITE_TEAM_TSV";
    public static final String DB_TEAMS_BY_TOURNAMENT = "DB_TEAMS_BY_TOURNAMENT";
    public static final String DB_DELETE_TEAM_BY_UUID = "DB_DELETE_TEAM_BY_UUID";
    public static final String DB_EDIT_TOURNAMENT = "DB_EDIT_TOURNAMENT";
    public static final String DB_WRITE_TEAM_MEMBERS_TSV = "DB_WRITE_TEAM_MEMBERS_TSV";
    public static final String DB_MEMBERS_BY_TEAM = "DB_MEMBERS_BY_TEAM";
    public static final String DB_TOGGLE_LOCK_TEAM_BY_UUID = "DB_LOCK_TEAM_BY_UUID";
    public static final String DB_ALL_TEAMS = "DB_ALL_TEAMS";
    public static final String DB_ROLES_BY_TOURNAMENT = "DB_ROLES_BY_TOURNAMENT";
    public static final String DB_REPLACE_ROLES_BY_TYPE_AND_TOURNAMENT = "DB_REPLACE_ROLES_BY_TYPE_AND_TOURNAMENT";
    public static final String DB_HAS_ROLE = "DB_HAS_ROLE";
    public static final String DB_PROBLEMS_BY_TOURNAMENT = "DB_PROBLEMS_BY_TOURNAMENT";
    public static final String DB_PROBLEMS_BY_TEAM = "DB_PROBLEMS_BY_TEAM";
    public static final String DB_PILOT_BY_UUID = "DB_PILOT_BY_UUID";
    public static final String DB_KICK_PILOT_BY_UUID = "DB_KICK_PILOT_BY_UUID";
    public static final String DB_ALL_CAPTAINS = "DB_ALL_CAPTAINS";
    public static final String DB_ALL_PILOTS = "DB_ALL_PILOTS";
    public static final String DB_TD_SUMMARY_BY_TOURNAMENT = "DB_TD_SUMMARY_BY_TOURNAMENT";
    public static final String DB_RECORD_NAME_IN_USE = "DB_RECORD_NAME_IN_USE";
    public static final String DB_CHECK_NAME_IN_USE_REPORTS = "DB_CHECK_NAME_IN_USE_REPORTS";
    public static final String DB_TEAMS_BY_PILOT = "DB_TEAMS_BY_PILOT";
    public static final String DB_PILOTS_AND_CAPTAIN_BY_TEAM = "DB_PILOTS_AND_CAPTAIN_BY_TEAM";
    public static final String DB_MAYBE_ALLOCATE_TD_ACCOUNT = "DB_MAYBE_ALLOCATE_TD_ACCOUNT";
    public static final String DB_SELECT_TD_BY_PILOT = "DB_SELECT_TD_BY_PILOT";
    public static final String DB_RECORD_REFTOOL_INPUTS = "DB_RECORD_REFTOOL_INPUTS";
    public static final String DB_CLEAR_PROBLEMS = "DB_CLEAR_PROBLEMS";
    public static final String DB_ADD_PROBLEM = "DB_ADD_PROBLEM";
    public static final String DB_PILOT_NAMES_IN_USE = "DB_PILOT_NAMES_IN_USE";
    public static final String DB_TEAMS_FOR_PILOT_LIST = "DB_TEAMS_FOR_PILOT_LIST";
    public static final String DB_WRITE_LOGIN = "DB_WRITE_LOGIN";
    public static final String DB_FETCH_REFRESH_TOKEN = "DB_FETCH_REFRESH_TOKEN";
    public static final String DB_TOGGLE_RESOLVED = "DB_TOGGLE_RESOLVED";
    public static final String DB_TEAM_UUID_FOR_NAME = "DB_TEAM_UUID_FOR_NAME";
    public static final String DB_CREATE_MATCH = "DB_CREATE_MATCH";
    public static final String DB_MATCHES_FOR_TEAM = "DB_MATCHES_FOR_TEAM";
    public static final String DB_LATEST_MATCH = "DB_LATEST_MATCH";
    public static final String DB_ALL_MATCHES = "DB_ALL_MATCHES";
    public static final String DB_MATCH_BY_ID = "DB_MATCH_BY_ID";
    public static final String DB_RECORD_OF_SHIP = "DB_RECORD_OF_SHIP";
    public static final String DB_FETCH_STREAMER_TOKEN = "DB_FETCH_STREAMER_TOKEN";
    public static final String DB_STREAMER_BY_CODE = "DB_STREAMER_BY_CODE";
    public static final String DB_RECORD_MATCH_RESULT = "DB_RECORD_MATCH_RESULT";
    public static final String DB_WINS_BY_TEAM = "DB_WINS_BY_TEAM";

    private final EventBus eventBus;

    public DbClient(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public <T> Future<Message<Object>> callDb(String call, T params) {
        return Future.future(promise -> eventBus.request(call, params, promise));
    }
}
