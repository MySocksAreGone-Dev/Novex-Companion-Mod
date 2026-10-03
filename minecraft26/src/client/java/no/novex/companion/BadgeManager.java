package no.novex.companion;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
public final class BadgeManager {
 private static BadgeManager instance;
 private final SupabaseApi api;private final NovexConfig config;
 private Set<UUID> verified=Set.of();private long next,expires;private boolean loading;
 BadgeManager(SupabaseApi api,NovexConfig config){this.api=api;this.config=config;instance=this;}
 void tick(Minecraft client){
  if(client.level==null){verified=Set.of();next=0;return;}
  long now=System.currentTimeMillis();if(now>expires)verified=Set.of();
  if(api==null||loading||now<next||client.getConnection()==null)return;
  if(!config.get(NovexConfig.Option.NAMETAG_BADGES)&&!config.get(NovexConfig.Option.TAB_BADGES)&&!config.get(NovexConfig.Option.CHAT_BADGES))return;
  next=now+60000;loading=true;
  var world=client.level;
  var ids=client.getConnection().getOnlinePlayers().stream().map(entry->BadgeVisual.profileId(entry.getProfile())).limit(100).toList();
  api.badges(ids).whenComplete((data,error)->client.execute(()->{loading=false;if(error==null&&client.level==world){verified=data;expires=System.currentTimeMillis()+65000;}}));
 }
 public static Component decorate(UUID player,Component name,NovexConfig.Option option){
  var manager=instance;
  return manager!=null&&name!=null&&manager.config.get(option)&&manager.verified.contains(player)?BadgeVisual.prefix(name):name;
 }
}
