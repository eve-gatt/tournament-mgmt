package toys.eve.tournmgmt.deploy;

import eve.toys.tournmgmt.web.WebVerticle;
import io.vertx.core.*;
import toys.eve.tournmgmt.common.vertx.GlobalOptions;
import toys.eve.tournmgmt.db.DbVerticle;

import java.util.ArrayList;
import java.util.List;

public class SingleProcessMain {
    public static void main(String[] args) {
        System.out.println("http.agent=" + System.getProperty("http.agent"));
        VertxOptions vertxOptions = GlobalOptions.vertxOptions(9092);
        Vertx vertx = Vertx.vertx(vertxOptions);
        GlobalOptions.globalInit();
        deploy(vertx);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("shut 'er down");
            vertx.close();
        }));
    }

    private static void deploy(Vertx vertx) {

        List<Future> dbs = new ArrayList<>();
        dbs.add(deployHelper(vertx, DbVerticle.class, new DeploymentOptions().setWorker(true)));

        CompositeFuture.all(dbs)
                .onSuccess(f -> {
                    System.out.println("DB(s) deployed successfully");
                    List<Future> webApps = new ArrayList<>();
                    webApps.add(deployHelper(vertx, WebVerticle.class, new DeploymentOptions()));
                    CompositeFuture.all(webApps)
                            .onSuccess(f2 -> {
                                System.out.println("Apps deployed successfully");
                            })
                            .onFailure(t -> {
                                System.err.println("webapp deployment failed");
                                t.printStackTrace();
                            });
                })
                .onFailure(t -> {
                    System.err.println("db deployment failed");
                    t.printStackTrace();
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
