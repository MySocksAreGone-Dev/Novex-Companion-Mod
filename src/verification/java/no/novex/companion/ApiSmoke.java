package no.novex.companion;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
public final class ApiSmoke {
    public static void main(String[] args) throws Exception {
        try (var api=SupabaseApi.configured()) {
            System.out.println("Public partners: " + api.partners().get(20,TimeUnit.SECONDS).size());
            try {
                api.attachSession("invalid-test-token-not-a-real-credential",Instant.now().plusSeconds(60)).get(20,TimeUnit.SECONDS);
                throw new AssertionError("Invalid credential accepted");
            } catch (java.util.concurrent.ExecutionException expected) {
                if (api.signedIn()) throw new AssertionError("Unexpected authenticated state");
                System.out.println("Invalid session rejected; no private identity established.");
            }
        }
    }
}
