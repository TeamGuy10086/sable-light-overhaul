package dev.sablelightoverhaul.lighting;

import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.sablelightoverhaul.LocalizedText;
import dev.sablelightoverhaul.ProfileEditPayload;
import dev.sablelightoverhaul.SableLightOverhaul;
import dev.sablelightoverhaul.api.EffectApi;
import dev.sablelightoverhaul.client.ProfileCache;
import dev.sablelightoverhaul.client.ProfileKeys;
import dev.sablelightoverhaul.client.ui.ConfigTaxonomy;
import dev.sablelightoverhaul.client.ui.DeviceSource;
import dev.sablelightoverhaul.client.ui.FieldSpec;
import dev.sablelightoverhaul.client.ui.FieldValue;
import dev.sablelightoverhaul.client.ui.Source;
import dev.sablelightoverhaul.profiles.ProfileCatalogue;
import dev.sablelightoverhaul.profiles.ProfileKinds;
import dev.sablelightoverhaul.profiles.ProfileText;
import dev.sablelightoverhaul.profiles.ProfileWire;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A cannon's form, as a page: a kind at the top, a system beside it, and the values underneath.
 *
 * <p><b>The shape follows the data now.</b> A form is filed under the kind of shot, and inside that under a
 * system -- the muzzle fireball, the ground dust, the muzzle light. The same system appears under
 * several kinds on purpose: thin smoke is thin smoke, but each kind keeps its own values for it, so each
 * kind's page has to offer it.
 *
 * <p><b>The page is a view over the server's table, which is why tabs are built on demand.</b> Values live on
 * the server and the client holds a copy, so rebuilding a tab cannot lose anything, and the page does not have
 * to keep every system of every kind alive at once -- which at a hundred-odd rows is worth a great deal.
 *
 * <p>Each row shows the value in force: the cannon's own where it has one, the preset otherwise. For a system
 * the server owns the preset arrived in the table; for a system the client owns it is this client's own
 * configuration, which is the split the fourteen light slots have always used.
 */
