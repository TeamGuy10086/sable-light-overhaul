package dev.sablelightoverhaul.lighting.channel;

/**
 * What a channel asks a spotlight to do.
 *
 * <p><b>The order, once, and nothing else.</b> A channel's numbers are read position by position:
 * {@code 0 enable, 1 angle, 2 azimuth, 3 width, 4 intensity, 5 colour, 6 range, 7 falloff}. Numbers
 * the message does not reach keep whatever the lamp is already set to, so a two-number command aims
 * the lamp without touching anything else -- and a lamp driven by a channel is not silently reset by
 * whoever sends the shortest message.
 *
 * <p>A boolean value is the whole command: it switches the lamp and leaves the aim alone. Text is the
 * same vector with an optional colour list after a bar; the spotlight takes the first colour, since it
 * throws one cone.
 */
public record SpotlightChannel(boolean on,Double angle,Double azimuth,Double width,Double intensity,
                               Double colour,Double range,Double falloff) {
 public static final int ENABLE=0,ANGLE=1,AZIMUTH=2,WIDTH=3,INTENSITY=4,COLOUR=5,RANGE=6,FALLOFF=7;
 /** How a channel's command is reduced to this lamp's settings. Pure, so the order is testable. */
 public static SpotlightChannel decode(ChannelSignal signal){
  ChannelCommand command=command(signal);
  Double enable=command.at(ENABLE);
  boolean on=signal.kind()==ChannelSignal.Kind.BOOL?signal.bool():enable!=null?enable>=0.5:signal.on();
  return new SpotlightChannel(on,command.at(ANGLE),command.at(AZIMUTH),command.at(WIDTH),
    command.at(INTENSITY),command.at(COLOUR),command.at(RANGE),command.at(FALLOFF));
 }
 /** A real value is its own vector; text carries the vector and the colours; nothing else is empty. */
 public static ChannelCommand command(ChannelSignal signal){
  return switch(signal.kind()){
   case REAL->ChannelCommand.of(signal.numbers());
   case TEXT->ChannelCommand.parse(signal.textOrEmpty());
   default->ChannelCommand.EMPTY;
  };
 }
}
