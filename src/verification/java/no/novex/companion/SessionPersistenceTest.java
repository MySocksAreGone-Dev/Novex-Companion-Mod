package no.novex.companion;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
final class SessionPersistenceTest {
 static final class Vault extends SessionVault {
  String saved;
  Vault(){super(Path.of("/tmp/novex-test-unused"),"test");}
  @Override CompletableFuture<String> load(){return CompletableFuture.completedFuture(saved);}
  @Override CompletableFuture<Void> save(String value){saved=value;return CompletableFuture.completedFuture(null);}
  @Override CompletableFuture<Void> clear(){saved=null;return CompletableFuture.completedFuture(null);}
 }
 static void run(){
  var vault=new Vault();var http=new SessionTest.Stub();
  var api=new SupabaseApi("https://example.com","sb_publishable_test",http);
  api.restore(vault).join();http.reply=SessionTest.session(3600);api.signIn("test@example.invalid","test-password").join();
  ApiTest.check(vault.saved!=null);api.close();ApiTest.check(vault.saved!=null);
  var nextHttp=new SessionTest.Stub();nextHttp.reply=SessionTest.session(3600).replace("test-refresh-only","rotated-refresh");
  try(var next=new SupabaseApi("https://example.com","sb_publishable_test",nextHttp)){
   next.restore(vault).join();ApiTest.check(next.signedIn());ApiTest.check(vault.saved.equals("rotated-refresh"));
   ApiTest.check(nextHttp.paths.contains("/auth/v1/token?grant_type=refresh_token"));
   next.signOut().join();ApiTest.check(vault.saved==null);ApiTest.check(!next.signedIn());
  }
  vault.saved="expired-refresh";var rejected=new SessionTest.Stub();rejected.status=400;
  try(var bad=new SupabaseApi("https://example.com","sb_publishable_test",rejected)){
   ApiTest.check(bad.restore(vault).isCompletedExceptionally());ApiTest.check(!bad.signedIn());ApiTest.check(vault.saved==null);
  }
  vault.saved="offline-refresh";var offline=new SessionTest.Stub();offline.status=503;
  try(var bad=new SupabaseApi("https://example.com","sb_publishable_test",offline)){
   ApiTest.check(bad.restore(vault).isCompletedExceptionally());ApiTest.check(vault.saved.equals("offline-refresh"));
  }
  System.out.println("Persistent-session checks passed: restart, rotation, logout, expiry and offline preservation.");
 }
}
