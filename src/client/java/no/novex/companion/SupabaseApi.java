package no.novex.companion;

import com.google.gson.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Existing Novex REST/RPC schema. Access tokens stay in memory; refresh tokens may use the OS credential vault. */
public final class SupabaseApi implements AutoCloseable {
    private final URI origin;
    private final String publicKey;
    private final HttpClient http;
    private final AtomicInteger generation = new AtomicInteger();
    private volatile Session session;
    private volatile boolean closed;
    private CompletableFuture<Session> refreshing;
    private SessionVault vault;
    private volatile boolean restoring;
    public boolean restoring(){return restoring;}
    String serviceOrigin(){return origin.toString();}
    public String storageStatus(){return vault==null?"Session stays in memory.":vault.status();}
    public synchronized CompletableFuture<Void> restore(SessionVault store) {
        vault=store;restoring=true;int attempt=generation.get();
        return store.load().thenCompose(refresh->{
            if(refresh==null||closed||generation.get()!=attempt)return CompletableFuture.<JsonElement>completedFuture(null);
            var body=new JsonObject();body.addProperty("refresh_token",refresh);
            return request("/auth/v1/token?grant_type=refresh_token",null,body);
        }).thenAccept(data->{if(data!=null)publish(parseSession(data),attempt);}).whenComplete((value,error)->{
            restoring=false;Throwable cause=error;while(cause!=null&&cause.getCause()!=null)cause=cause.getCause();
            if(cause instanceof ApiException failure&&(failure.status==400||failure.status==401)&&generation.get()==attempt)disconnect();
        });
    }
    private synchronized void publish(Session next,int attempt){
        if(!closed&&generation.get()==attempt){session=next;if(vault!=null&&next.refresh!=null)vault.save(next.refresh);}
    }
    private static final class ApiException extends IllegalStateException {
        final int status;
        ApiException(int status,String message){super(message);this.status=status;}
    }
    private volatile WebSocket socket;
    private boolean connecting;
    private long nextSocket;
    private final AtomicInteger socketRef=new AtomicInteger();
    private static final class Session {
        final UUID user; final String token; final Instant expires; final String refresh;
        Session(UUID user, String token, Instant expires) { this(user,token,expires,null); }
        Session(UUID user,String token,Instant expires,String refresh) { this.user=user; this.token=token; this.expires=expires; this.refresh=refresh; }
        @Override public String toString() { return "Novex session [redacted]"; }
    }
    public record Partner(String name, String description, String address) {}
    public SupabaseApi(String url, String key) { this(url,key,null); }
    SupabaseApi(String url,String key,HttpClient transport) {
        origin=URI.create(url);
        if (!"https".equals(origin.getScheme()) || origin.getHost()==null || origin.getUserInfo()!=null
            || origin.getQuery()!=null || origin.getFragment()!=null || !origin.getPath().isEmpty()
            || origin.getPort()!=-1) throw new IllegalArgumentException("Use an HTTPS Supabase origin.");
        boolean publishable=key.startsWith("sb_publishable_");
        try {
            if (!publishable) publishable=JsonParser.parseString(new String(Base64.getUrlDecoder().decode(key.split("\\.")[1]), StandardCharsets.UTF_8))
                .getAsJsonObject().get("role").getAsString().equals("anon");
        } catch (RuntimeException ignored) { publishable=false; }
        if (!publishable || key.length()>4096 || !key.matches("[A-Za-z0-9._-]+"))
            throw new IllegalArgumentException("Only public publishable/anon configuration is allowed.");
        publicKey=key;
        http=transport!=null?transport:HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public static SupabaseApi configured() {
        try (var in=SupabaseApi.class.getResourceAsStream("/novex-services.json")) {
            if (in==null) throw new IllegalStateException();
            var config=JsonParser.parseString(new String(in.readNBytes(8192), StandardCharsets.UTF_8)).getAsJsonObject();
            return new SupabaseApi(config.get("url").getAsString(),config.get("publishableKey").getAsString());
        } catch (Exception ignored) { throw new IllegalStateException("Novex public services are not configured."); }
    }
    /** Uses the same email/password account as Novex Client. Passwords are never persisted; refresh tokens use the OS vault when available. */
    public CompletableFuture<Void> signIn(String email, String password) {
        if (email==null || email.isBlank() || email.length()>320 || password==null || password.isEmpty() || password.length()>4096)
            return failed("Enter your Novex email and password.");
        int attempt=beginSession();
        JsonObject body=new JsonObject();body.addProperty("email",email.trim());body.addProperty("password",password);
        return request("/auth/v1/token?grant_type=password",null,body).thenAccept(data -> {
            Session next=parseSession(data);
            publish(next,attempt);
        });
    }
    private static Session parseSession(JsonElement data) {
        try {
            var row=data.getAsJsonObject();
            String token=row.get("access_token").getAsString(), refresh=row.get("refresh_token").getAsString();
            long seconds=row.get("expires_in").getAsLong();
            if (token.length()>16384 || refresh.length()>16384 || seconds<1 || seconds>86400) throw new IllegalArgumentException();
            return new Session(UUID.fromString(row.getAsJsonObject("user").get("id").getAsString()),token,Instant.now().plusSeconds(seconds),refresh);
        } catch (RuntimeException ignored) { throw new CompletionException(new IllegalStateException("Invalid Novex sign-in response.")); }
    }
    private synchronized CompletableFuture<Session> authorized() {
        Session current=session;
        if (closed || current==null) return failed("Sign in to your Novex account first.");
        if (current.expires.isAfter(Instant.now().plusSeconds(60))) return CompletableFuture.completedFuture(current);
        if (current.refresh==null) { disconnect();return failed("Novex session expired. Sign in again."); }
        if (refreshing!=null && !refreshing.isDone()) return refreshing;
        int attempt=generation.get(); JsonObject body=new JsonObject();body.addProperty("refresh_token",current.refresh);
        CompletableFuture<Session> attemptFuture=request("/auth/v1/token?grant_type=refresh_token",null,body).thenApply(data -> {
            Session next=parseSession(data);
            if (closed || generation.get()!=attempt) throw new CompletionException(new IllegalStateException("Novex session ended."));
            publish(next,attempt);return next;
        });
        refreshing=attemptFuture;
        attemptFuture.whenComplete((value,error)->{
            Throwable cause=error;while(cause!=null&&cause.getCause()!=null)cause=cause.getCause();
            if(cause instanceof ApiException failure&&(failure.status==400||failure.status==401)&&generation.get()==attempt)disconnect();
        });
        return attemptFuture;
    }
    public UUID userId() { return session==null?null:session.user; }
    public CompletableFuture<Void> signOut() {
        Session current=session;disconnect();
        if (current==null) return CompletableFuture.completedFuture(null);
        return request("/auth/v1/logout?scope=local",current.token,new JsonObject()).thenApply(ignored -> null);
    }
    public CompletableFuture<JsonArray> searchUsers(String query) {
        if (query==null || query.trim().length()<2 || query.length()>64) return failed("Enter 2–64 characters.");
        String safe=query.trim().replaceAll("[%_*]", "");
        return social(s -> "/rest/v1/profiles?select=id,username&username=ilike."+encode("%"+safe+"%")+"&id=neq."+s.user+"&limit=20");
    }
    public CompletableFuture<JsonElement> rpc(String name,JsonObject body) {
        if (!Set.of("accept_friend_request","companion_decline_request","companion_remove_friend","companion_mark_read","companion_unread","companion_presence","companion_friends_presence","companion_unlink_minecraft").contains(name)) return failed("Unsupported Novex action.");
        return authorized().thenCompose(s -> request("/rest/v1/rpc/"+name,s.token,body));
    }
    public CompletableFuture<Void> sendMessage(UUID friend,String content) {
        if (content==null || content.isBlank() || content.length()>4000) return failed("Messages must contain 1–4000 characters.");
        return authorized().thenCompose(s -> {
            JsonObject body=new JsonObject();body.addProperty("sender_id",s.user.toString());body.addProperty("receiver_id",friend.toString());body.addProperty("content",content);
            return request("/rest/v1/messages",s.token,body).thenApply(ignored -> null);
        });
    }
    /** Called by a future secure launcher handoff, never from config/arguments or UI token input. */
    public CompletableFuture<Void> attachSession(String accessToken, Instant expiresAt) {
        if (accessToken==null || accessToken.length()<20 || accessToken.length()>16384 || !accessToken.matches("[A-Za-z0-9._-]+")
            || expiresAt==null || !expiresAt.isAfter(Instant.now()) || expiresAt.isAfter(Instant.now().plusSeconds(3600)))
            return failed("A short-lived Novex session is required.");
        int attempt=beginSession();
        return request("/auth/v1/user",accessToken).thenAccept(data -> {
            UUID user=UUID.fromString(data.getAsJsonObject().get("id").getAsString());
            if (!closed && generation.get()==attempt && expiresAt.isAfter(Instant.now())) session=new Session(user,accessToken,expiresAt);
        });
    }
    private synchronized int beginSession() {disconnect();return generation.get();}
    public synchronized void disconnect() { if(vault!=null)vault.clear(); resetSession(); }
    private synchronized void resetSession() { generation.incrementAndGet(); session=null; refreshing=null; if(socket!=null)socket.abort();socket=null; }
    public boolean signedIn() { return !closed && session!=null && (session.refresh!=null || session.expires.isAfter(Instant.now())); }
    /** Supabase protocol 1.0; received rows are never trusted as authorization or cached directly. */
    public synchronized void realtime(Runnable changed) {
        long now=System.currentTimeMillis();
        if(closed||session==null||now<nextSocket||connecting)return;
        nextSocket=now+25000;
        int captured=generation.get();
        if(socket!=null) {
            WebSocket active=socket;
            authorized().thenAccept(current -> {
                if(generation.get()!=captured)return;
                var auth=new JsonObject();auth.addProperty("access_token",current.token);
                sendSocket(active,"realtime:novex-companion","access_token",auth)
                    .thenCompose(v->sendSocket(active,"phoenix","heartbeat",new JsonObject())).exceptionally(error->{active.abort();if(socket==active)socket=null;return null;});
            }).exceptionally(error->null);return;
        }
        connecting=true;nextSocket=now+60000;
        authorized().thenCompose(current->http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8))
            .buildAsync(URI.create("wss://"+origin.getHost()+"/realtime/v1/websocket?apikey="+encode(publicKey)+"&vsn=1.0.0"),new WebSocket.Listener() {
                private final StringBuilder partial=new StringBuilder();
                @Override public void onOpen(WebSocket ws) {
                    if(closed||generation.get()!=captured){ws.abort();return;}
                    socket=ws;
                    var payload=new JsonObject();payload.addProperty("access_token",current.token);
                    var config=new JsonObject();var changes=new JsonArray();
                    for(String event:List.of("INSERT","UPDATE")) {
                        var change=new JsonObject();change.addProperty("event",event);change.addProperty("schema","public");
                        change.addProperty("table","companion_social_revision");change.addProperty("filter","user_id=eq."+current.user);changes.add(change);
                    }
                    config.add("postgres_changes",changes);payload.add("config",config);
                    sendSocket(ws,"realtime:novex-companion","phx_join",payload).exceptionally(error->{ws.abort();return null;});ws.request(1);
                }
                @Override public CompletionStage<?> onText(WebSocket ws,CharSequence text,boolean last) {
                    if(partial.length()+text.length()>262144){ws.abort();return null;}
                    partial.append(text);
                    if(last) {
                        try {
                            var message=JsonParser.parseString(partial.toString()).getAsJsonObject();
                            if(generation.get()==captured&&"postgres_changes".equals(message.get("event").getAsString()))changed.run();
                        }catch(RuntimeException ignored){}partial.setLength(0);
                    }
                    ws.request(1);return null;
                }
                @Override public CompletionStage<?> onClose(WebSocket ws,int code,String reason) {if(socket==ws)socket=null;return null;}
                @Override public void onError(WebSocket ws,Throwable error) {if(socket==ws)socket=null;}
            })).whenComplete((ws,error)->{synchronized(this){connecting=false;}if(ws!=null&&generation.get()!=captured)ws.abort();});
    }
    private CompletableFuture<WebSocket> sendSocket(WebSocket ws,String topic,String event,JsonObject payload) {
        var msg=new JsonObject();msg.addProperty("topic",topic);msg.addProperty("event",event);msg.add("payload",payload);
        msg.addProperty("ref",event.equals("phx_join")?"1":Integer.toString(socketRef.incrementAndGet()+1));if(!topic.equals("phoenix"))msg.addProperty("join_ref","1");
        return ws.sendText(msg.toString(),true);
    }
    public CompletableFuture<JsonArray> friends() {
        return social(s -> "/rest/v1/friends?select=friend_id,profiles:friend_id(id,username,avatar_url)&user_id=eq."+s.user+"&limit=100");
    }
    public CompletableFuture<JsonArray> incomingRequests() {
        return social(s -> "/rest/v1/friend_requests?select=id,from_id,created_at,profiles:from_id(id,username)&to_id=eq."+s.user+"&status=eq.pending&order=created_at.desc&limit=100");
    }
    public CompletableFuture<JsonArray> messages(UUID friend, Instant before) {
        Objects.requireNonNull(friend);
        return social(s -> "/rest/v1/messages?select=id,sender_id,receiver_id,content,created_at&or="
            +encode("(and(sender_id.eq."+s.user+",receiver_id.eq."+friend+"),and(sender_id.eq."+friend+",receiver_id.eq."+s.user+"))")
            +"&order=created_at.desc&limit=50"+(before==null?"":"&created_at=lt."+encode(before.toString())));
    }
    private CompletableFuture<JsonArray> social(java.util.function.Function<Session,String> path) {
        int captured=generation.get();
        return authorized().thenCompose(current -> request(path.apply(current),current.token)).thenApply(data -> {
            if (closed || generation.get()!=captured) throw new CompletionException(new IllegalStateException("Novex session ended."));
            return data.getAsJsonArray();
        });
    }

    public CompletableFuture<Void> sendFriendRequest(UUID target) {
        Objects.requireNonNull(target);
        return authorized().thenCompose(current->{
            if(current.user.equals(target))return failed("You cannot add yourself.");
            JsonObject body=new JsonObject();body.addProperty("from_id",current.user.toString());body.addProperty("to_id",target.toString());
            return request("/rest/v1/friend_requests",current.token,body).thenApply(ignored->null);
        });
    }
    public CompletableFuture<Void> acceptFriendRequest(UUID requestId) {
        JsonObject body=new JsonObject();body.addProperty("request_id",requestId.toString());
        return rpc("accept_friend_request",body).thenApply(ignored->null);
    }
    public CompletableFuture<Void> verifyMinecraft(String minecraftToken) {
        if(minecraftToken==null||minecraftToken.length()<20||minecraftToken.length()>16384)return failed("Launch Minecraft with a legitimate signed-in Minecraft account first.");
        return authorized().thenCompose(current->{
            JsonObject body=new JsonObject();body.addProperty("minecraftAccessToken",minecraftToken);
            return request("/functions/v1/companion-verify-minecraft",current.token,body).thenApply(ignored->null);
        });
    }
    public CompletableFuture<java.util.Set<UUID>> badges(java.util.Collection<UUID> players) {
        var body=new JsonObject();var ids=new JsonArray();players.stream().limit(100).forEach(id->ids.add(id.toString()));body.add("player_ids",ids);
        return request("/rest/v1/rpc/companion_badges",null,body).thenApply(data->{
            java.util.Set<UUID> result=new java.util.HashSet<>();
            for(var row:data.getAsJsonArray()){UUID id=UUID.fromString(row.getAsJsonObject().get("minecraft_id").getAsString());if(players.contains(id))result.add(id);}
            return java.util.Set.copyOf(result);
        });
    }
    public CompletableFuture<List<Partner>> partners() {
        // Anonymous request intentionally excludes unpublished admin-visible slides.
        return request("/rest/v1/home_slides?select=title,description,server_address&type=eq.partner_server&enabled=eq.true"
            +"&and="+encode("(or(starts_at.is.null,starts_at.lte."+Instant.now()+"),or(ends_at.is.null,ends_at.gt."+Instant.now()+"))")
            +"&order=sort_order.asc,id.asc&limit=50",null).thenApply(SupabaseApi::parsePartners);
    }
    static List<Partner> parsePartners(JsonElement data) {
        List<Partner> result=new ArrayList<>();
        for (var element:data.getAsJsonArray()) {
            try {
                var row=element.getAsJsonObject();String name=row.get("title").getAsString();
                String description=row.get("description").getAsString(), address=row.get("server_address").getAsString();
                if (name.isBlank() || name.length()>120 || description.length()>2000 || !validAddress(address)) continue;
                result.add(new Partner(name.replaceAll("[\\p{Cntrl}§]",""),description.replaceAll("[\\p{Cntrl}§]"," "),address));
                if (result.size()==50) break;
            } catch (RuntimeException ignored) { /* Discard invalid remote rows, not the entire menu. */ }
        }
        return List.copyOf(result);
    }
    static boolean validAddress(String value) {
        if (value==null || value.length()>253 || !value.matches("[A-Za-z0-9][A-Za-z0-9.-]*(?::[0-9]{1,5})?")) return false;
        int colon=value.indexOf(':');
        return colon<0 || Integer.parseInt(value.substring(colon+1))>=1 && Integer.parseInt(value.substring(colon+1))<=65535;
    }
    private CompletableFuture<JsonElement> request(String path,String token) { return request(path,token,null); }
    private CompletableFuture<JsonElement> request(String path,String token,JsonObject payload) {
        if (closed) return failed("Novex services are closed.");
        HttpRequest.Builder builder=HttpRequest.newBuilder(origin.resolve(path)).timeout(Duration.ofSeconds(12))
            .header("apikey",publicKey).header("Accept","application/json");
        if (token!=null) builder.header("Authorization","Bearer "+token);
        if (payload==null) builder.GET();
        else builder.header("Content-Type","application/json").header("Prefer","return=minimal")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
        CompletableFuture<HttpResponse<byte[]>> call=http.sendAsync(builder.build(), ignored -> new LimitedBody());
        return call.copy().orTimeout(15,TimeUnit.SECONDS).handle((response,error) -> {
            if (error!=null) call.cancel(true);
            if (error!=null) throw new CompletionException(new IllegalStateException("Unable to connect to Novex. Please retry."));
            int status=response.statusCode();
            if (status==401 && token!=null && session!=null && token.equals(session.token)) disconnect();
            if (status<200 || status>=300) throw new CompletionException(new ApiException(status,path.startsWith("/auth/v1/token") ? (status==429?"Too many sign-in attempts. Try again later.":"Unable to sign in. Check your email/password and confirm your email.") : status==401?"Novex session expired.":status==403?"This action is not allowed.":"Novex request failed (HTTP "+status+")."));
            if (response.body().length==0) return JsonNull.INSTANCE;
            try { return JsonParser.parseString(new String(response.body(),StandardCharsets.UTF_8)); }
            catch (RuntimeException e) { throw new CompletionException(new IllegalStateException("Novex returned an invalid response.")); }
        });
    }
    private static String encode(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
    private static <T> CompletableFuture<T> failed(String message) { return CompletableFuture.failedFuture(new IllegalStateException(message)); }
    @Override public void close() { closed=true; resetSession(); if(vault!=null)vault.close(); http.shutdownNow(); }
    /** Bound memory before receiving the full response. */
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body=new CompletableFuture<>();
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return body; }
        public void onSubscribe(Flow.Subscription value) { subscription=value; value.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer:buffers) {
                if ((long)bytes.size()+buffer.remaining()>262144) {
                    subscription.cancel();body.completeExceptionally(new IllegalStateException("Response too large"));return;
                }
                byte[] chunk=new byte[buffer.remaining()]; buffer.get(chunk);bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable ignored) { body.completeExceptionally(new IllegalStateException("Network response failed")); }
        public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
