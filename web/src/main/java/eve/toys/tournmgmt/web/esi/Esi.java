package eve.toys.tournmgmt.web.esi;

import eve.toys.tournmgmt.web.authn.AppRBAC;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.web.client.WebClient;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class Esi {

    public static final String ESI_BASE = "https://esi.evetech.net/latest";

    private Esi() {
    }

    public static Esi create() {
        return new Esi();
    }

    public Future<JsonObject> lookupShip(WebClient webClient, String shipName) {
        return lookupByType(webClient, shipName, "inventory_type");
    }

    private static Future<JsonObject> lookupByType(WebClient webClient, String name, String category) {
        return Future.future(promise -> {
            String url = "/search/?categories=" + category + "&strict=true&search=" + encode(name);
            webClient.getAbs(ESI_BASE + url)
                    .send(ar -> {
                        if (ar.failed()) {
                            promise.fail(ar.cause());
                            return;
                        }
                        if (ar.result().statusCode() != 200) {
                            promise.fail(ar.result().statusCode() + ": " + ar.result().statusMessage()
                                    + "\n" + url);
                            return;
                        }
                        JsonObject json = ar.result().body().toJsonObject();
                        promise.complete(new JsonObject()
                                .put("category", category)
                                .put(category, name)
                                .put("result", json.getJsonArray(category)));
                    });
        });
    }

    private static String encode(String string) {
        try {
            return URLEncoder.encode(string, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return string;
        }
    }

    public Future<JsonObject> fetchType(WebClient webClient, Integer typeId) {
        return Future.future(promise -> {
            String url = "/universe/types/" + typeId;
            webClient.getAbs(ESI_BASE + url)
                    .send(ar -> {
                        if (ar.failed()) {
                            promise.fail(ar.cause());
                            return;
                        }
                        if (ar.result().statusCode() != 200) {
                            promise.fail(ar.result().statusCode() + ": " + ar.result().statusMessage()
                                    + "\n" + url);
                            return;
                        }
                        JsonObject json = ar.result().body().toJsonObject();
                        promise.complete(new JsonObject()
                                .put("result", json));
                    });
        });
    }

    public Future<JsonObject> getSkills(AccessToken user, int characterId) {
        return Future.future(promise -> {
            AppRBAC.refreshIfNeeded(user, v -> {
                String url = "/characters/" + characterId + "/skills/";
                user.fetch(ESI_BASE + url, ar -> {
                    if (ar.failed()) {
                        promise.fail(ar.cause());
                        return;
                    }
                    if (ar.result().statusCode() != 200) {
                        promise.fail(ar.result().statusCode()
                                + "\n" + url);
                        return;
                    }
                    promise.complete(ar.result().jsonObject());
                });

            });
        });
    }

    public Future<JsonObject> checkMembership(WebClient webClient,
                                              String uuid,
                                              Future<JsonObject> checkAlliance,
                                              Future<JsonObject> checkCharacter) {
        return Future.future(promise ->
                CompositeFuture.all(checkAlliance, checkCharacter)
                        .onSuccess(f -> {
                            JsonObject alliance = f.resultAt(0);
                            JsonObject character = f.resultAt(1);

                            JsonObject out = new JsonObject()
                                    .put("uuid", uuid)
                                    .put("expectedAlliance", alliance)
                                    .put("character", character);
                            if (alliance.getJsonArray("result") != null && character.getJsonArray("result") != null) {
                                String url = "/characters/" + character.getJsonArray("result").getInteger(0);
                                webClient.getAbs(ESI_BASE + url)
                                        .send(ar -> {
                                            if (ar.failed()) {
                                                promise.fail(ar.cause());
                                                return;
                                            }
                                            if (ar.result().statusCode() != 200) {
                                                promise.fail(ar.result().statusCode() + ": " + ar.result().statusMessage()
                                                        + "\n" + url);
                                                return;
                                            }
                                            Integer allianceId = ar.result().body().toJsonObject().getInteger("alliance_id");
                                            out.put("actualAlliance", allianceId);
                                            promise.complete(out);
                                        });
                            } else {
                                promise.complete(out);
                            }
                        })
                        .onFailure(Throwable::printStackTrace)
        );
    }

    public Future<JsonObject> lookupCharacter(WebClient webClient, String character) {
        return lookupByType(webClient, character, "character");
    }

    public Future<JsonObject> lookupAlliance(WebClient webClient, String alliance) {
        return lookupByType(webClient, alliance, "alliance");
    }

}
