package dev.sablelightoverhaul.client.ui;

import dev.sablelightoverhaul.LocalizedText;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The config screen, as a window the player can push out of the way.
 *
 * <p><b>Three levels, one page at a time.</b> The left column is the ten categories from
 * {@link ConfigTaxonomy}; the page shows that category's sections and topics, with a search box for when the
 * player remembers a key's name but not its page. The rows are still built from {@link Source}, so the same
 * screen can later drive a block's page without knowing where its values live.
 *
 * <p><b>Two controls for a number.</b> A numeric row gets a draggable bar and a text box. The bar is quick to
 * sweep, the box is how an exact value is typed; neither alone covers both. The bar writes while it is dragged
 * and the file is saved once when the drag ends, so dragging does not rewrite the toml on every pixel.
 *
 * <p><b>Read-only is a property of the source.</b> A page whose owner is not the player, or whose server says
 * no, is the same tree with its controls disabled; the window never checks permissions itself.
 */
public class ConfigWindow extends ModularUIScreen {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final float WIN_W = 460.0F;
    private static final float NAV_W = 92.0F;
    private static final float ROW_H = 12.0F;


    private final Source source;
    private final List<Source.Group> allGroups;
    private final Map<String, FieldValue> values;
    private final UIElement window;
    private final UIElement nav;
    private final ScrollerView view;
    private final TextField search;
    private final float winW;
    private final float winH;
    private final float labelW;
    private final float controlW;
    private String category;
    private String query = "";
    private float winX = 12.0F;
    private float winY = 36.0F;
    private boolean dragging;
    private double dragFromX;
    private double dragFromY;
    private float dragOriginX;
    private float dragOriginY;
    private final Set<String> collapsedSections = new HashSet<>();
    private final Set<String> collapsedTopics = new HashSet<>();
    private boolean collapseInitialised;
    private boolean contentRebuildPending;
    private int refreshCountdown;
    private ValueSlider draggingSlider;
    private final List<ValueSlider> sliders = new ArrayList<>();
    /** Every cycler on the current page; an open one owns the click, wherever its popup is drawn. */
    private final List<Selector<?>> selectors = new ArrayList<>();

    public ConfigWindow(Source source) {
        this(source, build(source));
    }

    private ConfigWindow(Source source, Shell shell) {
        super(ModularUI.of(shell.ui()), Component.translatable("sablelightoverhaul.config.title"));
        this.source = source;
        this.allGroups = source.groups();
        this.values = new HashMap<>(source.read());
        this.window = shell.window();
        this.nav = shell.nav();
        this.view = shell.view();
        this.search = shell.search();
        this.winW = shell.width();
        this.winH = shell.height();
        float contentW = winW - NAV_W - 16.0F;
        this.labelW = Math.max(90.0F, Math.min(140.0F, contentW * 0.38F));
        this.controlW = Math.max(120.0F, contentW - labelW - 4.0F);
        this.category = firstCategory();
        source.selectCategory(category);
        this.search.setTextResponder(text -> {
            query = text == null ? "" : text.trim().toLowerCase(java.util.Locale.ROOT);
            rebuildContent();
        });
        this.moveTo(winX, winY);
        rebuildNav();
        rebuildContent();
    }

    // ------------------------------------------------------------------ building

    private record Shell(UI ui, UIElement window, UIElement nav, ScrollerView view, TextField search,
                         float width, float height) {}

