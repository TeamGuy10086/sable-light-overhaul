package dev.sablelightoverhaul;

/**
 * How much of the view a light covers, which is how the limited shadow slots are handed out.
 *
 * <p>The patched shader has no say in <em>which</em> lights cast: the mod sends it a bit per light and the
 * pack obeys. Choosing those bits by distance gets it backwards in both directions — a flash two blocks
 * behind the camera wins a slot it cannot possibly need, while a searchlight across the view loses one —
 * and choosing them by "where does the light's centre land on screen" is worse still, because it misses
 * the case this exists for.
 *
 * <p><b>The light that leaks through a wall is behind that wall, and therefore nowhere on screen.</b> It
 * still pours light onto the surface the player is looking at, so a test on the light's own screen
 * position cannot find it. What can find it is the intersection of the light's reach with the view
 * volume: the lamp is a couple of blocks beyond the wall, squarely inside the player's field of view, and
 * only the wall's depth buffer knows it should not be lighting anything.
 *
 * <p>So the weight is a solid angle, tapered by how much of the light's reach is inside the frustum:
 * {@code radius^2 / (radius^2 + distance^2)}, times a per-plane factor that is one while the light's
 * centre is inside that plane and falls to zero as the centre leaves by its own radius. Cheap, pure
 * arithmetic, and testable — which is why it lives here and not in the client class.
 */
public final class LightScreenWeight {
 /** The four side planes of a view frustum; the near and far planes are not useful here. */
 public static final int SIDES=4;
 /**
  * Largest radius the weight will believe.
  *
  * <p>A beam's extent is its <em>length</em>: a 512-block searchlight reports a 512-block radius, which
  * would make it outrank everything on size alone from any distance. A beam is not 512 blocks of screen,
  * so the size term is capped at something a genuinely large sphere could be. Brightness and position
  * still count in full.
  */
 public static final double RADIUS_CAP=32.0;
 private static final double TINY=1.0E-6;
 private LightScreenWeight(){}
 /**
  * The four side planes of the camera's frustum, in world space, with their normals pointing inward.
  *
  * @param corners four points on the near plane in <b>camera-relative</b> coordinates, in cyclic order
  *                around the rectangle (top-left, top-right, bottom-right, bottom-left), flattened as
  *                {@code x0,y0,z0,x1,y1,z1,...}
  * @return {@code SIDES} planes of four doubles each: {@code nx,ny,nz,d}, where a point {@code p} is on
  *         the inside when {@code nx*px + ny*py + nz*pz + d >= 0}. A degenerate edge is left as four
  *         zeroes, which {@link #of} skips.
  */
 public static double[] sidePlanes(double camX,double camY,double camZ,double[] corners){
  double[] planes=new double[SIDES*4];
  // The near-plane centroid is inside all four planes, which is how a normal's sign is settled below.
  double cx=(corners[0]+corners[3]+corners[6]+corners[9])/4.0;
  double cy=(corners[1]+corners[4]+corners[7]+corners[10])/4.0;
  double cz=(corners[2]+corners[5]+corners[8]+corners[11])/4.0;
  for(int i=0;i<SIDES;i++){
   int a=i*3, b=((i+1)%SIDES)*3;
   double ax=corners[a], ay=corners[a+1], az=corners[a+2];
   double bx=corners[b], by=corners[b+1], bz=corners[b+2];
   // Both corners are camera-relative, so this is the plane through the camera and that near-plane edge.
   double nx=ay*bz-az*by, ny=az*bx-ax*bz, nz=ax*by-ay*bx;
   double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
   if(length<TINY) continue;
   nx/=length; ny/=length; nz/=length;
   if(nx*cx+ny*cy+nz*cz<0.0){nx=-nx; ny=-ny; nz=-nz;}
   int o=i*4;
   planes[o]=nx; planes[o+1]=ny; planes[o+2]=nz;
   // The plane passes through the camera, so its constant is fixed by that point alone.
   planes[o+3]=-(nx*camX+ny*camY+nz*camZ);
  }
  return planes;
 }
 /**
  * The weight of one light for a shadow slot: 0 when it cannot light anything on screen, larger the more
  * of the view its reach covers and the brighter it is.
  */
 public static double of(AreaLight light,double camX,double camY,double camZ,double[] planes){
  double radius=Math.min(RADIUS_CAP,Math.max(light.extentX(),Math.max(light.extentY(),light.extentZ())));
  double dx=light.x()-camX, dy=light.y()-camY, dz=light.z()-camZ;
  double distanceSq=dx*dx+dy*dy+dz*dz;
  double visible=1.0;
  for(int i=0;i<SIDES;i++){
   int b=i*4;
   double nx=planes[b], ny=planes[b+1], nz=planes[b+2];
   if(nx==0.0&&ny==0.0&&nz==0.0) continue;
   double signed=nx*light.x()+ny*light.y()+nz*light.z()+planes[b+3];
   // One while the light's centre is inside this plane; falling to nothing once it has left by its own
   // radius, i.e. once none of the light can reach into the view at all.
   if(signed<=-radius) return 0.0;
   visible*=Math.min(1.0,Math.max(0.0,(signed+radius)/Math.max(radius,TINY)));
  }
  double coverage=radius*radius/(radius*radius+distanceSq+TINY);
  return coverage*visible*(light.brightness()/15.0);
 }
}
