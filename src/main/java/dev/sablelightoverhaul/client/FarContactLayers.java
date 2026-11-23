package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.FarContacts;
import dev.wolfmisc.WolfMiscConfig;
import dev.wolfmisc.api.HoloFacing;
import dev.wolfmisc.api.HoloFrame;
import dev.wolfmisc.api.HoloLabel;
import dev.wolfmisc.api.HoloLayerApi;
import dev.wolfmisc.api.HoloMesh;
import dev.wolfmisc.api.HoloPlacementV1;
import dev.wolfmisc.api.HoloRadarApi;
import dev.wolfmisc.api.HoloSurfaceId;
import dev.wolfmisc.api.HoloUnits;
import dev.wolfmisc.api.selection.HoloVisibilityApi;
import dev.wolfmisc.api.selection.InformationMaskV1;
import dev.wolfmisc.api.selection.SelectionBounds;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** WolfMisc's scene layers draw snapshot contacts without instantiating any entities. */
public final class FarContactLayers {
    private static ClientLevel owner;
    private static List<FarContacts.Contact> contacts = List.of();
    private static int ticksUntilRequest;
    private static int ticksUntilExpiry;
    private static boolean integratedRadar;
    private FarContactLayers() {}

    /** Optional CPU snapshot for the radar, independent of the world overlay or camera. */
    public static List<FarContacts.Contact> radarContacts(ClientLevel level) {
        if(level==null||owner!=level||ticksUntilExpiry<=0)return List.of();
        integratedRadar=true;
        return contacts;
    }

    public static void install() {
        FarContacts.receiver = FarContactLayers::receive;
        HoloLayerApi.registerWorld("sablelightoverhaul:far_contacts", FarContactLayers::world);
        HoloLayerApi.registerDisplay("sablelightoverhaul:far_contacts_radar", HoloLayerApi.DisplayStage.AFTER_TERRAIN,
            FarContactLayers::display);
        NeoForge.EVENT_BUS.addListener(FarContactLayers::tick);
        FarContactPortraits.install();
    }