    /** Builds the frame only: title, search, navigation column and the scrolling page. */
    private static Shell build(Source source) {
        var minecraft = Minecraft.getInstance();
        float width = Math.min(WIN_W, Math.max(380.0F, minecraft.getWindow().getGuiScaledWidth() - 16.0F));
        float bodyHeight = Math.max(150.0F, Math.min(300.0F, minecraft.getWindow().getGuiScaledHeight() - 84.0F));

        UIElement root = new UIElement().layout(l -> {
            l.widthPercent(100);
            l.heightPercent(100);
        });

        UIElement window = new UIElement().layout(l -> {
            l.positionType(TaffyPosition.ABSOLUTE);
            l.left(12);
            l.top(36);
            l.width(width);
            l.heightAuto();
            l.paddingAll(4);
            l.gapAll(2);
            l.flexDirection(FlexDirection.COLUMN);
        }).style(s -> s.backgroundTexture(Sprites.BORDER));

        Label title = new Label();
        title.setText(source.title().isEmpty() ? Component.translatable(source instanceof RemoteServerSource
                ? "sablelightoverhaul.config.serverTitle"
                : "sablelightoverhaul.config.title") : LocalizedText.component(source.title()));
        title.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(8.0F)
                .adaptiveWidth(false).adaptiveHeight(false)
                .textAlignHorizontal(Horizontal.CENTER).textAlignVertical(Vertical.CENTER));
        title.layout(l -> {
            l.width(width - 8);
            l.height(12.0F);
        });
        window.addChild(title);

        UIElement searchRow = new UIElement().layout(l -> {
            l.width(width - 8);
            l.height(10.0F);
            l.gapAll(2);
            l.flexDirection(FlexDirection.ROW);
        });
        boolean remote = source instanceof RemoteServerSource || !source.serverNavigation() || minecraft.getConnection() == null;
        TextField search = new TextField();
        search.setText("", false);
        search.textFieldStyle(s -> s.textColor(0xFFFFFFFF).cursorColor(0xFFFFFFFF).textShadow(false)
                .fontSize(8.0F).placeholder(Component.translatable("sablelightoverhaul.config.searchHint")));
        search.layout(l -> {
            l.width(remote ? width - 8 : width - 104);
            l.height(10.0F);
            l.paddingHorizontal(2.0F);
            l.paddingVertical(1.0F);
        });
        searchRow.addChild(search);
        if (!remote) {
            Button server = new Button();
            server.setText(Component.translatable("sablelightoverhaul.config.serverButton"));
            server.textStyle(s -> s.fontSize(8.0F).textShadow(false));
            server.layout(l -> {
                l.width(94.0F);
                l.height(10.0F);
            });
            server.setOnClick(event -> {
                server.setActive(false);
                server.setText(Component.translatable("sablelightoverhaul.config.serverRequesting"));
                ConfigKey.requestServerConfig();
            });
            searchRow.addChild(server);
        }
        window.addChild(searchRow);
        if (!source.actions().isEmpty() || !source.serverNavigation()) {
            UIElement actions = new UIElement().layout(l -> { l.width(width - 8); l.heightAuto(); l.flexDirection(FlexDirection.ROW); l.gapAll(3); });
            for (Source.Action action : source.actions()) {
                Button button = new Button();
                button.setText(LocalizedText.component(action.label()));
                button.textStyle(s -> s.fontSize(8).textShadow(false));
                button.layout(l -> { l.width(72); l.height(12); });
                button.setOnClick(e -> {
                    action.run().run();
                    if(Minecraft.getInstance().screen instanceof ConfigWindow screen)screen.refreshCountdown=10;
                });
                actions.addChild(button);
            }
            if(!source.serverNavigation()) {
                Button refresh=new Button();refresh.setText(LocalizedText.component("刷新"));
                refresh.layout(l->{l.width(38);l.height(12);});refresh.textStyle(t->t.fontSize(8));
                refresh.setOnClick(e->{if(Minecraft.getInstance().screen instanceof ConfigWindow screen)screen.refreshCountdown=1;});
                actions.addChild(refresh);
            }
            window.addChild(actions);
        }

        UIElement body = new UIElement().layout(l -> {
            l.width(width - 8);
            l.height(bodyHeight);
            l.gapAll(4);
            l.flexDirection(FlexDirection.ROW);
        });
        window.addChild(body);

        UIElement nav = new UIElement().layout(l -> {
            l.width(NAV_W);
            l.height(bodyHeight);
            l.gapAll(1);
            l.flexDirection(FlexDirection.COLUMN);
        });
        body.addChild(nav);

