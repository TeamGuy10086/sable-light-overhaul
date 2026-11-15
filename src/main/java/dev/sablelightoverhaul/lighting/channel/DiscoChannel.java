package dev.sablelightoverhaul.lighting.channel;

/**
 * What a channel asks a disco ball to do.
 *
 * <p>The numbers are read position by position:
 * {@code 0 enable, 1 spin, 2 ray width, 3 intensity, 4 range, 5 beams, 6 falloff}. Positions the
 * message does not reach keep their current value, so a channel can drive one knob or all of them.
 *
 * <p><b>The colours come after a bar</b> -- {@code "1 20 8 15 64 12 0 | 3,7,11"} -- because a bare
 * list of numbers cannot say whether it is a spin speed or a palette. The list is the same syntax the
 * ball's own page uses, so a player who has set colours by hand can copy the string into a channel
 * unchanged. A boolean value switches the show and changes nothing else.
 */
public record DiscoChannel(boolean on,Double spin,Double width,Double intensity,Double range,
                           Double beams,Double falloff,int[] colors) {
 public static final int ENABLE=0,SPIN=1,WIDTH=2,INTENSITY=3,RANGE=4,BEAMS=5,FALLOFF=6;
 public static DiscoChannel decode(ChannelSignal signal){
  ChannelCommand command=SpotlightChannel.command(signal);
  Double enable=command.at(ENABLE);
  boolean on=signal.kind()==ChannelSignal.Kind.BOOL?signal.bool():enable!=null?enable>=0.5:signal.on();
  return new DiscoChannel(on,command.at(SPIN),command.at(WIDTH),command.at(INTENSITY),
    command.at(RANGE),command.at(BEAMS),command.at(FALLOFF),command.colors());
 }
}