    private static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (owner != mc.level) {
            owner = mc.level;
            contacts = List.of();
            ticksUntilRequest = 0;
        }
        if (owner == null || mc.hasSingleplayerServer() || mc.getConnection() == null) return;
        if (ticksUntilExpiry > 0 && --ticksUntilExpiry == 0) contacts = List.of();
        if (ticksUntilRequest > 0) ticksUntilRequest--;
        int range = ExtraConfig.FAR_CONTACT_RANGE.get();
        if (range <= mc.options.renderDistance().get()) {
            contacts = List.of();
            return;
        }
        if (ticksUntilRequest == 0 && mc.getConnection().hasChannel(FarContacts.Request.TYPE)) {
            ticksUntilRequest = 10;
            ClientChannels.sendToServer(new FarContacts.Request(range));
        }
    }

    private static void receive(FarContacts.Snapshot snapshot) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (owner != null && owner == level && level.dimension().location().equals(snapshot.dimension())
                && ExtraConfig.FAR_CONTACT_RANGE.get() > mc.options.renderDistance().get()) {
            contacts = snapshot.contacts();
            ticksUntilExpiry = 30;
        }
    }

    private static void display(HoloFrame frame) {
        if (owner == null || frame.level != owner || contacts.isEmpty()) return;
        var map = frame.display().orElseThrow();
        if (!WolfMiscConfig.CLIENT.holoEntities.get()) return;
        var displayPos = BlockPos.of(map.key());
        var dimension = owner.dimension();
        if (HoloRadarApi.jammed(dimension, displayPos, frame.nowMillis)) return;
        var surface = new HoloSurfaceId(dimension.location().toString(), map.key());
        var mesh = HoloMesh.builder();
        for (var contact : contacts) {
            if(integratedRadar&&contact.uuid()!=null)continue;
            Vec3 p = contact.position();
            if (!map.contains(p.x, p.z) || owner.getEntity(contact.id()) != null) continue;
            if (HoloRadarApi.decoyed(dimension, displayPos, contact.id(), frame.nowMillis)
                    || HoloRadarApi.blanked(dimension, p.x, p.y, p.z, frame.nowMillis)) continue;
            double half = contact.width() * .5;
            var bounds = new SelectionBounds(p.x - half, p.y, p.z - half, p.x + half, p.y + contact.height(), p.z + half);
            if (HoloVisibilityApi.hidden(surface, InformationMaskV1.Category.ENTITY, bounds, frame.nowMillis)
                    || frame.occluded(map.anchorX(), map.anchorY(), map.anchorZ(), p.x, p.y + contact.height() * .5, p.z)) continue;
            var point = frame.project(p.x, p.y, p.z);
            Vec3 center = new Vec3(point.x() - map.anchorX(), point.y() - map.anchorY() + .015,
                point.z() - map.anchorZ());
            double r = .018;
            quad(mesh, center.add(-r,0,0), center.add(0,0,r), center.add(r,0,0), center.add(0,0,-r), contact.color());
        }
        frame.mesh(mesh.build(), HoloPlacementV1.at(map.anchorX(),map.anchorY(),map.anchorZ(),HoloUnits.DISPLAY_UNITS));
    }

    private static void world(HoloFrame frame) {
        FarContactPortraits.beginFrame();
        if (owner == null || frame.level != owner || contacts.isEmpty()) return;
        var eye = new Vec3(frame.view.x(), frame.view.y(), frame.view.z());
        var mc = Minecraft.getInstance();
        var renderer = mc.gameRenderer;
        Camera camera = renderer.getMainCamera();
        var look = camera.getLookVector();
        double projectionLimit = renderer.getDepthFar() * .9;
        if (projectionLimit <= 0) return;
        double focusAngle = ExtraConfig.FAR_CONTACT_FOCUS_ANGLE.get();
        double focusCosine = Math.cos(Math.toRadians(focusAngle));
        int focusSize = ExtraConfig.FAR_CONTACT_FOCUS_SIZE.get();
        boolean showHealth = ExtraConfig.FAR_CONTACT_HEALTH.get();
        var mesh = HoloMesh.builder();
        for (var contact : contacts) {
            Vec3 p = contact.position();
            if (owner.getEntity(contact.id()) != null) continue;
            Vec3 mid = p.add(0, contact.height() * .5, 0);
            Vec3 offset = mid.subtract(eye);
            double depth = offset.x * look.x + offset.y * look.y + offset.z * look.z;
            if (depth <= 0 || VoxyAccess.blocked(eye, mid, contact.id())) continue;
            double distance = offset.length();
            // Scale around the eye so the screen projection stays put on very short client view distances.
            // The LOD ray above still runs to the actual contact.
            double scale = distance > projectionLimit ? projectionLimit / distance : 1;
            Vec3 renderCenter = eye.add(offset.scale(scale));
            double size = Math.max(1, distance * .008);
            double width = Math.max(contact.width(), size) * scale;
            double height = Math.max(contact.height(), size) * scale;
            Vec3 axis = new Vec3(eye.z-p.z, 0, p.x-eye.x).normalize();
            if (axis.lengthSqr() == 0) axis = new Vec3(1,0,0);
            Vec3 origin = renderCenter.add(axis.scale(-width*.5)).add(0,-height*.5,0);
            double stroke = size * scale * .065;
            strip(mesh, origin, axis, 0,0,width,stroke,contact.color());
            strip(mesh, origin, axis, 0,height-stroke,width,height,contact.color());
            strip(mesh, origin, axis, 0,stroke,stroke,height-stroke,contact.color());
            strip(mesh, origin, axis, width-stroke,stroke,width,height-stroke,contact.color());
            String health = showHealth ? FarContactPortraits.healthText(contact.health()) : null;
            if (focusAngle > 0 && FarContactPortraits.inFocus(depth, distance, focusCosine)) {
                FarContactPortraits.focus(contact.type(), renderCenter.subtract(eye), axis, width, height,
                    contact.color(), focusSize, health);
                continue;
            }
            Vec3 top = renderCenter.add(0, height * .5 + size * scale * .14, 0);
            double textHeight = size * scale * .30;
            double portraitLift = health == null ? 0 : textHeight * 1.3;
            boolean portrait = FarContactPortraits.add(contact.type(), top.subtract(eye).add(0, portraitLift, 0),
                axis, size * scale * .72);
            String text = portrait ? health : FarContactPortraits.nameWithHealth(contact.type(), health);
            if (text == null) continue;
            double textWidth = (mc.font.width(text) + 4) * textHeight / 9;
            Vec3 plate = top.add(offset.scale(.02 * scale / distance)).add(axis.scale(-textWidth * .5));
            strip(mesh, plate, axis, 0, -textHeight * .1, textWidth, textHeight * 1.12, 0x18212A);
            frame.label(new HoloLabel(text, top.x, top.y + textHeight, top.z, contact.color(), 1,
                textHeight, HoloFacing.UPRIGHT_BILLBOARD, 0));
        }
        frame.mesh(mesh.build(), HoloPlacementV1.world());
    }

    private static void strip(HoloMesh.Builder mesh, Vec3 origin, Vec3 axis, double x0,double y0,double x1,double y1,int color) {
        Vec3 left = origin.add(axis.scale(x0));
        Vec3 right = origin.add(axis.scale(x1));
        quad(mesh,left.add(0,y0,0),right.add(0,y0,0),right.add(0,y1,0),left.add(0,y1,0),color);
    }

    private static void quad(HoloMesh.Builder mesh, Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color) {
        mesh.quad(vertex(a,color),vertex(b,color),vertex(c,color),vertex(d,color));
    }

    private static HoloMesh.Vertex vertex(Vec3 p,int color) {
        return new HoloMesh.Vertex(p.x,p.y,p.z,(color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,1);
    }
}
