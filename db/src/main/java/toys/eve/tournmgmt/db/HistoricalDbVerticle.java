package toys.eve.tournmgmt.db;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLClient;
import toys.eve.tournmgmt.db.HistoricalClient.Call;

import java.util.Objects;
import java.util.stream.Collectors;

public class HistoricalDbVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(HistoricalDbVerticle.class.getName());
    private static final String URL = System.getenv("HISTORICAL_URL");
    private static final String USER = System.getenv("HISTORICAL_USER");
    private static final String PASSWORD = System.getenv("HISTORICAL_PASSWORD");
    private SQLClient sqlClient;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        Objects.requireNonNull(URL, "Please supply HISTORICAL_URL");
        Objects.requireNonNull(USER, "Please supply HISTORICAL_USER");
        Objects.requireNonNull(PASSWORD, "Please supply HISTORICAL_PASSWORD");

        JsonObject config = new JsonObject()
                .put("driver_class", "org.mariadb.jdbc.Driver")
                .put("url", URL)
                .put("user", USER)
                .put("password", PASSWORD);
        sqlClient = JDBCClient.createShared(vertx, config, "historical");

        vertx.eventBus().consumer(Call.HISTORICAL_FETCH_MATCHES_BY_TEAM.name(), this::fetchMatchesByTeam);
        vertx.eventBus().consumer(Call.HISTORICAL_WIN_LOSS_BY_TOURNAMENT_AND_SHIP.name(), this::winlossByTournamentAndShip);
        vertx.eventBus().consumer(Call.HISTORICAL_WINS_BY_TOURNAMENT_AND_TEAM.name(), this::winsByTournamentAndTeam);
        vertx.eventBus().consumer(Call.HISTORICAL_WINS_BY_TOURNAMENT_AND_PLAYER.name(), this::winsByTournamentAndPlayer);
        vertx.eventBus().consumer(Call.HISTORICAL_PILOT_WIN_LOSS.name(), this::pilotWinLoss);

        startPromise.complete();
    }

    private void pilotWinLoss(Message<JsonArray> msg) {
        String pilots = msg.body().stream()
                .map(o -> (String) o)
                .map(o -> "\"" + o + "\"")
                .collect(Collectors.joining(","));
        sqlClient.query("select m.Tournament, Player, case when p.Team = m.Victor then 'W' else 'L' end as winloss, count(*) as count\n" +
                        "from players p\n" +
                        "         inner join matches m on p.Tournament = m.Tournament\n" +
                        "    and p.MatchNo = m.MatchNo\n" +
                        "    and p.SeriesNo = m.SeriesNo\n" +
                        "where Player in (" + pilots + ")\n" +
                        "group by m.Tournament, Player, winloss",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(new JsonArray(ar.result().getRows()));
                    }
                });
    }

    private void winsByTournamentAndTeam(Message msg) {
        sqlClient.query("select t.Tournament,\n" +
                        "       Team,\n" +
                        "       case when t.Team = m.Victor then 'W' else 'L' end as winloss,\n" +
                        "       count(*) as count\n" +
                        "from teams t\n" +
                        "         inner join\n" +
                        "     matches m on t.Tournament = m.Tournament\n" +
                        "         and t.MatchNo = m.MatchNo\n" +
                        "         and t.SeriesNo = m.SeriesNo\n" +
                        "group by Tournament, Team, winloss\n" +
                        "order by Tournament, count desc;",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(new JsonArray(ar.result().getRows()));
                    }
                });
    }

    private void winsByTournamentAndPlayer(Message msg) {
        sqlClient.query("select m.Tournament,\n" +
                        "       p.Player,\n" +
                        "       case when p.Team = m.Victor then 'W' else 'L' end as winloss,\n" +
                        "       count(*)                                          as count\n" +
                        "from matches m\n" +
                        "         inner join\n" +
                        "     players p on m.Tournament = p.Tournament and\n" +
                        "                  m.MatchNo = p.MatchNo and\n" +
                        "                  m.SeriesNo = p.SeriesNo\n" +
                        "where p.Player in (\n" +
                        "                   'Aimsucks',\n" +
                        "                   'Mira Chieve',\n" +
                        "                   'Vlad Starlord',\n" +
                        "                   'Kane Carnifex',\n" +
                        "                   'StarFleetCommander',\n" +
                        "                   'Sp3ctr380',\n" +
                        "                   'Tyrion Hekki',\n" +
                        "                   'xXNemXx',\n" +
                        "                   'Hansy Babes',\n" +
                        "                   'Ithugor Wells',\n" +
                        "                   'Soldarius',\n" +
                        "                   'Stowesh',\n" +
                        "                   'michael Rinah',\n" +
                        "                   'Alasker',\n" +
                        "                   'Cyclo Hexanol',\n" +
                        "                   'Eargonall Kaundur',\n" +
                        "                   'dexter xio',\n" +
                        "                   'Nika NOisER',\n" +
                        "                   'DrHorrible',\n" +
                        "                   'Reire Murasame',\n" +
                        "                   'Kentril Ul-Diomed',\n" +
                        "                   'NEWBEEGOGOGO',\n" +
                        "                   'Rixx Javix',\n" +
                        "                   'Levi Nineveh',\n" +
                        "                   'Plejaden',\n" +
                        "                   'Melinda I',\n" +
                        "                   'Davak Kateelo',\n" +
                        "                   'Yukiko Kami',\n" +
                        "                   'Xoorauch Destroyer',\n" +
                        "                   'TheLastSparton',\n" +
                        "                   'PiLINchi',\n" +
                        "                   'Dirk Stetille'\n" +
                        "    )\n" +
                        "group by Tournament, p.Player, winloss\n" +
                        "order by Tournament, count desc\n",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(new JsonArray(ar.result().getRows()));
                    }
                });
    }

    private void fetchMatchesByTeam(Message<String> msg) {
        String teamName = msg.body();
        sqlClient.queryWithParams("select * " +
                                  "from matches " +
                                  "where RedTeam = ? " +
                                  "   or BlueTeam = ? " +
                                  "order by Tournament, MatchNo, SeriesNo",
                new JsonArray().add(teamName).add(teamName),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(new JsonArray(ar.result().getRows()));
                    }
                });
    }

    private void winlossByTournamentAndShip(Message<JsonObject> msg) {
        String whereClause = msg.body().getString("whereClause");
        sqlClient.query("select Tournament,\n" +
                        "       Ship,\n" +
                        "       Class,\n" +
                        "       SuperClass,\n" +
                        "       winloss,\n" +
                        "       sum(count) as fielded,\n" +
                        "       sum(used)  as used\n" +
                        "from (\n" +
                        "         select m.Tournament,\n" +
                        "                p.Team,\n" +
                        "                p.Ship,\n" +
                        "                s.Class,\n" +
                        "                sc.SuperClass,\n" +
                        "                case when p.Team = m.Victor then 'W' else 'L' end as winloss,\n" +
                        "                count(*)                                          as count,\n" +
                        "                least(count(*), 1)                                as used\n" +
                        "         from players p\n" +
                        "                  inner join matches m\n" +
                        "                             on p.Tournament = m.Tournament and\n" +
                        "                                p.MatchNo = m.MatchNo and\n" +
                        "                                p.SeriesNo = m.SeriesNo\n" +
                        "                  inner join ships s on p.Ship = s.Ship\n" +
                        "                  inner join superclasses sc on s.Class = sc.Class\n" +
                        (whereClause == null ? "" : whereClause) +
                        "         group by m.Tournament,\n" +
                        "                  m.MatchNo,\n" +
                        "                  m.SeriesNo,\n" +
                        "                  p.Team,\n" +
                        "                  p.Ship,\n" +
                        "                  s.Class,\n" +
                        "                  winloss\n" +
                        "     ) as source\n" +
                        "group by Tournament, Ship, Class, winloss\n",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(new JsonArray(ar.result().getRows()));
                    }
                });
    }

}
