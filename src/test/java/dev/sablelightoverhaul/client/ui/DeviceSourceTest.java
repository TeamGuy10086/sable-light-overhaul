package dev.sablelightoverhaul.client.ui;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class DeviceSourceTest {
    private static FieldSpec number(String key) {
        return new FieldSpec(key,"device",key,"",FieldSpec.Kind.SLIDER,0,20,0.01,List.of(),false,null);
    }
    @Test void equalPathsInDifferentWeaponKindsRemainIndependent() {
        var a=number("tank|size");var b=number("auto|size");
        var av=new AtomicReference<FieldValue>(new FieldValue.Num(1.25));
        var bv=new AtomicReference<FieldValue>(new FieldValue.Num(2.5));
        var source=new DeviceSource("cannon").bind(a,av::get,av::set).bind(b,bv::get,bv::set);
        var snapshot=source.read();source.write(a,new FieldValue.Num(3.75));
        assertEquals(new FieldValue.Num(3.75),source.read().get(a.key()));
        assertEquals(new FieldValue.Num(2.5),source.read().get(b.key()));
        assertEquals(new FieldValue.Num(1.25),snapshot.get(a.key()));
        assertFalse(source.serverNavigation());
    }
    @Test void invalidNumbersNeverReachThePayloadWriter() {
        var spec=number("size");var value=new AtomicReference<FieldValue>(new FieldValue.Num(1));
        var source=new DeviceSource("test").bind(spec,value::get,value::set);
        for(double n:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,21})source.write(spec,new FieldValue.Num(n));
        assertEquals(new FieldValue.Num(1),value.get());
    }
    @Test void duplicateBindingsAreRejectedAndReadOnlyRowsCannotWrite() {
        var spec=number("size");var source=new DeviceSource("test").bind(spec,()->new FieldValue.Num(3),null);
        assertFalse(source.canWrite(spec));source.write(spec,new FieldValue.Num(5));
        assertEquals(new FieldValue.Num(3),source.read().get("size"));
        assertThrows(IllegalArgumentException.class,()->source.bind(spec,()->null,null));
    }
}
