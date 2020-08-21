package toys.eve.tournmgmt.deploy;

import eve.toys.tournmgmt.web.WebVerticle;
import io.vertx.core.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import toys.eve.tournmgmt.common.vertx.GlobalOptions;
import toys.eve.tournmgmt.db.DbVerticle;

import java.util.ArrayList;
import java.util.List;

public class SingleProcessMain {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleProcessMain.class.getName());

    public static void main(String[] args) {
        LOGGER.info("http.agent=" + System.getProperty("http.agent"));
        VertxOptions vertxOptions = GlobalOptions.vertxOptions(9092);
        Vertx vertx = Vertx.vertx(vertxOptions);
        GlobalOptions.globalInit();
        deploy(vertx);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("shut 'er down");
            vertx.close();
        }));
    }

    private static void deploy(Vertx vertx) {

        List<Future> dbs = new ArrayList<>();
        dbs.add(deployHelper(vertx, DbVerticle.class, new DeploymentOptions().setWorker(true)));

        CompositeFuture.all(dbs)
                .onSuccess(f -> {
                    LOGGER.info("DB(s) deployed successfully");
                    List<Future> webApps = new ArrayList<>();
                    webApps.add(deployHelper(vertx, WebVerticle.class, new DeploymentOptions()));
                    CompositeFuture.all(webApps)
                            .onSuccess(f2 -> {
                                LOGGER.info("Apps deployed successfully");
                            })
                            .onFailure(t -> {
                                LOGGER.error("webapp deployment failed");
                                t.printStackTrace();
                                vertx.close();
                            });
                })
                .onFailure(t -> {
                    LOGGER.error("db deployment failed");
                    t.printStackTrace();
                    vertx.close();
                });
    }

    private static Future<String> deployHelper(Vertx vertx, Class<? extends Verticle> v, DeploymentOptions options) {
        Promise<String> p = Promise.promise();
        vertx.deployVerticle(v, options, ar -> {
            if (ar.succeeded()) {
                p.complete(ar.result());
            } else {
                p.fail(ar.cause());
            }
        });
        return p.future();
    }

}
