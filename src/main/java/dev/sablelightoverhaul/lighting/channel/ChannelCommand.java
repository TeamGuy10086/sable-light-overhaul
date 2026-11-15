package dev.sablelightoverhaul.lighting.channel;

import java.util.ArrayList;
import java.util.List;

/**
 * One channel command: a numeric vector, and optionally a list of palette colours after a bar.
 *
 * <p>Two shapes reach the same place. A channel that carries an AFN REAL value gives its components
 * straight as the vector, which is the shortest way for a frequency board's script to aim a lamp. A
 * channel that carries TEXT gives {@code "1 30 0 8 15 | 3,7,11"}: everything before the bar is the
 * vector, everything after it is the colours, and that bar is the whole reason colours are expressible
 * at all -- a bare list of numbers cannot say whether it means angles or a palette.
 *
 * <p>Numbers may be separated by spaces, commas or semicolons and unparsable tokens are skipped
 * rather than fatal: a transmitter that publishes a slightly different string should move the lamp,
 * not throw inside a server tick. Colours are palette indices clamped to the same 0..15 the page's own
 * colour field uses, so a channel can drive a disco ball exactly the way a player can.
 */
public record ChannelCommand(double[] numbers,int[] colors) {
 private static final char BAR='|';
 public static final ChannelCommand EMPTY=new ChannelCommand(new double[0],new int[0]);
 /** The command a REAL value stands for: its components, and no colours. */
 public static ChannelCommand of(double[] reals){
  return reals==null||reals.length==0?EMPTY:new ChannelCommand(reals.clone(),new int[0]);
 }
 /** Parse the text form; see the class note for the syntax. */
 public static ChannelCommand parse(String text){
  if(text==null||text.isBlank())return EMPTY;
  String[] sides=text.split("\\"+BAR,2);
  double[] numbers=numbers(sides[0]);
  int[] colors=sides.length<2?new int[0]:colors(sides[1]);
  return new ChannelCommand(numbers,colors);
 }
 private static double[] numbers(String part){
  List<Double> out=new ArrayList<>();
  for(String token:part.trim().split("[\\s,;]+")){
   if(token.isEmpty())continue;
   try{out.add(Double.parseDouble(token));}catch(NumberFormatException ignored){}
  }
  double[] result=new double[out.size()];
  for(int i=0;i<result.length;i++)result[i]=out.get(i);
  return result;
 }
 private static int[] colors(String part){
  List<Integer> out=new ArrayList<>();
  for(String token:part.trim().split("[\\s,;]+")){
   if(token.isEmpty())continue;
   try{out.add(Math.max(0,Math.min(15,Integer.parseInt(token))));}catch(NumberFormatException ignored){}
  }
  int[] result=new int[out.size()];
  for(int i=0;i<result.length;i++)result[i]=out.get(i);
  return result;
 }
 /** One number of the vector, or {@code absent} when the command does not reach that far. */
 public Double at(int index){
  return index>=0&&index<numbers.length?numbers[index]:null;
 }
 public boolean hasColors(){return colors.length>0;}
}