        ScrollerView view = new ScrollerView();
        view.layout(l -> {
            l.width(width - NAV_W - 16);
            l.height(bodyHeight);
        });
        view.viewContainer.layout(l -> {
            l.widthPercent(100);
            l.gapAll(1);
            l.flexDirection(FlexDirection.COLUMN);
        });
        body.addChild(view);

        root.addChild(window);
        return new Shell(UI.of(root), window, nav, view, search, width, bodyHeight);
    }

    private List<String> categories() {
        var result = new java.util.LinkedHashSet<String>();
        for (String c : ConfigTaxonomy.CATEGORIES) if (hasCategory(c)) result.add(c);
        for (Source.Group group : allGroups) result.add(group.category());
        return List.copyOf(result);
    }

    private String firstCategory() {
        for (String candidate : categories()) {
            if (hasCategory(candidate)) return candidate;
        }
        return categories().isEmpty() ? "" : categories().get(0);
    }

    private boolean hasCategory(String candidate) {
        for (Source.Group group : allGroups) {
            if (group.category().equals(candidate)) return true;
        }
        return false;
    }

    private void rebuildNav() {
        nav.clearAllChildren();
        for (String candidate : categories()) {
            if (!hasCategory(candidate)) continue;
            boolean selected = candidate.equals(category);
            Button button = new Button();
            button.setText(Component.literal((selected ? "§e" : "§7") + LocalizedText.text(candidate)));
            button.textStyle(s -> s.fontSize(8.0F).textShadow(false));
            button.layout(l -> {
                l.width(NAV_W);
                l.height(12.0F);
            });
            button.setOnClick(event -> {
                category = candidate;
                source.selectCategory(category);
                query = "";
                search.setText("", false);
                collapseInitialised = false;
                collapsedSections.clear();
                collapsedTopics.clear();
                rebuildNav();
                rebuildContent();
            });
            nav.addChild(button);
        }
    }

    private void rebuildContent() {
        view.viewContainer.clearAllChildren();
        sliders.clear();
        selectors.clear();
        // The rows that owned the pressed slider are gone; its release will never arrive, so commit now.
        if (draggingSlider != null) {
            draggingSlider.endDrag();
            draggingSlider = null;
        }
        List<Source.Group> shown = shownGroups();
        int total = 0;
        for (Source.Group group : shown) total += group.fields().size();
        if (!collapseInitialised && query.isEmpty()) {
            collapseInitialised = true;
            collapsedSections.clear();
            String first = null;
            for (Source.Group group : shown) {
                if (!group.section().equals(first)) {
                    if (first != null) collapsedSections.add(group.section());
                    first = group.section();
                }
            }
        }

        Label header = new Label();
        header.setText(query.isEmpty()
                ? Component.literal(LocalizedText.text(category) + "  (" + total + ")")
                : Component.translatable("sablelightoverhaul.config.searchResults", total));
        header.textStyle(s -> s.textColor(0xFFFFC080).textShadow(false).fontSize(9.0F)
                .adaptiveWidth(false).adaptiveHeight(false)
                .textAlignHorizontal(Horizontal.LEFT).textAlignVertical(Vertical.CENTER));
        header.layout(l -> {
            l.widthPercent(100);
            l.height(12.0F);
        });
        view.addScrollViewChild(header);

        if (shown.isEmpty()) {
            Label empty = new Label();
            empty.setText(Component.translatable(query.isEmpty()
                    ? "sablelightoverhaul.config.emptyPage"
                    : "sablelightoverhaul.config.noMatches"));
            empty.textStyle(s -> s.textColor(0xFF909090).textShadow(false).fontSize(8.0F));
            empty.layout(l -> {
                l.widthPercent(100);
                l.height(12.0F);
            });
            view.addScrollViewChild(empty);
            view.verticalScroller.setValue(0.0F);
            return;
        }

        String lastSection = null;
        String lastTopic = null;
        UIElement sectionBody = null;
        UIElement topicBody = null;
        String sectionName = null;
        String topicName = null;
        for (Source.Group group : shown) {
            if (!group.section().equals(lastSection)) {
                lastSection = group.section();
                lastTopic = null;
                sectionName = group.section();
                String name = sectionName;
                int count = sectionCount(shown, name);
                boolean collapsed = query.isEmpty() && collapsedSections.contains(name);
                final UIElement body = column();
                body.setDisplay(!collapsed);
                sectionBody = body;
                Button section = header((collapsed ? "▸ " : "▾ ") + LocalizedText.text(name) + "  (" + count + ")",
                        8.0F, 0xFFFFC080, 10.0F);
                section.setOnClick(event -> {
                    boolean show = !body.isDisplayed();
                    body.setDisplay(show);
                    if (show) collapsedSections.remove(name); else collapsedSections.add(name);
                    section.setText(Component.literal((show ? "▾ " : "▸ ") + LocalizedText.text(name) + "  (" + count + ")"));
                });
                view.addScrollViewChild(section);
                view.addScrollViewChild(body);
            }
            if (!group.topic().equals(group.section())) {
                if (!group.topic().equals(lastTopic)) {
                    lastTopic = group.topic();
                    topicName = group.topic();
                    String name = topicName;
                    int count = topicCount(shown, group.section(), name);
                    String key = group.section() + "\u0000" + name;
                    boolean collapsed = collapsedTopics.contains(key);
                    final UIElement body = column();
                    body.setDisplay(!collapsed);
                    topicBody = body;
                    Button topic = header((collapsed ? "  ▸ " : "  ▾ ") + LocalizedText.text(name) + "  (" + count + ")",
                            7.0F, 0xFFB0B0B0, 9.0F);
                    topic.setOnClick(event -> {
                        boolean show = !body.isDisplayed();
                        body.setDisplay(show);
                        if (show) collapsedTopics.remove(key); else collapsedTopics.add(key);
                        topic.setText(Component.literal((show ? "  ▾ " : "  ▸ ") + LocalizedText.text(name) + "  (" + count + ")"));
                    });
                    sectionBody.addChild(topic);
                    sectionBody.addChild(body);
                }
            } else {
                topicBody = null;
            }
            UIElement target = topicBody == null ? sectionBody : topicBody;
            for (FieldSpec spec : group.fields()) target.addChild(row(spec));
        }
        view.verticalScroller.setValue(0.0F);
    }

    private Button header(String text, float font, int colour, float height) {
        Button button = new Button();
        button.setText(Component.literal(text));
        button.textStyle(s -> s.textColor(colour).textShadow(false).fontSize(font)
                .adaptiveWidth(false).adaptiveHeight(false)
                .textAlignHorizontal(Horizontal.LEFT).textAlignVertical(Vertical.CENTER));
        button.layout(l -> {
            l.widthPercent(100);
            l.height(height);
        });
        return button;
    }

    private UIElement column() {
        return new UIElement().layout(l -> {
            l.widthPercent(100);
            l.gapAll(1);
            l.flexDirection(FlexDirection.COLUMN);
        });
    }

    private int sectionCount(List<Source.Group> groups, String section) {
        int count = 0;
        for (Source.Group group : groups) {
            if (group.section().equals(section)) count += group.fields().size();
        }
        return count;
    }

    private int topicCount(List<Source.Group> groups, String section, String topic) {
        int count = 0;
        for (Source.Group group : groups) {
            if (group.section().equals(section) && group.topic().equals(topic)) count += group.fields().size();
        }
        return count;
    }

    /** Rows whose backend tier is not the one in use would only write values nobody reads. */
    private static boolean modeRelevant(FieldSpec spec) {
        dev.sablelightoverhaul.LightingMode mode = dev.sablelightoverhaul.client.ClientFlashes.effectiveMode();
        String key = spec.key();
        if (key.equals("shockwave.sloOnlyRefraction")) {
            return mode == dev.sablelightoverhaul.LightingMode.SloOnly;
        }
        if (key.equals("lighting.screenScale") || key.equals("lighting.tiled")) {
            return mode == dev.sablelightoverhaul.LightingMode.DynamicOnly
                    || mode == dev.sablelightoverhaul.LightingMode.SloOnly;
        }
        if (key.equals("lighting.inPackScale")) {
            return mode == dev.sablelightoverhaul.LightingMode.Advanced;
        }
        if (key.equals("lighting.shaderScale")) {
            return mode == dev.sablelightoverhaul.LightingMode.DynamicOnly
                    && dev.sablelightoverhaul.client.IrisCompat.shaderPackInUse();
        }
        if (key.startsWith("lighting.color.") || key.equals("lighting.falloffPower")
                || key.equals("lighting.normalInfluence")) {
            return mode == dev.sablelightoverhaul.LightingMode.DynamicOnly
                    || mode == dev.sablelightoverhaul.LightingMode.SloOnly
                    || mode == dev.sablelightoverhaul.LightingMode.Advanced;
        }
        return true;
    }

    @Override public void tick() {
        super.tick();
        if(refreshCountdown>0 && --refreshCountdown==0){values.clear();values.putAll(source.read());contentRebuildPending=true;}
        // Rebuild after Selector has finished closing its popup and dispatching the click.
        if(contentRebuildPending){contentRebuildPending=false;rebuildContent();}
    }

    private boolean dustRelevant(FieldSpec spec) {
        String prefix="muzzle.tankCannon.groundDust.",key=spec.key();
        if(!key.startsWith(prefix))return true;
        var mode=values.get(prefix+"mode");
        boolean field=!(mode instanceof FieldValue.Choice c)||c.name().equals("FIELD");
        String suffix=key.substring(prefix.length());
        if(suffix.startsWith("field."))return field;
        return !field||!java.util.Set.of("count","speed","scatter","damping","gravity","sizeMin","sizeMax","stretchMin","stretchMax","fade").contains(suffix);
    }

    private List<Source.Group> shownGroups() {
        List<Source.Group> out = new ArrayList<>();
        for (Source.Group group : allGroups) {
            if (query.isEmpty() && !group.category().equals(category)) continue;
            List<FieldSpec> fields = new ArrayList<>();
            for (FieldSpec spec : group.fields()) {
                if (!source.isRelevant(spec) || !modeRelevant(spec) || !dustRelevant(spec)) continue;
                if (query.isEmpty()
                        || spec.key().toLowerCase(java.util.Locale.ROOT).contains(query)
                        || spec.label().toLowerCase(java.util.Locale.ROOT).contains(query)
                        || fieldLabel(spec).getString().toLowerCase(java.util.Locale.ROOT).contains(query)
                        || LocalizedText.text(spec.tooltip()).toLowerCase(java.util.Locale.ROOT).contains(query)) {
                    fields.add(spec);
                }
            }
            if (fields.isEmpty()) continue;
            out.add(new Source.Group(group.category(), group.section(), group.topic(), List.copyOf(fields)));
        }
        return out;
    }

    /** The long description is shown on hover only; the label column stays short. */
    private static List<Component> tooltipLines(String text) {
        String description = LocalizedText.text(text).trim();
        String defaults = null;
        int cut = description.indexOf("  Default:");
        if (cut > 0) {
            defaults = description.substring(cut).trim();
            description = description.substring(0, cut).trim();
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(description));
        if (defaults != null && !defaults.isBlank()) {
            lines.add(Component.literal(defaults).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return lines;
    }

    private static Component fieldLabel(FieldSpec spec) {
        return spec.labelKey() != null && net.minecraft.locale.Language.getInstance().has(spec.labelKey())
                ? Component.translatable(spec.labelKey()) : LocalizedText.component(spec.label());
    }

    private UIElement row(FieldSpec spec) {
        UIElement row = new UIElement().layout(l -> {
            l.widthPercent(100);
            l.height(ROW_H);
            l.gapAll(4);
            l.flexDirection(FlexDirection.ROW);
            l.alignItems(AlignItems.CENTER);
        });
        if (spec.tooltip() != null && !spec.tooltip().isBlank()) {
            List<Component> lines = tooltipLines(spec.tooltip());
            // The hover event walks up from the hovered child, so one listener on the row covers label,
            // slider and text box alike.
            row.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
                    event.hoverTooltips = new HoverTooltips(lines, null, null, null));
        }
        Label label = new Label();
        Component labelText = fieldLabel(spec);
        if (spec.needsRestart()) labelText = labelText.copy().append(" *");
        label.setText(labelText);
        label.textStyle(s -> s.textColor(0xFFE0E0E0).textShadow(false).fontSize(8.0F)
                .adaptiveWidth(false).adaptiveHeight(false).textWrap(TextWrap.HOVER_ROLL)
                .textAlignHorizontal(Horizontal.LEFT).textAlignVertical(Vertical.CENTER));
        label.layout(l -> {
            l.width(source.canReset(spec) ? labelW - 14 : labelW);
            l.height(ROW_H);
            l.flexShrink(0.0F);
        });
        row.addChild(label);
        if(source.canReset(spec)) {
            Button reset = new Button();reset.setText(Component.literal("↺"));
            reset.layout(l -> {l.width(10);l.height(10);});
            reset.textStyle(s -> s.fontSize(8));
            reset.setOnClick(e -> {source.reset(spec);source.flush();refreshCountdown=10;});
            row.addChild(reset);
        }
        if (!source.canWrite(spec)) {
            Label fixed = new Label();
            String text = FieldValue.display(spec, values.get(spec.key()));
            fixed.setText(spec.kind() == FieldSpec.Kind.TEXT ? Component.literal(text == null ? "" : text) : LocalizedText.component(text));
            fixed.textStyle(s -> s.textColor(0xFF909090).textShadow(false).fontSize(8.0F)
                    .adaptiveWidth(false).adaptiveHeight(false));
            fixed.layout(l -> {
                l.width(controlW);
                l.height(ROW_H);
            });
            row.addChild(fixed);
        } else {
            row.addChild(control(spec));
        }
        return row;
    }

    private UIElement control(FieldSpec spec) {
        FieldValue current = values.get(spec.key());
        return switch (spec.kind()) {
            case TOGGLE -> {
                Toggle toggle = new Toggle();
                toggle.noText();
                toggle.setOn(FieldValue.flag(current), false);
                toggle.setOnToggleChanged(on -> {
                    write(spec, new FieldValue.Bool(on));
                    source.flush();
                });
                toggle.layout(l -> {
                    l.width(controlW);
                    l.height(10.0F);
                });
                yield toggle;
            }
            case CYCLER -> {
                Selector<String> selector = new Selector<>();
                selector.setCandidates(spec.options());
                {
                    // The selector builds its preview once before a value has been selected.
                    selector.setCandidateUIProvider(UIElementProvider.text(name -> {
                        if (name == null) return Component.empty();
                        return "DynamicOnly".equals(name)
                                ? Component.translatable("sablelightoverhaul.mode.screenSpaceApproximation")
                                : LocalizedText.component(name);
                    }));
                    selector.selectorStyle(style -> style.maxItemCount(spec.options().size()));
                }
                int index = current instanceof FieldValue.Choice c ? c.index() : 0;
                if (!spec.options().isEmpty()) {
                    selector.setSelected(spec.options().get(Math.max(0, Math.min(spec.options().size() - 1, index))),
                            false);
                }
                selectors.add(selector);
                selector.setOnValueChanged(chosen -> {
                    int picked = spec.options().indexOf(chosen);
                    if (picked >= 0) {
                        write(spec, new FieldValue.Choice(picked, chosen));
                        source.flush();
                        if(spec.key().equals("muzzle.tankCannon.groundDust.mode"))contentRebuildPending=true;
                    }
                });
                selector.layout(l -> {
                    l.width(controlW);
                    l.height(10.0F);
                });
                yield selector;
            }
            case SLIDER -> numeric(spec, current);
            default -> text(spec, current);
        };
    }

    private UIElement text(FieldSpec spec, FieldValue current) {
        String shown = FieldValue.display(spec, current);
        TextField field = new TextField();
        field.setText(shown == null ? "" : shown, false);
        field.textFieldStyle(s -> s.textColor(0xFFFFFFFF).cursorColor(0xFFFFFFFF).textShadow(false)
                .fontSize(8.0F));
        field.layout(l -> {
            l.width(controlW);
            l.height(10.0F);
            l.paddingHorizontal(2.0F);
            l.paddingVertical(1.0F);
        });
        field.setTextResponder(text -> {
            FieldValue parsed = parse(spec, text);
            if (parsed == null) return;
            write(spec, parsed);
            source.flush();
        });
        return field;
    }

    /** A bar and a box; the bar writes while dragged, the box is for exact numbers. */
    private UIElement numeric(FieldSpec spec, FieldValue current) {
        double value = current instanceof FieldValue.Num number ? number.value() : spec.min();
        TextField field = new TextField();
        String shown = FieldValue.display(spec, current);
        field.setText(shown == null ? "" : shown, false);
        field.textFieldStyle(s -> s.textColor(0xFFFFFFFF).cursorColor(0xFFFFFFFF).textShadow(false)
                .fontSize(8.0F));
        field.layout(l -> {
            l.width(70.0F);
            l.height(10.0F);
            l.paddingHorizontal(2.0F);
            l.paddingVertical(1.0F);
        });

        ValueSlider[] slider = new ValueSlider[1];
        slider[0] = new ValueSlider(spec.fraction(value), fraction -> {
            double next = spec.fromFraction(fraction);
            write(spec, new FieldValue.Num(next));
            String text = FieldValue.display(spec, values.get(spec.key()));
            field.setText(text == null ? "" : text, false);
        }, source::flush);
        sliders.add(slider[0]);
        slider[0].layout(l -> {
            l.width(controlW - 76.0F);
            l.height(10.0F);
        });

        field.setTextResponder(text -> {
            FieldValue parsed = parse(spec, text);
            if (parsed == null) return;
            write(spec, parsed);
            double fraction = spec.fraction(FieldValue.numeric(parsed));
            LOG.info("[SLO-CFG] text set key={} min={} max={} parsed={} fraction={}",
                    spec.key(), spec.min(), spec.max(), FieldValue.numeric(parsed), fraction);
            slider[0].setFraction(fraction);
            source.flush();
        });

        UIElement row = new UIElement().layout(l -> {
            l.width(controlW);
            l.height(10.0F);
            l.gapAll(4);
            l.flexDirection(FlexDirection.ROW);
            l.alignItems(AlignItems.CENTER);
        });
        row.addChildren(slider[0], field);
        return row;
    }

    private void write(FieldSpec spec, FieldValue candidate) {
        FieldValue changed = FieldValue.changed(values.get(spec.key()), candidate);
        if (changed == null) return;
        source.write(spec, changed);
        values.put(spec.key(), changed);
    }

    private static FieldValue parse(FieldSpec spec, String text) {
        if (spec.kind() == FieldSpec.Kind.COLOR) {
            try {
                return new FieldValue.Num(Integer.parseInt(text.trim().replace("0x", "").replace("#", ""), 16)
                        & 0xFFFFFF);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (spec.kind() == FieldSpec.Kind.TEXT) return new FieldValue.Text(text);
        try {
            return new FieldValue.Num(spec.snap(Double.parseDouble(text.trim())));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ window handling

    private void moveTo(float x, float y) {
        this.window.layout(l -> {
            l.left(x);
            l.top(y);
        });
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x33000000);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        // An open selector popup may be drawn over any slider; the normal dispatcher must own that click.
        boolean capture = ConfigPointerCapture.manual(button,selectorOpen());
        if (capture && insideTitleBar(mouseX, mouseY)) {
            dragging = true;
            dragFromX = mouseX;
            dragFromY = mouseY;
            dragOriginX = winX;
            dragOriginY = winY;
            return true;
        }
        if (capture) {
            ValueSlider hit = sliderAt((float) mouseX, (float) mouseY);
            if (hit != null) {
                LOG.info("[SLO-CFG] slider hit mouse=({}, {}) left={} top={} size={}x{}",
                        mouseX, mouseY, hit.getPositionX(), hit.getPositionY(),
                        hit.getSizeWidth(), hit.getSizeHeight());
                draggingSlider = hit;
                hit.begin((float) mouseX);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean selectorOpen() {
        for (Selector<?> selector : selectors) {
            if (selector.isOpen()) return true;
        }
        return false;
    }

    /** The slider under the cursor, if it is displayed and the pointer lies inside the visible viewport. */
    private ValueSlider sliderAt(float mouseX, float mouseY) {
        float scrollLeft = view.getPositionX();
        float scrollTop = view.getPositionY();
        float scrollWidth = view.getSizeWidth();
        float scrollHeight = view.getSizeHeight();
        for (ValueSlider candidate : sliders) {
            if (!candidate.isDisplayed()) continue;
            float left = candidate.getPositionX();
            float top = candidate.getPositionY();
            float width = candidate.getSizeWidth();
            float height = candidate.getSizeHeight();
            if(!ConfigPointerCapture.hit(mouseX,mouseY,left,top,width,height,scrollLeft,scrollTop,scrollWidth,scrollHeight))continue;
            return candidate;
        }
        return null;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSlider != null) {
            draggingSlider.dragTo((float) mouseX);
            return true;
        }
        if (dragging) {
            winX = Math.max(0.0F, Math.min(this.width - winW, dragOriginX + (float) (mouseX - dragFromX)));
            winY = Math.max(0.0F, Math.min(this.height - 24.0F, dragOriginY + (float) (mouseY - dragFromY)));
            moveTo(winX, winY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if(button!=0) return super.mouseReleased(mouseX,mouseY,button);
        if (draggingSlider != null) {
            draggingSlider.endDrag();
            draggingSlider = null;
            return true;
        }
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean insideTitleBar(double mouseX, double mouseY) {
        return mouseX >= winX && mouseX <= winX + winW
                && mouseY >= winY && mouseY <= winY + 14.0F;
    }

    @Override
    public void onClose() {
        source.flush();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * A minimal draggable bar. LDLib2 has no Slider in this version, and a text box alone is not a slider, so
     * this paints a ProgressBar and turns a drag across its width into a fraction.
     */
    private static final class ValueSlider extends UIElement {
        private final ProgressBar bar = new ProgressBar();
        private final java.util.function.DoubleConsumer onChange;
        private final Runnable onCommit;
        private float startWorldX;
        private float startFraction;
        private boolean dragging;

        ValueSlider(double fraction, java.util.function.DoubleConsumer onChange, Runnable onCommit) {
            this.onChange = onChange;
            this.onCommit = onCommit;
            bar.setRange(0.0F, 1.0F);
            // A config value is absolute: interpolating from the previous value made "type 30 after 1200"
            // look full until the animation caught up (or forever, if no tick reached it).
            bar.progressBarStyle(style -> style.interpolate(false));
            bar.setProgress((float) Math.max(0.0, Math.min(1.0, fraction)));
            bar.layout(l -> {
                l.widthPercent(100);
                l.heightPercent(100);
            });
            bar.label(label -> label.setVisible(false));
            addChild(bar);
        }

        void setFraction(double fraction) {
            bar.setProgress((float) Math.max(0.0, Math.min(1.0, fraction)));
        }

        /** The screen owns the press: UIEvent coordinates proved unreliable inside the scroller. */
        void begin(float mouseX) {
            startWorldX = getPositionX();
            startFraction = bar.getNormalizedValue();
            dragging = true;
            LOG.info("[SLO-CFG] slider begin mouseX={} left={} width={} startFraction={}",
                    mouseX, startWorldX, getSizeWidth(), startFraction);
            dragTo(mouseX);
        }

        void dragTo(float mouseX) {
            if (!dragging) return;
            float width = getSizeWidth();
            if (width <= 0.0F) return;
            double fraction = Math.max(0.0, Math.min(1.0, (mouseX - startWorldX) / width));
            bar.setProgress((float) fraction);
            onChange.accept(fraction);
        }

        void endDrag() {
            if (!dragging) return;
            dragging = false;
            LOG.info("[SLO-CFG] slider end fraction={}", bar.getNormalizedValue());
            if (onCommit != null) onCommit.run();
        }
    }
}
