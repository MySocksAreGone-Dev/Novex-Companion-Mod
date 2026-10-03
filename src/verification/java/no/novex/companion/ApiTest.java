package no.novex.companion;
import com.google.gson.JsonParser;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;
public final class ApiTest {
    static void check(boolean value) { if (!value) throw new AssertionError("API verification failed"); }
    static void run() {
        SessionTest.run();
        for (String url : List.of("http://example.com","https://user@example.com","https://example.com/private")) {
            try { new SupabaseApi(url,"sb_publishable_test");throw new AssertionError("Unsafe origin accepted"); }
            catch (IllegalArgumentException expected) {}
        }
        try { new SupabaseApi("https://example.com","sb_secret_private");throw new AssertionError("Secret accepted"); }
        catch (IllegalArgumentException expected) {}
        try (var api=new SupabaseApi("https://example.com","sb_publishable_test")) {
            check(!api.signedIn());check(api.friends().isCompletedExceptionally());
            check(api.incomingRequests().isCompletedExceptionally());
            check(api.sendFriendRequest(java.util.UUID.randomUUID()).isCompletedExceptionally());
            check(api.acceptFriendRequest(java.util.UUID.randomUUID()).isCompletedExceptionally());
            check(api.attachSession("invalid",java.time.Instant.now()).isCompletedExceptionally());
        }
        check(SupabaseApi.validAddress("mc.example.org:25565"));
        check(!SupabaseApi.validAddress("example.com:65536"));
        check(!SupabaseApi.validAddress("example.com:0"));
        check(!SupabaseApi.validAddress("https://example.com"));
        var rows=SupabaseApi.parsePartners(JsonParser.parseString("[{\"title\":\"Server\",\"description\":\"Hello\",\"server_address\":\"mc.example.org\"},{\"title\":\"Invalid\",\"description\":\"x\",\"server_address\":\"bad:99999\"}]"));
        check(rows.size()==1);
        var body=new SupabaseApi.LimitedBody();boolean[] cancelled={false};
        body.onSubscribe(new Flow.Subscription() { public void request(long n) {} public void cancel() { cancelled[0]=true; } });
        body.onNext(List.of(ByteBuffer.allocate(262145)));
        check(cancelled[0] && body.getBody().toCompletableFuture().isCompletedExceptionally());
        System.out.println("API checks passed: origin/key validation, auth gating, addresses, bounded responses.");
    }
}
