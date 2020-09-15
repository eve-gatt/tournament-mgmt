package eve.toys.tournmgmt.web.esi;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class ETagCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(ETagCache.class.getName());

    private final WebClient esi;
    private final CircuitBreaker cb;

    private Map<String, ETagAndResult> etags = new HashMap<>();
    private int etagCacheHit;
    private int cacheMiss;
    private int cacheTotal;
    private int expiryCacheHit;

    public ETagCache(WebClient esi, CircuitBreaker cb) {
        this.esi = esi;
        this.cb = cb;
    }

    public Future<HttpResponse<Buffer>> callApi(String url) {

        Promise<HttpResponse<Buffer>> promise = Promise.promise();

        cacheTotal++;

        ETagAndResult cachedResult = etags.getOrDefault(url, ETagAndResult.NONE);

        HttpRequest<Buffer> rq = esi.getAbs(url);
        if (cachedResult != ETagAndResult.NONE) {
            if (ZonedDateTime.now().isBefore(DateTimeFormatter.RFC_1123_DATE_TIME.parse(cachedResult.expiry, ZonedDateTime::from))) {
                expiryCacheHit++;
                promise.complete(cachedResult.response);
                return promise.future();
            } else {
                rq.putHeader("If-None-Match", cachedResult.eTag);
            }
        }
        cb.executeAndReport(promise, p -> {

            if (cacheTotal % 100 == 1) {
                LOGGER.info(System.out.printf("ExpiryHit:ETagHit:Miss:Total:CacheSize - %d:%d:%d:%d:%d%n", expiryCacheHit, etagCacheHit, cacheMiss, cacheTotal, etags.size()));
            }

            rq.send(res -> {
                if (res.failed()) {
                    p.fail(res.cause());
                    return;
                }

                if (res.result().statusCode() >= 500 && res.result().statusCode() < 600) {
                    p.fail(res.result().statusCode() + " " + res.result().statusMessage());
                    return;
                }

                String expiry = res.result().getHeader("expires");
                if (res.result().statusCode() == 304) {
                    etagCacheHit++;
                    cachedResult.updateExpiry(expiry);
                    p.complete(cachedResult.response);
                } else {
                    cacheMiss++;
                    String etag = res.result().getHeader("Etag");
                    if (etag != null && etag.length() > 3) {
                        etags.put(url, new ETagAndResult(etag, res.result(), expiry));
                    }
                    p.complete(res.result());
                }

            });
        });

        return promise.future();
    }

    private static class ETagAndResult {
        private static final ETagAndResult NONE = new ETagAndResult("none", null, null);
        private final String eTag;
        private final HttpResponse<Buffer> response;
        private String expiry;

        public ETagAndResult(String eTag, HttpResponse<Buffer> response, String expiry) {
            this.eTag = eTag;
            this.response = response;
            this.expiry = expiry;
        }

        public void updateExpiry(String expiry) {
            this.expiry = expiry;
        }
    }
}
