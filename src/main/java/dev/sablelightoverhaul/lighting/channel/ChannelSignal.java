package dev.sablelightoverhaul.lighting.channel;

/**
 * What a channel last said, in this mod's own terms.
 *
 * <p>This is the boundary: the AFN types stop here, and everything above (the blocks, the page, the
 * mapping from a channel to a lamp's settings) works on this record. That keeps the dependency
 * optional at compile time, keeps every mapping testable without a game, and means a change in AFN's
 * value model touches exactly one class.
 *
 * <p>{@code strength} is the channel's held signal, 0..15. AFN channels hold their value rather than
 * pulsing it, which is why nothing here has an edge in it: a device that wants "on while the channel
 * is on" reads {@link #on()}, and a device that wants an edge has no use for this mod's channels.
 */
public record ChannelSignal(int strength,Kind kind,boolean bool,double[] reals,String text) {
 public enum Kind { /** Nothing is typed on the channel; only the strength means anything. */ NONE, BOOL, REAL, TEXT }
 /** A channel that is attached but saying nothing. */
 public static final ChannelSignal NOTHING=new ChannelSignal(0,Kind.NONE,false,new double[0],"");
 public boolean present(){return kind!=Kind.NONE;}
 /** Whether the channel carries a signal at all. */
 public boolean powered(){return strength>0;}
 /**
  * Whether the channel says "on".
  *
  * <p>A typed boolean is believed as it stands, because that is what a boolean is for. Anything else
  * falls back to the held signal, which is what a redstone-minded device would read anyway.
  */
 public boolean on(){return kind==Kind.BOOL?bool:powered();}
 /** The typed value as reals, or an empty array when the channel carries no numbers. */
 public double[] numbers(){return reals==null?new double[0]:reals.clone();}
 /** The typed value as text, or an empty string. */
 public String textOrEmpty(){return text==null?"":text;}
}
