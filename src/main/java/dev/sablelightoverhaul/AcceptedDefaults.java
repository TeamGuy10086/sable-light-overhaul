package dev.sablelightoverhaul;

import com.electronwill.nightconfig.core.EnumGetMethod;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Accepted pack tuning used only when defining defaults, never to overwrite a loaded config. */
public final class AcceptedDefaults extends ModConfigSpec.Builder {
    private final JsonObject defaults;
    private final List<String> sections=new ArrayList<>();
    public AcceptedDefaults(String side){
        String resource="/assets/sablelightoverhaul/defaults/"+side+".json";
        try(var stream=Objects.requireNonNull(AcceptedDefaults.class.getResourceAsStream(resource),resource);
            var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){
            defaults=JsonParser.parseReader(reader).getAsJsonObject();
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
    @Override public ModConfigSpec.Builder push(List<String> path){super.push(path);sections.addAll(path);return this;}
    @Override public ModConfigSpec.Builder pop(int count){
        super.pop(count);sections.subList(sections.size()-count,sections.size()).clear();return this;
    }
    private <T> Supplier<T> tuned(List<String> path,Supplier<T> fallback){
        var full=new ArrayList<>(sections);full.addAll(path);
        JsonElement value=defaults.get(String.join(".",full));
        if(value==null)return fallback; // Future settings keep their explicitly authored defaults.
        T exemplar=fallback.get();
        @SuppressWarnings("unchecked") T decoded=(T)decode(value,exemplar);
        return ()->decoded;
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static Object decode(JsonElement value,Object exemplar){
        if(exemplar instanceof Enum<?> e)return Enum.valueOf(e.getDeclaringClass(),value.getAsString());
        if(exemplar instanceof Boolean)return value.getAsBoolean();
        if(exemplar instanceof Integer)return value.getAsInt();
        if(exemplar instanceof Long)return value.getAsLong();
        if(exemplar instanceof Double)return value.getAsDouble();
        if(exemplar instanceof String)return value.getAsString();
        if(exemplar instanceof List<?> list){
            var result=new ArrayList<>();
            for(var element:value.getAsJsonArray()){
                Object item=list.isEmpty()?"":list.getFirst();
                result.add(decode(element,item));
            }
            return List.copyOf(result);
        }
        throw new IllegalArgumentException("Unsupported default type: "+exemplar.getClass());
    }
    @Override public ModConfigSpec.BooleanValue define(List<String> p,Supplier<Boolean> d){return super.define(p,tuned(p,d));}
    @Override public ModConfigSpec.IntValue defineInRange(List<String> p,Supplier<Integer> d,int min,int max){return super.defineInRange(p,tuned(p,d),min,max);}
    @Override public ModConfigSpec.LongValue defineInRange(List<String> p,Supplier<Long> d,long min,long max){return super.defineInRange(p,tuned(p,d),min,max);}
    @Override public ModConfigSpec.DoubleValue defineInRange(List<String> p,Supplier<Double> d,double min,double max){return super.defineInRange(p,tuned(p,d),min,max);}
    @Override public <T> ModConfigSpec.ConfigValue<T> define(List<String> p,Supplier<T> d,Predicate<Object> validator,Class<?> type){
        return super.define(p,tuned(p,d),validator,type);
    }
    @Override public <V extends Enum<V>> ModConfigSpec.EnumValue<V> defineEnum(List<String> p,Supplier<V> d,EnumGetMethod converter,Predicate<Object> validator,Class<V> type){
        return super.defineEnum(p,tuned(p,d),converter,validator,type);
    }
    @Override public <T> ModConfigSpec.ConfigValue<List<? extends T>> defineList(List<String> p,Supplier<List<? extends T>> d,Supplier<T> newElement,Predicate<Object> validator,ModConfigSpec.Range<Integer> size){
        return super.defineList(p,tuned(p,d),newElement,validator,size);
    }
}
