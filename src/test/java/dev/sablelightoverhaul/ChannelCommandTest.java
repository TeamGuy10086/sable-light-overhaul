package dev.sablelightoverhaul;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import dev.sablelightoverhaul.lighting.channel.ChannelCommand;
import dev.sablelightoverhaul.lighting.channel.ChannelSignal;
import dev.sablelightoverhaul.lighting.channel.DiscoChannel;
import dev.sablelightoverhaul.lighting.channel.SpotlightChannel;
import org.junit.jupiter.api.Test;

/**
 * The channel command language: where a channel's numbers land, and what happens when it says nothing.
 *
 * <p>These are the rules a player writing a frequency board's script has to be able to rely on, so
 * they are pinned here rather than only in the docks: the order of the vector, that an absent position
 * leaves its setting alone, and that colours only exist after the bar.
 */
class ChannelCommandTest {
 @Test void textCarriesTheVectorAndTheColours(){
  ChannelCommand command=ChannelCommand.parse("1 30 -15 8 15 2 64 0 | 3,7,11");
  assertEquals(8,command.numbers().length);
  assertEquals(1.0,command.at(0));
  assertEquals(30.0,command.at(1));
  assertEquals(-15.0,command.at(2));
  assertArrayEquals(new int[]{3,7,11},command.colors());
  assertTrue(command.hasColors());
 }
 @Test void withoutABarThereAreNoColours(){
  ChannelCommand command=ChannelCommand.parse("3,7,11");
  assertArrayEquals(new double[]{3,7,11},command.numbers());
  assertFalse(command.hasColors(),"a bare list of numbers cannot mean a palette");
 }
 @Test void nonsenseIsSkippedRatherThanFatal(){
  ChannelCommand command=ChannelCommand.parse("1 oops 8 | x,9");
  assertArrayEquals(new double[]{1,8},command.numbers());
  assertArrayEquals(new int[]{9},command.colors());
  assertArrayEquals(new double[0],ChannelCommand.parse("   ").numbers());
  assertArrayEquals(new double[0],ChannelCommand.parse(null).numbers());
 }
 @Test void coloursArePaletteIndices(){
  assertArrayEquals(new int[]{0,15,15},ChannelCommand.parse("| -4,15,99").colors());
 }
 @Test void theSpotlightReadsTheOrderItDocuments(){
  // enable, angle, azimuth, width, intensity, colour, range, falloff
  SpotlightChannel asked=SpotlightChannel.decode(
    new ChannelSignal(15,ChannelSignal.Kind.REAL,false,new double[]{1,30,-15,8,12,4,64,2},""));
  assertTrue(asked.on());
  assertEquals(30.0,asked.angle());
  assertEquals(-15.0,asked.azimuth());
  assertEquals(8.0,asked.width());
  assertEquals(12.0,asked.intensity());
  assertEquals(4.0,asked.colour());
  assertEquals(64.0,asked.range());
  assertEquals(2.0,asked.falloff());
 }
 @Test void whatTheMessageDoesNotMentionIsLeftAlone(){
  SpotlightChannel asked=SpotlightChannel.decode(
    new ChannelSignal(15,ChannelSignal.Kind.REAL,false,new double[]{1,30},""));
  assertTrue(asked.on());
  assertEquals(30.0,asked.angle());
  assertNull(asked.width(),"a two-number command must not reset the rest of the lamp");
  assertNull(asked.colour());
  assertNull(asked.falloff());
 }
 @Test void aBooleanIsTheWholeCommand(){
  SpotlightChannel on=SpotlightChannel.decode(new ChannelSignal(15,ChannelSignal.Kind.BOOL,true,new double[0],""));
  assertTrue(on.on());
  assertNull(on.angle());
  SpotlightChannel off=SpotlightChannel.decode(new ChannelSignal(15,ChannelSignal.Kind.BOOL,false,new double[0],""));
  assertFalse(off.on(),"a typed boolean is believed over the held signal");
 }
 @Test void withNothingTypedTheHeldSignalDecides(){
  assertTrue(SpotlightChannel.decode(new ChannelSignal(7,ChannelSignal.Kind.NONE,false,new double[0],"")).on());
  assertFalse(SpotlightChannel.decode(new ChannelSignal(0,ChannelSignal.Kind.NONE,false,new double[0],"")).on());
  assertFalse(SpotlightChannel.decode(new ChannelSignal(0,ChannelSignal.Kind.REAL,false,new double[]{0},"")).on(),
    "an explicit zero in the enable position is off even while the channel is powered");
 }
 @Test void theDiscoBallReadsItsOwnOrderAndItsColours(){
  // enable, spin, ray width, intensity, range, beams, falloff
  DiscoChannel asked=DiscoChannel.decode(
    new ChannelSignal(15,ChannelSignal.Kind.TEXT,false,new double[0],"1 20 8 15 64 12 0 | 3,7,11"));
  assertTrue(asked.on());
  assertEquals(20.0,asked.spin());
  assertEquals(8.0,asked.width());
  assertEquals(15.0,asked.intensity());
  assertEquals(64.0,asked.range());
  assertEquals(12.0,asked.beams());
  assertEquals(0.0,asked.falloff());
  assertArrayEquals(new int[]{3,7,11},asked.colors());
 }
 @Test void aRealValueHasNoColoursToOffer(){
  DiscoChannel asked=DiscoChannel.decode(
    new ChannelSignal(15,ChannelSignal.Kind.REAL,false,new double[]{1,20,8},""));
  assertEquals(20.0,asked.spin());
  assertEquals(0,asked.colors().length);
 }
}
