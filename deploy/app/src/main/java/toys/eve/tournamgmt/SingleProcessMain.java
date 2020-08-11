package toys.eve.tournamgmt;

import eve.toys.tournmgmt.web.WebVerticle;
import io.vertx.core.*;
import toys.eve.tournmgmt.common.vertx.GlobalOptions;

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
//        dbs.add(deployHelper(vertx, 5v5db.class, new DeploymentOptions().setWorker(true)));

        CompositeFuture.all(dbs)
                .onSuccess(f -> {
                    List<Future> webApps = new ArrayList<>();
                    webApps.add(deployHelper(vertx, WebVerticle.class, new DeploymentOptions()));
                    CompositeFuture.all(webApps)
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
