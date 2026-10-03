package no.novex.companion;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
final class SessionTest {
 static void run() {
  var http=new Stub();
  try(var api=new SupabaseApi("https://example.com","sb_publishable_test",http)) {
   ApiTest.check(api.signIn("","").isCompletedExceptionally());
   http.reply=session(1);api.signIn("test@example.invalid","temporary test password").join();
   ApiTest.check(api.signedIn());
   http.reply=session(3600);api.rpc("companion_unread",new com.google.gson.JsonObject()).join();
   ApiTest.check(http.paths.contains("/auth/v1/token?grant_type=refresh_token"));
   ApiTest.check(api.rpc("companion_record_verified_link",new com.google.gson.JsonObject()).isCompletedExceptionally());
   api.disconnect();ApiTest.check(!api.signedIn());
   http.reply=session(1);api.signIn("test@example.invalid","temporary test password").join();
   http.status=400;ApiTest.check(api.rpc("companion_unread",new com.google.gson.JsonObject()).isCompletedExceptionally());
   ApiTest.check(!api.signedIn());http.status=200;
   http.pending=new CompletableFuture<>();var login=api.signIn("test@example.invalid","temporary test password");
   api.disconnect();http.pending.complete(response(http.last,session(3600)));login.join();ApiTest.check(!api.signedIn());
  }
  System.out.println("Session checks passed: login, refresh, logout race, privileged RPC rejection.");
 }
 static String session(int seconds) {return "{\"access_token\":\"test-access-only-not-a-real-token\",\"refresh_token\":\"test-refresh-only\",\"expires_in\":"+seconds+",\"user\":{\"id\":\"10000000-0000-4000-8000-000000000001\"}}";}
 static HttpResponse<byte[]> response(HttpRequest request,String text) {return response(request,text,200);}
 static HttpResponse<byte[]> response(HttpRequest request,String text,int status) {
  return new HttpResponse<>() {
   public int statusCode(){return status;}public HttpRequest request(){return request;}
   public Optional<HttpResponse<byte[]>> previousResponse(){return Optional.empty();}
   public HttpHeaders headers(){return HttpHeaders.of(Map.of(),(a,b)->true);}
   public byte[] body(){return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);}
   public Optional<SSLSession> sslSession(){return Optional.empty();}public URI uri(){return request.uri();}public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}
  };
 }
 static final class Stub extends HttpClient {
  int status=200;String reply="[]";List<String> paths=new ArrayList<>();HttpRequest last;CompletableFuture<HttpResponse<byte[]>> pending;
  public Optional<CookieHandler> cookieHandler(){return Optional.empty();}public Optional<Duration> connectTimeout(){return Optional.empty();}
  public Redirect followRedirects(){return Redirect.NEVER;}public Optional<ProxySelector> proxy(){return Optional.empty();}
  public SSLContext sslContext(){throw new UnsupportedOperationException();}public SSLParameters sslParameters(){return new SSLParameters();}
  public Optional<Authenticator> authenticator(){return Optional.empty();}public Version version(){return Version.HTTP_1_1;}
  public Optional<Executor> executor(){return Optional.empty();}
  public <T> HttpResponse<T> send(HttpRequest r,HttpResponse.BodyHandler<T> h){throw new UnsupportedOperationException();}
  @SuppressWarnings("unchecked") public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h){
   last=r;paths.add(r.uri().getPath()+(r.uri().getQuery()==null?"":"?"+r.uri().getQuery()));
   if(pending!=null)return (CompletableFuture)pending;
   return CompletableFuture.completedFuture((HttpResponse<T>)response(r,r.uri().getPath().equals("/auth/v1/token")?reply:"[]",status));
  }
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h,HttpResponse.PushPromiseHandler<T> p){return sendAsync(r,h);}
  @Override public void shutdownNow() {}
 }
}
