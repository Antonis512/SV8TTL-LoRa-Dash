package org.overmesh.android;
import java.util.Arrays;
/** Immutable channel-copy confirmation; secrets stay native and are never logged. */
final class ChannelCopyPlan {
 private final ChannelAdmin source,target;private final int slot,sourceGeneration,targetGeneration;private final String title;private final byte[] key;
 ChannelCopyPlan(ChannelAdmin source,ChannelAdmin target,int slot,String title,int sourceGeneration,int targetGeneration){
  if(source==target||source.meshcore!=target.meshcore)throw new IllegalArgumentException("Choose two different radios of the same network");
  byte[] secret=source.secrets.get(slot);if(secret==null)throw new IllegalArgumentException("Channel key not reported. Reconnect the source radio first");
  this.source=source;this.target=target;this.slot=slot;this.title=title;this.key=secret.clone();this.sourceGeneration=sourceGeneration;this.targetGeneration=targetGeneration;
 }
 byte[] prepare(ChannelAdmin currentSource,ChannelAdmin currentTarget,boolean sourceReady,boolean targetReady,int sourceGeneration,int targetGeneration,String currentTitle){
  if(!sourceReady||!targetReady||currentSource!=source||currentTarget!=target||sourceGeneration!=this.sourceGeneration||targetGeneration!=this.targetGeneration)throw new IllegalArgumentException("Connection changed; choose the radios again");
  if(!title.equals(currentTitle)||!Arrays.equals(key,source.secrets.get(slot)))throw new IllegalArgumentException("Source channel changed; choose it again");
  if(target.slots.containsValue(title))throw new IllegalArgumentException("Skipped: destination already has this channel name");
  String mode="private",encoded;
  if(target.meshcore){StringBuilder hex=new StringBuilder();for(byte b:key)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));encoded=hex.toString();}
  else{encoded=java.util.Base64.getEncoder().encodeToString(key);if(key.length==0)mode="none";else if(key.length==1&&key[0]==1)mode="default";}
  return target.start(title,mode,encoded);
 }
}
