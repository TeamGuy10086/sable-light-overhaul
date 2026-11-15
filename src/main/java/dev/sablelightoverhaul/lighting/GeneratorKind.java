package dev.sablelightoverhaul.lighting;

/** The two emitters. Everything else about them is shared: anchor, offset, wrench and page. */
public enum GeneratorKind {
 /** Kicks dust off the ground the body is driving over. */
 DUST("dust_generator"),
 /** Vents smoke that rises in world space, so a moving body leaves it behind. */
 SMOKE("smoke_generator"),
 /** Sheds a vortex off the blocks it is registered to, while the body it rides on moves. */
 VORTEX("vortex_generator");
 private final String id;
 GeneratorKind(String id){this.id=id;}
 public String id(){return id;}
 public String labelKey(){return "block.sablelightoverhaul."+id;}
}
