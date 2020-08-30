package eve.toys.tournmgmt.web.esi;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.AccessToken;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class Esi {

    public static final String ESI_BASE = "https://esi.evetech.net/latest";

    public static Future<JsonObject> lookupALliance(AccessToken token, String alliance) {
        return Future.future(promise ->
                token.fetch(ESI_BASE + "/search/?categories=alliance&strict=true&search=" + encode(alliance),
                        ar -> {
                            if (ar.failed()) {
                                promise.fail(ar.cause());
                                return;
                            }
                            promise.complete(new JsonObject()
                                    .put("name", alliance)
                                    .put("result", ar.result().body().toJsonObject().getJsonArray("alliance")));
                        }));
    }

    private static String encode(String alliance) {
        try {
            return URLEncoder.encode(alliance, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return alliance;
        }
    }

    public static Future<JsonObject> checkCharacter(AccessToken token, String character) {
        return Future.future(promise ->
                token.fetch(ESI_BASE + "/search/?categories=character&strict=true&search=" + encode(character),
                        ar -> {
                            if (ar.failed()) {
                                promise.fail(ar.cause());
                                return;
                            }
                            promise.complete(new JsonObject()
                                    .put("name", character)
                                    .put("result", ar.result().body().toJsonObject().getJsonArray("character")));
                        }));
    }

    public static Future<JsonObject> checkMembership(AccessToken token,
                                                     Future<JsonObject> checkAlliance,
                                                     Future<JsonObject> checkCharacter) {
        return Future.future(promise ->
                CompositeFuture.all(checkAlliance, checkCharacter)
                        .onSuccess(f -> {
                            JsonObject alliance = f.resultAt(0);
                            JsonObject character = f.resultAt(1);

                            JsonObject out = new JsonObject();
                            out.put("alliance", alliance).put("character", character);
                            if (alliance.getJsonArray("result") != null && character.getJsonArray("result") != null) {
                                token.fetch(ESI_BASE + "/characters/" + character.getJsonArray("result").getInteger(0),
                                        ar -> {
                                            if (ar.failed()) {
                                                promise.fail(ar.cause());
                                                return;
                                            }
                                            Integer allianceId = ar.result().body().toJsonObject().getInteger("alliance_id");
                                            out.put("membership", allianceId);
                                            promise.complete(out);
                                        });
                            } else {
                                promise.complete(out);
                            }
                        })
                        .onFailure(Throwable::printStackTrace)
        );
    }
}