public final class ProfileLdlib {
    private static final float WIDTH = 242, ROW = 11, GAP = 2, LABEL = 112, CONTROL = 118;
    /** Systems already reported, so the page says what it resolved once each rather than on every rebuild. */
    private static final java.util.Set<String> REPORTED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /**
     * What the page is showing, for the two buttons that act on a whole page.
     *
     * <p>Set by {@code fillBody} and read by the buttons, because a button is built once with the page and the
     * page's contents change under it. Making them read the page's own record is what lets one pair of buttons
     * serve every system without holding a second copy of anything.
     */
    private static Holder pageHolder;
    private static ResourceLocation pageKind;
    private static ProfileCatalogue.System pageSystem;
    private static List<String> pagePaths = List.of();
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, SableLightOverhaul.ID);
    public static final DeferredHolder<MenuType<?>, MenuType<ModularUIContainerMenu>> MENU =
            MENUS.register("profile", ProfileLdlib::menuType);

    private ProfileLdlib() {}

    private static MenuType<ModularUIContainerMenu> menuType() {
        return IMenuTypeExtension.create((id, inventory, data) ->
                new ModularUIContainerMenu(MENU.get(), id, inventory, Holder.read(inventory.player, data)));
    }

    public static void register(IEventBus bus) { MENUS.register(bus); }

    /**
     * Opens the page for one cannon.
     *
     * <p>Only the cannon's identity and the type of block it sits on travel with the menu: the values are
     * already on the client, having arrived with the table, so the menu does not carry a second copy of them
     * and cannot go stale by carrying an old one.
     */
    public static boolean open(ServerPlayer player, EffectApi.SourceKey key, ResourceLocation mountType) {
        if (player == null || key == null) return false;
        player.openMenu(new Holder(player, key, mountType), buffer -> {
            ProfileWire.SOURCE_KEY.encode(buffer, key);
            buffer.writeResourceLocation(mountType == null ? key.typeId() : mountType);
        });
        return true;
    }

    /** Menu provider and page holder in one, so the server menu and the client page agree. */
    record Holder(Player player, EffectApi.SourceKey key, ResourceLocation mountType)
            implements MenuProvider, IContainerUIHolder {
        static Holder read(Player player, RegistryFriendlyByteBuf buffer) {
            return new Holder(player, ProfileWire.SOURCE_KEY.decode(buffer), buffer.readResourceLocation());
        }

        @Override public boolean isStillValid(Player owner) { return true; }

        @Override public Component getDisplayName() {
            return Component.translatable("sablelightoverhaul.profile.title", mountLabel(mountType));
        }

        @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player owner) {
            return new ModularUIContainerMenu(MENU.get(), id, inventory, this);
        }

        @Override public ModularUI createUI(Player owner) {
            return ModularUI.of(UI.of(new UIElement()), owner);
        }
    }

    /** What a player calls the block the cannon is mounted on. */
    private static String mountLabel(ResourceLocation mountType) {
        return mountType == null ? "cannon" : mountType.toString();
    }

    /** What a player calls a kind, in their own language. */
    private static String kindLabel(EffectApi.WeaponKind kind) {
        return Component.translatable("sablelightoverhaul.profile.kind." + kind.name()).getString();
    }

    /**
     * What a row is called: the language file where it has a name for this leaf, a word for the path's last
     * segment otherwise, and the raw tail as a last resort.
     *
     * <p>The dictionary exists because a page can now show any key, and writing a name per key by hand is a
     * list that goes stale the first time a key is added. A segment word covers every key that ends in it --
     * every system has a count, a size, a speed -- which is both shorter to keep and honest about what a row
     * is: the system above it already says what the number belongs to.
     */
    private static Component rowLabel(String path) {
        String leaf = "sablelightoverhaul.profile.leaf." + path;
        String translated = Component.translatable(leaf).getString();
        if (!translated.equals(leaf)) return Component.translatable(leaf);
        int cut = path.lastIndexOf('.');
        String tail = cut < 0 ? path : path.substring(cut + 1);
        String word = SEGMENTS.get(tail);
        return word != null ? LocalizedText.component(word) : Component.literal(tail);
    }

    /** What a path's last segment is called in a row. A missing entry falls back to the segment itself. */
    private static final Map<String, String> SEGMENTS = Map.ofEntries(
            Map.entry("enabled", "开关"), Map.entry("count", "数量"), Map.entry("size", "尺寸"),
            Map.entry("sizeMin", "最小尺寸"), Map.entry("sizeMax", "最大尺寸"),
            Map.entry("stretch", "拉伸"), Map.entry("stretchMin", "最小拉伸"), Map.entry("stretchMax", "最大拉伸"),
            Map.entry("speed", "速度"), Map.entry("speedScale", "速度倍率"), Map.entry("velocity", "后坐速度"),
            Map.entry("radius", "半径"), Map.entry("lifetime", "寿命"), Map.entry("color", "颜色"),
            Map.entry("alpha", "不透明度"), Map.entry("scatter", "散布"), Map.entry("damping", "阻尼"),
            Map.entry("gravity", "重力"), Map.entry("searchDistance", "搜索距离"),
            Map.entry("tintFromBlock", "按方块取色"), Map.entry("physicsSteps", "物理步数"),
            Map.entry("fixPositionAndSpeed", "固定位置与速度"), Map.entry("particlesPerShot", "每炮粒子预算"),
            Map.entry("cullDistance", "发送距离"), Map.entry("plumeScale", "白烟倍率"),
            Map.entry("length", "长度"), Map.entry("coneAngle", "张角"),
            Map.entry("hazeInterval", "补烟间隔"), Map.entry("hazeCount", "补烟数量"),
            Map.entry("hazeLifetime", "烟寿命"), Map.entry("hazeAlpha", "烟不透明度"),
            Map.entry("hazeSize", "烟尺寸"), Map.entry("muzzleSpread", "外扩速度"),
            Map.entry("muzzleDrift", "外扩上限"), Map.entry("muzzleSink", "下沉加速度"),
            Map.entry("muzzleClearance", "离地保护"), Map.entry("muzzlePush", "推力"),
            Map.entry("muzzlePushRadius", "推烟半径"), Map.entry("lightGain", "受光倍率"),
            Map.entry("forwardRange", "前向长度"), Map.entry("rearRange", "后向长度"),
            Map.entry("sideRange", "侧向宽度"), Map.entry("verticalRange", "垂直高度"),
            Map.entry("rearClipDistance", "后向裁切"), Map.entry("coreFraction", "核心占比"),
            Map.entry("middleFraction", "中段起点"), Map.entry("tailBrightness", "尾段亮度"),
            Map.entry("durationTicks", "持续刻数"), Map.entry("peakHoldTicks", "峰值保持"),
            Map.entry("minimumBrightness", "最低亮度"), Map.entry("decayPerTick", "每刻衰减"),
            Map.entry("referenceSize", "参考尺寸"), Map.entry("maximumRange", "最大射程"),
            Map.entry("lightDurationTicks", "光时长"));

    private static UI page(Holder holder) {
        UIElement root = new UIElement().layout(layout -> {
            layout.width(WIDTH * 2 + GAP * 4);
            layout.paddingAll(4.0F);
            layout.gapAll(GAP);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.FLEX_START);
        });
        root.addChild(title(Component.translatable("sablelightoverhaul.profile.title",
                mountLabel(holder.mountType())), WIDTH - 8));
        root.addChild(title(Component.translatable("sablelightoverhaul.profile.hint"), WIDTH - 8));

        var kinds = ProfileKinds.all();
        var kindLabels = kinds.stream().map(ProfileLdlib::kindLabel).toList();
        var kindSelector = new Selector<String>();
        kindSelector.layout(layout -> { layout.width(CONTROL); layout.height(10.0F); });
        kindSelector.setCandidates(new ArrayList<>(kindLabels));

        var systemSelector = new Selector<String>();
        systemSelector.layout(layout -> { layout.width(CONTROL); layout.height(10.0F); });

        // Two columns by wrapping, not by splitting the loop: the container is two columns wide and the rows
        // wrap into it, so a system with twenty keys reads as two short columns and a system with three keys
        // stays one column. The row recipe itself is untouched -- same 242/11/2/112/118 the light and
        // flashlight pages use -- which is the point: the difference was never the rows, it was the container.
        UIElement body = new UIElement().layout(layout -> {
            layout.width(WIDTH * 2);
            layout.gapAll(GAP * 3);
            layout.flexDirection(FlexDirection.ROW);
            layout.flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP);
        });

        int start = startKind(holder, kinds);
        kindSelector.setSelected(kindLabels.get(start), false);
        kindSelector.setOnValueChanged(chosen -> {
            int picked = kindLabels.indexOf(chosen);
            if (picked < 0) return;
            dev.sablelightoverhaul.client.ProfileViewMemory.remember(holder.key(), kinds.get(picked));
            fillTabs(holder, kinds.get(picked), systemSelector, body);
        });
        systemSelector.setOnValueChanged(chosen ->
                fillBody(holder, currentKind(kinds, kindLabels, kindSelector), chosen, body));

        root.addChild(kindSelector);
        root.addChild(systemSelector);
        root.addChild(body);
        root.addChild(bar(holder));
        fillTabs(holder, kinds.get(start), systemSelector, body);
        return UI.of(root);
    }

    /** Which kind the top selector is showing. The selector's own value is the only source of truth here. */
    private static EffectApi.WeaponKind currentKind(List<EffectApi.WeaponKind> kinds, List<String> labels,
                                                    Selector<String> selector) {
        Object chosen = selector.getValue();
        int at = chosen == null ? -1 : labels.indexOf(chosen.toString());
        return at < 0 ? kinds.get(0) : kinds.get(at);
    }

    /** The kind this cannon's page was last left on, or the first one. */
    private static int startKind(Holder holder, List<EffectApi.WeaponKind> kinds) {
        var remembered = dev.sablelightoverhaul.client.ProfileViewMemory.lastFor(holder.key()).orElse(null);
        int at = remembered == null ? -1 : kinds.indexOf(remembered);
        return at < 0 ? 0 : at;
    }

    /** Fills the tab selector for a kind and shows its first system. */
    private static void fillTabs(Holder holder, EffectApi.WeaponKind kind, Selector<String> tabs, UIElement body) {
        var systems = ProfileCatalogue.systemsFor(kind);
        var labels = systems.stream().map(ProfileCatalogue.System::label).toList();
        tabs.setCandidates(new ArrayList<>(labels));
        tabs.setCandidateUIProvider(com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider.text(LocalizedText::component));
        body.clearAllChildren();
        if (labels.isEmpty()) return;
        tabs.setSelected(labels.get(0), false);
        fillBody(holder, kind, labels.get(0), body);
    }

    /** Rebuilds the rows for one system. Safe on every switch: no value is held here. */
    private static void fillBody(Holder holder, EffectApi.WeaponKind kind, String systemLabel, UIElement body) {
        body.clearAllChildren();
        ProfileCatalogue.System system = null;
        for (var candidate : ProfileCatalogue.systemsFor(kind)) {
            if (candidate.label().equals(systemLabel)) { system = candidate; break; }
        }
        if (system == null) return;
        // Recorded for the page buttons: "pin this page" and "restore this page" both act on exactly what is
        // on screen, and this is the only place that knows it. The rows themselves hold no state.
        pageHolder = holder;
        pageKind = ProfileKinds.id(kind);
        pageSystem = system;
        pagePaths = List.copyOf(ProfileKeys.keysFor(system));
        var kindId = pageKind;
        var stored = ProfileCache.storedAll(holder.key(), kindId);
        var paths = pagePaths;
// The side is spelled out on every system, because two of them carry the same name on purpose: the server
        // holds a preset for the kind while the client holds the value the effect actually reads, and only the
        // label tells an owner which of the two they are editing.
        Label counter = label(LocalizedText.component("%s · %s · %s%s%s", kindLabel(kind), LocalizedText.component(systemLabel),
                paths.size(),
                paths.isEmpty() ? LocalizedText.component("  (此系统暂无可用键)") : LocalizedText.component(" 键"),
                system.side() == ProfileCatalogue.Side.CLIENT
                        ? LocalizedText.component("   【客户端】") : LocalizedText.component("   【服务端】")),
                WIDTH - 8);
        body.addChild(counter);
        if (REPORTED.add(system.label())) {
            // One line per system, once: an empty page is otherwise impossible to tell apart from a page whose
            // rows were never built, and those are different bugs with different answers.
            com.mojang.logging.LogUtils.getLogger().info(
                    "Sable Light Overhaul: page system {} resolved {} key(s)", system.label(), paths.size());
        }
        for (var path : paths) {
            try {
                Double own = stored.get(path);
                double shown = own != null ? own : ProfileKeys.preset(system, path);
                body.addChild(row(holder, kindId, path, shown, own != null));
            } catch (RuntimeException oneRowFailed) {
                // A row that cannot be built must not take the whole page with it, and must not do it quietly.
                com.mojang.logging.LogUtils.getLogger().warn(
                        "Sable Light Overhaul: could not build the row for {} ({})", path, oneRowFailed.toString());
            }
        }
    }

    private static UIElement row(Holder holder, ResourceLocation kindId, String path, double value, boolean overridden) {
        UIElement row = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(GAP);
            layout.height(ROW);
            layout.alignItems(AlignItems.CENTER);
        });
        // An overridden row is shown a little warmer than one still on its preset, so a glance says which
        // values this cannon actually decided.
        Label name = label(rowLabel(path), LABEL);
        if (overridden) name.textStyle(style -> style.textColor(0xFFFFC0).textShadow(false).fontSize(8.0F));
        row.addChildren(name, number(holder, kindId, path, value));
        // Only a row that holds its own value can be put back on the preset. A row already on the preset has
        // nothing to forget, and a button that did nothing would be worse than no button at all.
        if (overridden) row.addChild(button("↺", () -> {
            PacketDistributor.sendToServer(new dev.sablelightoverhaul.ProfileClearPayload(
                    holder.key(), kindId, List.of(path)));
            tell(Component.translatable("sablelightoverhaul.profile.restoredOne", rowLabel(path)));
        }));
        return row;
    }

    private static TextField number(Holder holder, ResourceLocation kindId, String path, double value) {
        TextField input = new TextField();
        input.style(style -> style.backgroundTexture(Sprites.RECT_DARK));
        input.textFieldStyle(style -> style.textColor(0xFFFFFF).cursorColor(0xFFFFFF).textShadow(false).fontSize(8.0F));
        input.layout(layout -> {
            layout.width(CONTROL);
            layout.height(10.0F);
            layout.paddingHorizontal(2.0F);
            layout.paddingVertical(1.0F);
        });
        // Wide enough for every key a form can hold. The old page had per-leaf ranges because it only ever
        // showed fourteen known leaves; a page that can show any key does not know its bounds, and inventing
        // them per key is how a value the configuration allows would be silently clamped here.
        input.setNumbersOnlyDouble(-1.0e9, 1.0e9);
        input.setText(format(value), false);
        input.setWheelDur(1, 0.1F);
        input.setTextResponder(text -> {
            try {
                send(holder.player(), new ProfileEditPayload(holder.key(), kindId, path, Double.parseDouble(text)));
            } catch (NumberFormatException notYet) {
                // Typing is not an edit until it parses.
            }
        });
        return input;
    }

    /**
     * Copy the form out, or paste one in.
     *
     * <p>The export carries every value the client holds for this cannon, over every kind, because the
     * clipboard is the only place a form leaves the server's table, and exporting one kind would silently lose
     * the rest.
     */
    private static UIElement bar(Holder holder) {
        UIElement bar = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(GAP);
            layout.height(ROW);
            layout.alignItems(AlignItems.CENTER);
        });
        bar.addChildren(
            // Pin: write every value this page is showing into this cannon's own record. What travels is the
            // displayed value -- an override where the cannon has one, the kind's preset where it has not --
            // because "keep what I am looking at" is the request, and the server stores what it is sent. This
            // reuses the pasted-form payload: writing a set of values for a cannon and a kind is the same
            // operation, whatever the values came from.
            button(LocalizedText.text("固定当前"), () -> {
                if (pageHolder == null || pageKind == null) return;
                var stored = ProfileCache.storedAll(pageHolder.key(), pageKind);
                var values = new LinkedHashMap<String, Double>();
                for (var path : pagePaths) {
                    Double own = stored.get(path);
                    values.put(path, own != null ? own : ProfileKeys.preset(pageSystem, path));
                }
                if (values.isEmpty()) {
                    tell(LocalizedText.component("本页没有可固定的键"));
                    return;
                }
                PacketDistributor.sendToServer(new dev.sablelightoverhaul.ProfileImportPayload(
                        pageHolder.key(), Map.of(pageKind.toString(), values)));
                tell(LocalizedText.component("已固定本页 %s 项 —— 此后不再随炮种预设变化", values.size()));
            }),
            // Restore: forget them instead, which is what puts the rows back on the preset. A cannon has no
            // defaults of its own, so restoring is removing the record rather than writing one.
            button(LocalizedText.text("还原本页"), () -> {
                if (pageHolder == null || pageKind == null || pagePaths.isEmpty()) return;
                PacketDistributor.sendToServer(new dev.sablelightoverhaul.ProfileClearPayload(
                        pageHolder.key(), pageKind, pagePaths));
                tell(LocalizedText.component("已还原本页 %s 项 —— 回落到炮种预设", pagePaths.size()));
            }),
            button(Component.translatable("sablelightoverhaul.profile.export").getString(), () -> {
                // The clipboard format carries text, so the numbers are written the way the format wants them
                // rather than handed over as doubles and converted somewhere the reader cannot see.
                var byType = new LinkedHashMap<String, Map<String, String>>();
                for (var kind : ProfileKinds.all()) {
                    var values = ProfileCache.storedAll(holder.key(), ProfileKinds.id(kind));
                    if (values.isEmpty()) continue;
                    var asText = new LinkedHashMap<String, String>();
                    values.forEach((name, value) -> asText.put(name, String.valueOf(value)));
                    byType.put(ProfileKinds.id(kind).toString(), asText);
                }
                var text = ProfileText.writeText(new ProfileText.TextExport("cannon", modVersion(), byType));
                Minecraft.getInstance().keyboardHandler.setClipboard(text);
                tell(Component.translatable("sablelightoverhaul.profile.exported", text.length()));
            }),
            button(Component.translatable("sablelightoverhaul.profile.import").getString(), () -> {
                var parsed = ProfileText.readText(Minecraft.getInstance().keyboardHandler.getClipboard());
                if (parsed.isEmpty()) {
                    tell(Component.translatable("sablelightoverhaul.profile.nothingToRead"));
                    return;
                }
                var export = parsed.get();
                // The clipboard carries text; the payload carries numbers. A line whose value is not a number
                // is dropped here rather than sent as a zero, which would look like a deliberate setting.
                var byType = new LinkedHashMap<String, Map<String, Double>>();
                export.byType().forEach((type, values) -> {
                    var numbers = new LinkedHashMap<String, Double>();
                    values.forEach((name, value) -> {
                        try {
                            numbers.put(name, Double.parseDouble(value));
                        } catch (NumberFormatException notANumber) {
                            // Reported by ProfileText's own line count, and not sent.
                        }
                    });
                    if (!numbers.isEmpty()) byType.put(type, numbers);
                });
                PacketDistributor.sendToServer(new dev.sablelightoverhaul.ProfileImportPayload(holder.key(), byType));
                tell(Component.translatable("sablelightoverhaul.profile.imported", byType.size(),
                        export.modVersion(), export.unknownLines().size()));
            }));
        return bar;
    }

    private static Button button(String label, Runnable action) {
        Button button = new Button();
        button.setText(label);
        button.layout(layout -> {
            layout.width(96.0F);
            layout.height(12.0F);
        });
        button.setOnClick(event -> action.run());
        return button;
    }

    private static Label label(Component text, float width) {
        Label label = new Label();
        label.setText(text);
        label.textStyle(style -> style.textColor(0xFFFFFF).textShadow(false).fontSize(8.0F));
        label.layout(layout -> {
            layout.width(width);
            layout.height(10.0F);
        });
        return label;
    }

    private static Label title(Component text, float width) {
        Label label = new Label();
        label.setText(text);
        label.textStyle(style -> style.textColor(0xFFFFFF).textShadow(false).fontSize(8.0F));
        label.layout(layout -> {
            layout.width(width);
            layout.height(12.0F);
        });
        return label;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** One line in chat, which is where a page says what it just did. */
    private static void tell(Component message) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal("Sable Light Overhaul: ").append(message), false);
        }
    }

    /** The build that wrote an export, so an import from an older one can be recognised as older. */
    private static String modVersion() {
        return net.neoforged.fml.ModList.get().getModContainerById(SableLightOverhaul.ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    /** Edits only originate from a player using the page, which is always the client side. */
    private static void send(Player owner, ProfileEditPayload payload) {
        if (owner.level().isClientSide) PacketDistributor.sendToServer(payload);
    }


    /** Shared F9 editor; keys include the weapon kind so equal paths never alias across tabs. */
    public static Source configSource(ModularUIContainerMenu menu) {
        Holder holder = (Holder)menu.uiHolder;
        var metadata = new LinkedHashMap<String, FieldSpec>();
        for (var config : List.of(dev.sablelightoverhaul.client.MuzzleClientConfig.SPEC, LightingCommon.SPEC))
            for (var group : new dev.sablelightoverhaul.client.ui.TomlSource(config, java.util.Set.of()).groups())
                for (var field : group.fields()) metadata.put(field.key(), field);
        var resets = new LinkedHashMap<String, Runnable>();
        var pins = new LinkedHashMap<String, Runnable>();
        var clears = new LinkedHashMap<String, Runnable>();
        String[] selected = {""};
        DeviceSource source = new DeviceSource(holder.getDisplayName().getString()) {
            @Override public void selectCategory(String category) { selected[0] = category; }
            @Override public boolean canReset(FieldSpec spec) { return resets.containsKey(spec.key()); }
            @Override public void reset(FieldSpec spec) { var reset = resets.get(spec.key()); if (reset != null) reset.run(); }
        };
        for (var kind : ProfileKinds.all()) {
            var kindId = ProfileKinds.id(kind);
            String category = kindLabel(kind);
            var kindPaths = new LinkedHashMap<String, ProfileCatalogue.System>();
            for (var system : ProfileCatalogue.systemsFor(kind)) {
                var fields = new ArrayList<FieldSpec>();
                for (String path : ProfileKeys.keysFor(system)) {
                    if(kindPaths.putIfAbsent(path, system) != null) continue;
                    String key = kindId + "|" + path;
                    FieldSpec base = metadata.get(path);
                    var shape = base != null && base.kind() == FieldSpec.Kind.TOGGLE ? FieldSpec.Kind.TOGGLE : FieldSpec.Kind.SLIDER;
                    FieldSpec field = new FieldSpec(key, system.label(), rowLabel(path).getString(),
                            base == null ? path : base.tooltip(), shape,
                            base == null ? -1e9 : base.min(), base == null ? 1e9 : base.max(),
                            base == null ? 0 : base.step(), List.of(), false, null);
                    fields.add(field);
                    source.bind(field, () -> {
                        double n = ProfileCache.storedAll(holder.key(), kindId).getOrDefault(path, ProfileKeys.preset(system,path));
                        return shape == FieldSpec.Kind.TOGGLE ? new FieldValue.Bool(n != 0) : new FieldValue.Num(n);
                    }, edit -> send(holder.player(),new ProfileEditPayload(holder.key(),kindId,path,
                            edit instanceof FieldValue.Bool b ? b.on() ? 1 : 0 : FieldValue.numeric(edit))));
                    resets.put(key, () -> PacketDistributor.sendToServer(new dev.sablelightoverhaul.ProfileClearPayload(holder.key(),kindId,List.of(path))));
                }
                source.group(category, system.label(), system.side() == ProfileCatalogue.Side.CLIENT
                        ? LocalizedText.text("客户端") : LocalizedText.text("服务端"), fields);
            }
            pins.put(category, () -> {
                var values = new LinkedHashMap<String,Double>();
                var stored = ProfileCache.storedAll(holder.key(),kindId);
                kindPaths.forEach((path,system)->values.put(path,stored.getOrDefault(path,ProfileKeys.preset(system,path))));
                if(!values.isEmpty())PacketDistributor.sendToServer(new dev.sablelightoverhaul.ProfileImportPayload(holder.key(),Map.of(kindId.toString(),values)));
            });
            clears.put(category, () -> PacketDistributor.sendToServer(new dev.sablelightoverhaul.ProfileClearPayload(holder.key(),kindId,List.copyOf(kindPaths.keySet()))));
        }
        source.action(LocalizedText.text("固定当前炮种"), () -> { var r=pins.get(selected[0]);if(r!=null)r.run(); });
        source.action(LocalizedText.text("还原当前炮种"), () -> { var r=clears.get(selected[0]);if(r!=null)r.run(); });
        source.action(Component.translatable("sablelightoverhaul.profile.export").getString(), () -> exportProfile(holder));
        source.action(Component.translatable("sablelightoverhaul.profile.import").getString(), () -> importProfile(holder));
        return source;
    }
    private static void exportProfile(Holder holder) {
        var byType = new LinkedHashMap<String,Map<String,String>>();
        for(var kind:ProfileKinds.all()){
            var values=new LinkedHashMap<String,String>();
            ProfileCache.storedAll(holder.key(),ProfileKinds.id(kind)).forEach((k,v)->values.put(k,v.toString()));
            if(!values.isEmpty())byType.put(ProfileKinds.id(kind).toString(),values);
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(ProfileText.writeText(new ProfileText.TextExport("cannon",modVersion(),byType)));
    }
    private static void importProfile(Holder holder) {
        var parsed=ProfileText.readText(Minecraft.getInstance().keyboardHandler.getClipboard());
        if(parsed.isEmpty()){tell(Component.translatable("sablelightoverhaul.profile.nothingToRead"));return;}
        var byType=new LinkedHashMap<String,Map<String,Double>>();
        parsed.get().byType().forEach((type,values)->{
            var numbers=new LinkedHashMap<String,Double>();
            values.forEach((key,text)->{try{double n=Double.parseDouble(text);if(Double.isFinite(n))numbers.put(key,n);}catch(NumberFormatException ignored){}});
            if(!numbers.isEmpty())byType.put(type,numbers);
        });
        if(!byType.isEmpty())PacketDistributor.sendToServer(new dev.sablelightoverhaul.ProfileImportPayload(holder.key(),byType));
    }
}
