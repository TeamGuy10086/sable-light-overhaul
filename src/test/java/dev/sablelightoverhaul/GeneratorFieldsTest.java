package dev.sablelightoverhaul;
import dev.sablelightoverhaul.lighting.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GeneratorFieldsTest {
 @Test void everyFieldRoundTripsOnItsOwnKind(){
  for(var kind:GeneratorKind.values()){
   var values=new GeneratorValues();
   for(var field:GeneratorFields.of(kind)){
    switch(field.type()){
     // The first write is always stored, so a value equal to the config default is kept too.
     case NUMBER->{assertTrue(values.setNumber(kind,field.id(),1));assertEquals(1,values.number(kind,field.id()),1e-9);
                   assertFalse(values.setNumber(kind,field.id(),1),"writing the same value again changes nothing");}
     case TOGGLE->{assertTrue(values.setFlag(kind,field.id(),true));assertTrue(values.flag(kind,field.id()));}
     case TEXT->{assertTrue(values.setText(kind,field.id(),"a:b"));assertEquals("a:b",values.text(kind,field.id()));
                 assertFalse(values.setText(kind,field.id(),"a:b"));}
    }
   }
  }
 }
 @Test void numbersAreClampedIntoTheirDeclaredRange(){
  var values=new GeneratorValues();
  assertTrue(values.setNumber(GeneratorKind.DUST,"searchDepth",1.0E6));
  assertEquals(128,values.number(GeneratorKind.DUST,"searchDepth"),1e-9);
  assertTrue(values.setNumber(GeneratorKind.DUST,"searchDepth",-5));
  assertEquals(1,values.number(GeneratorKind.DUST,"searchDepth"),1e-9);
  // An unchanged value reports no change, so a page writing back what it displayed is free.
  assertFalse(values.setNumber(GeneratorKind.DUST,"searchDepth",1));
 }
 @Test void aFieldOfTheOtherKindDoesNotExist(){
  var values=new GeneratorValues();
  assertNull(GeneratorFields.find(GeneratorKind.DUST,"intervalAtMaxSignal"));
  assertNull(GeneratorFields.find(GeneratorKind.SMOKE,"searchDepth"));
  assertFalse(values.setNumber(GeneratorKind.DUST,"intervalAtMaxSignal",4));
  assertFalse(values.setFlag(GeneratorKind.DUST,"riseSpeed",true));
  // A number field refuses a flag write and vice versa, so a page bug cannot corrupt the block.
  assertFalse(values.setFlag(GeneratorKind.DUST,"searchDepth",true));
  assertFalse(values.setNumber(GeneratorKind.DUST,"tintFromBlock",1));
 }
 @Test void bothKindsShareTheCommonFields(){
  for(var kind:GeneratorKind.values()){
   var ids=GeneratorFields.of(kind).stream().map(GeneratorFields.Field::id).toList();
   assertTrue(ids.containsAll(java.util.List.of(GeneratorFields.ENABLED,GeneratorFields.FORCE,
     GeneratorFields.OFFSET_X,GeneratorFields.OFFSET_Z,GeneratorFields.PARTICLE,
     // How thick a puff is born and how fast it thins: shared, because all three kinds emit one.
     "opacity","fade")));
   assertEquals(ids.size(),new java.util.HashSet<>(ids).size(),"field ids must be unique");
   assertTrue(GeneratorFields.of(kind).stream().allMatch(field->field.labelKey().startsWith("sablelightoverhaul.generator.field.")));
  }
 }
 @Test void theWrenchStepMovesHorizontallyAndClamps(){
  var values=new GeneratorValues();
  for(int i=0;i<3;i++)values.nudge(GeneratorKind.SMOKE,1,0,GeneratorBlock.STEP);
  assertEquals(0.3,values.number(GeneratorKind.SMOKE,GeneratorFields.OFFSET_X),1e-9);
  for(int i=0;i<400;i++)values.nudge(GeneratorKind.SMOKE,-1,0,GeneratorBlock.STEP);
  assertEquals(-16,values.number(GeneratorKind.SMOKE,GeneratorFields.OFFSET_X),1e-9);
  values.nudge(GeneratorKind.SMOKE,0,1,GeneratorBlock.STEP);
  assertEquals(0.1,values.number(GeneratorKind.SMOKE,GeneratorFields.OFFSET_Z),1e-9);
  assertEquals(0,values.offset(GeneratorKind.SMOKE).y,1e-9,"the height always comes from the ground");
 }
 @Test void savingRoundTripsOnlyEditedValues(){
  var values=new GeneratorValues();
  values.setNumber(GeneratorKind.SMOKE,"intervalAtMaxSignal",7);
  values.setFlag(GeneratorKind.SMOKE,GeneratorFields.FORCE,true);
  values.setText(GeneratorKind.SMOKE,GeneratorFields.PARTICLE,"minecraft:large_smoke");
  var tag=new CompoundTag();
  values.save(tag);
  var read=new GeneratorValues();
  read.load(tag);
  assertEquals(7,read.number(GeneratorKind.SMOKE,"intervalAtMaxSignal"),1e-9);
  assertTrue(read.flag(GeneratorKind.SMOKE,GeneratorFields.FORCE));
  assertEquals("minecraft:large_smoke",read.text(GeneratorKind.SMOKE,GeneratorFields.PARTICLE));
 }
 @Test void anUntouchedValueStillFollowsTheConfigDefault(){
  // The defaults that do not come from the config must hold on a fresh block; the config-backed
  // ones need a loaded config, so they are verified in game rather than here.
  var values=new GeneratorValues();
  assertTrue(values.flag(GeneratorKind.DUST,GeneratorFields.ENABLED),"a new generator starts enabled");
  assertFalse(values.flag(GeneratorKind.DUST,GeneratorFields.FORCE),"continuous emission is a tuning switch, off by default");
  assertEquals(0,values.offset(GeneratorKind.DUST).x,1e-9);
  assertEquals(0,values.offset(GeneratorKind.DUST).z,1e-9);
  assertFalse(values.flag(GeneratorKind.SMOKE,GeneratorFields.FORCE));
 }
}
