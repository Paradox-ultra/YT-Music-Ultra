import com.google.protos.youtube.api.innertube.BrowseEndpointOuterClass;
import java.lang.reflect.*;import java.util.*;
/** Schema-only diagnostic. It never serializes or prints an account response or field values. */
public final class UltraLibraryCapture {
 static final int LIMIT=18000,DEPTH=48,MAX_TEXT=12000;
 static final class Scan{int left=LIMIT;final Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());final StringBuilder out=new StringBuilder();int matches;boolean truncated;}
 static String name(Class<?> c){String n=c.getName();return n.matches("[A-Za-z0-9_.$]{1,200}")?n:"unknown";}
 static void line(Scan s,String value){if(s.out.length()+value.length()+1<=MAX_TEXT)s.out.append(value).append('\n');else s.truncated=true;}
 static boolean liked(Object value){try{if(!(value instanceof bjue))return false;Object e=((bjue)value).j.l(BrowseEndpointOuterClass.browseEndpoint.d);return e instanceof bizw&&"VLLM".equals(((bizw)e).c);}catch(Throwable e){return false;}}
 static void fields(Object row,Scan s,String prefix,int depth,Set<Object> visited){if(row==null||depth>7||s.truncated||!visited.add(row))return;if(row instanceof List){int i=0;for(Object x:(List<?>)row){if(i++>=5)break;fields(x,s,prefix+"[]",depth+1,visited);}return;}if(!(row instanceof bhbk))return;
  for(Field f:row.getClass().getDeclaredFields()){if(Modifier.isStatic(f.getModifiers()))continue;line(s,prefix+"."+f.getName()+":"+name(f.getType()));if(!f.getType().isPrimitive()&&f.getType()!=String.class){try{f.setAccessible(true);Object v=f.get(row);if(v instanceof bhbk||v instanceof List)fields(v,s,prefix+"."+f.getName(),depth+1,visited);}catch(Throwable ignored){}}}
 }
 static void walk(Object node,List<Object> chain,String path,int d,Scan s){if(node==null||d>DEPTH||--s.left<0||s.truncated)return;if(node instanceof List){int i=0;for(Object x:(List<?>)node){if(i>=2000)break;walk(x,chain,path+"[]",d+1,s);i++;}return;}if(!(node instanceof bhbk)||!s.seen.add(node))return;
  if(liked(node)){s.matches++;line(s,"matched-reserved-liked-browse-command");line(s,"path:"+path);for(int i=Math.max(0,chain.size()-5);i<chain.size();i++){Object ancestor=chain.get(i);line(s,"ancestor:"+name(ancestor.getClass()));}Object row=chain.isEmpty()?node:chain.get(Math.max(0,chain.size()-2));fields(row,s,name(row.getClass()),0,Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>()));}
  List<Object> next=new ArrayList<>(chain);next.add(node);
  for(Field f:node.getClass().getDeclaredFields()){if(Modifier.isStatic(f.getModifiers())||f.getType().isPrimitive()||f.getType()==String.class)continue;try{f.setAccessible(true);Object v=f.get(node);if(v instanceof bhbk||v instanceof List)walk(v,next,path+"."+f.getName()+":"+name(f.getType()),d+1,s);}catch(Throwable ignored){}}
  if(node instanceof bhbg){try{Iterator<?> it=((bhbg)node).j.e();while(it.hasNext()){Object e=it.next();if(e instanceof Map.Entry)walk(((Map.Entry<?,?>)e).getValue(),next,path+".extension",d+1,s);}}catch(Throwable ignored){}}
 }
 public static String shape(Object response){Scan s=new Scan();line(s,"library-schema-only-v1");walk(response,new ArrayList<Object>(),"response",0,s);line(s,"status:"+(s.left<0||s.truncated?"bounded":s.matches==0?"no-liked-command":"captured"));return s.out.toString();}
 public static void accept(Object response){try{ultra.downloads.LibraryEvidence.store(shape(response));}catch(Throwable e){ultra.downloads.Diagnostics.error("library_schema",e);}}
}
