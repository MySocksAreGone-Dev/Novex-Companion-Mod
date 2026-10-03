package no.novex.companion;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Refresh tokens only: Secret Service on Linux, DPAPI CurrentUser on Windows. No plaintext fallback. */
class SessionVault implements AutoCloseable {
    private final Path file;
    private final String scope;
    private final boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Novex secure session");t.setDaemon(true);return t;});
    private volatile String status="Checking secure session storage...";
    SessionVault(Path config, String origin) {
        file=config.resolveSibling("novex-session.dpapi");
        try {scope=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((origin+"\n"+config.toAbsolutePath().normalize()).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException("Secure session storage unavailable.");}
    }
    String status(){return status;}
    CompletableFuture<String> load(){return CompletableFuture.supplyAsync(()->{
        try {
            String value;
            if(windows){if(!Files.exists(file)){status="Sign-in will be saved securely on this device.";return null;}if(Files.size(file)>65536)throw new Exception();value=crypt(false,Files.readString(file));}
            else value=command(List.of("/usr/bin/secret-tool","lookup","application","novex-companion","instance",scope),"",true);
            status=value==null||value.isBlank()?"Sign in to save your session securely.":"Saved sign-in found.";
            return value==null||value.isBlank()?null:valid(value.strip());
        } catch(Exception ignored){status="Secure storage unavailable; sign-in lasts until Minecraft closes.";return null;}
    },worker);}
    CompletableFuture<Void> save(String refresh){return CompletableFuture.runAsync(()->{
        try {
            valid(refresh);
            if(windows){String encrypted=crypt(true,refresh);Files.createDirectories(file.getParent());Path temp=Files.createTempFile(file.getParent(),"novex-session-",".tmp");try {Files.writeString(temp,encrypted);try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}}
            else command(List.of("/usr/bin/secret-tool","store","--label=Novex Companion sign-in","application","novex-companion","instance",scope),refresh,false);
            status="Sign-in is saved securely on this device.";
        }catch(Exception ignored){status="Unable to save securely; sign in again after restarting.";}
    },worker);}
    CompletableFuture<Void> clear(){return CompletableFuture.runAsync(()->{
        try{if(windows)Files.deleteIfExists(file);else command(List.of("/usr/bin/secret-tool","clear","application","novex-companion","instance",scope),"",true);status="Saved sign-in removed.";}
        catch(Exception ignored){status="Could not remove saved sign-in from the system keyring.";}
    },worker);}
    private String crypt(boolean encrypt,String input)throws Exception{
        String method=encrypt?"Protect":"Unprotect";
        String source=encrypt?"[Text.Encoding]::UTF8.GetBytes($inputValue)":"[Convert]::FromBase64String($inputValue)";
        String output=encrypt?"[Convert]::ToBase64String($result)":"[Text.Encoding]::UTF8.GetString($result)";
        String script="$ErrorActionPreference='Stop';Add-Type -AssemblyName System.Security;$inputValue=[Console]::In.ReadToEnd();$result=[Security.Cryptography.ProtectedData]::"+method+"("+source+",$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);[Console]::Out.Write("+output+")";
        String root=System.getenv("SystemRoot");if(root==null)throw new Exception();
        return command(List.of(Path.of(root,"System32","WindowsPowerShell","v1.0","powershell.exe").toString(),"-NoProfile","-NonInteractive","-Command",script),input,false);
    }
    private static String valid(String value){if(value.isBlank()||value.length()>16384||!value.matches("[A-Za-z0-9._~-]+"))throw new IllegalArgumentException("Invalid saved session.");return value;}
    private static String command(List<String> args,String input,boolean emptyAllowed)throws Exception{
        Process p=new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        // Drain stdout concurrently, bounded, so a broken keyring helper cannot deadlock the game.
        var output=CompletableFuture.supplyAsync(()->{try(var stream=p.getInputStream()){return stream.readNBytes(65537);}catch(Exception e){return new byte[0];}});
        try {
            try(var stream=p.getOutputStream()){stream.write(input.getBytes(StandardCharsets.UTF_8));}
            if(!p.waitFor(15,TimeUnit.SECONDS))throw new Exception();
            byte[] bytes=output.get(2,TimeUnit.SECONDS);
            if(bytes.length>65536||p.exitValue()!=0&&!(emptyAllowed&&p.exitValue()==1&&bytes.length==0))throw new Exception();
            return new String(bytes,StandardCharsets.UTF_8);
        }finally{p.destroyForcibly();}
    }
    @Override public void close(){worker.shutdown();try{if(!worker.awaitTermination(17,TimeUnit.SECONDS))worker.shutdownNow();}catch(InterruptedException e){Thread.currentThread().interrupt();worker.shutdownNow();}}
}
