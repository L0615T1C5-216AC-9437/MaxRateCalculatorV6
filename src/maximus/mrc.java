package maximus;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Dialog;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Log;
import com.google.ortools.Loader;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.World;
import mindustry.game.EventType;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Iconc;
import mindustry.gen.Tex;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.input.Placement;
import mindustry.mod.Mod;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;

import java.util.Locale;
import java.util.ResourceBundle;

import static arc.Core.settings;
import static mindustry.Vars.*;

public class mrc extends Mod {
    private static KeyCode key = KeyCode.backtick;
    private static boolean contrast = false;
    private static Label.LabelStyle style = Styles.defaultLabel;
    private static final int maxSelection = 500;
    public static ResourceBundle bundle;
    //translations
    public static String translatedStringPower = "";
    public static String translatedStringOptional = "";
    public static String translatedStringRealTitle = "";
    public static String translatedStringMaxTitle = "";
    public static String translatedStringPowerGeneration = "";

    private static int x1 = -1, y1 = -1, x2 = -1, y2 = -1;

    public enum MRCSettings {
        KeyBind("`"),
        MenuTransparency(5),
        HighContrast(false);

        public static final MRCSettings[] all = values();

        public final String key;
        public final Object defaultValue;

        MRCSettings(Object def) {
            this.key = "mrc" + name();
            this.defaultValue = def;
        }

        public boolean isNum() {
            return defaultValue instanceof Integer;
        }

        public boolean isBool() {
            return defaultValue instanceof Boolean;
        }

        public boolean isString() {
            return defaultValue instanceof String;
        }

        public Object get() {
            return Core.settings.get(key, defaultValue);
        }

        public boolean bool() {
            return Core.settings.getBool(key, (Boolean) defaultValue);
        }

        public int num() {
            return Core.settings.getInt(key, (Integer) defaultValue);
        }

        public String string() {
            return Core.settings.getString(key, (String) defaultValue);
        }

        public void set(Object value) {
            Core.settings.put(key, value);
        }
    }

    public mrc(){
        Log.info("Loading Events in Max Rate Calculator.");

        //listen for game load event
        Events.run(Trigger.draw, () -> {
            if (Core.input.keyDown(key) && x1 != -1 && y1 != -1) {
                drawSelection(x1, y1, tileX(Core.input.mouseX()), tileY(Core.input.mouseY()));
            }
        });
        Events.on(ClientLoadEvent.class, event -> {
            //load language pack
            Locale locale;
            String loc = settings.getString("locale");
            if(loc.equals("default")){
                locale = Locale.getDefault();
            }else{
                if(loc.contains("_")){
                    String[] split = loc.split("_");
                    locale = new Locale(split[0], split[1]);
                }else{
                    locale = new Locale(loc);
                }
            }
            try {
                bundle = ResourceBundle.getBundle("languagePack", locale);
            } catch (Exception ignored) {
                Log.err("No language pack available for Max Rate Calculator, defaulting to english");
                bundle = ResourceBundle.getBundle("languagePack", Locale.ENGLISH);
            }

            translatedStringPower = Core.bundle.get("bar.power");
            translatedStringOptional = mrc.bundle.getString("optional");
            translatedStringRealTitle = mrc.bundle.getString("calculateReal");
            translatedStringMaxTitle = mrc.bundle.getString("calculateMaximum");
            translatedStringPowerGeneration = mrc.bundle.getString("powerGeneration");

            for (KeyCode kc : KeyCode.all) { //this is probably a terrible implementation of custom key binds
                if (kc.value.equalsIgnoreCase(MRCSettings.KeyBind.string())) {
                    key = kc;
                    break;
                }
            }
            contrast = MRCSettings.HighContrast.bool();
            style = contrast ? Styles.outlineLabel : Styles.defaultLabel;

            /*
            //add setting to core bundle
            var coreBundle = Core.bundle.getProperties();
            coreBundle.put("setting.mrcSendInfoMessage.name", mrc.bundle.getString("mrc.settings.SendInfoMessage"));
            coreBundle.put("setting.mrcSplitInfoMessage.name", mrc.bundle.getString("mrc.settings.SplitInfoMessage"));
            coreBundle.put("setting.mrcShowZeroAverageMath.name", mrc.bundle.getString("mrc.settings.ShowZeroAverageMath"));
            Core.bundle.setProperties(coreBundle);
            //add custom settings
            Core.settings.put("uiscalechanged", false);//stop annoying "ui scale changed" message
            addBooleanGameSetting("mrcSendInfoMessage", false);
            addBooleanGameSetting("mrcSplitInfoMessage", false);
            addBooleanGameSetting("mrcShowZeroAverageMath", true);
             */

            if (!Core.settings.has("mrcFirstTime")) {
                Vars.ui.showInfo(bundle.getString("mrc.firstTimeMessage"));
                Core.settings.put("mrcFirstTime", false);
                Core.settings.forceSave();
            }

            Loader.loadNativeLibraries();
        });
        Events.run(EventType.Trigger.update, () -> {
            if (Vars.state.isPlaying() && !Vars.ui.chatfrag.shown() && !Core.scene.hasDialog()) {
                int rawCursorX = World.toTile(Core.input.mouseWorld().x), rawCursorY = World.toTile(Core.input.mouseWorld().y);

                if (Core.input.keyTap(key)) {
                    x1 = rawCursorX;
                    y1 = rawCursorY;
                }
                if (Core.input.keyRelease(key) && x1 != -1 && y1 != -1) {
                    x2 = rawCursorX;
                    y2 = rawCursorY;
                    matrix m = new matrix(x1, y1, x2, y2, true);
                    if (m.lastCR.code() != matrix.ExitCode.NoRecipes) {
                        CustomLabel cl = new CustomLabel(m, (x1 + x2) * 4f, (Math.min(y1, y2) - 5) * 8f);
                    }
                    x1 = -1;
                    y1 = -1;
                    x2 = -1;
                    y2 = -1;
                }
            }
        });
    }

    @Override
    public void loadContent(){
		Log.info("Loading the Max Rate Calculator!");
    }
    //anuke
    int tileX(float cursorX) {
        return World.toTile(Core.input.mouseWorld(cursorX, 0).x);
    }

    int tileY(float cursorY) {
        return World.toTile(Core.input.mouseWorld(0, cursorY).y);
    }

    public boolean validBreak(int x, int y){
        return Build.validBreak(player.team(), x, y);
    }
    //anuke likes to draw stuff
    void drawSelection(int x1, int y1, int x2, int y2) {
        //todo: fix weird bloom effect
        Draw.reset();
        Placement.NormalizeDrawResult result = Placement.normalizeDrawArea(Blocks.air, x1, y1, x2, y2, false, mrc.maxSelection, 1f);
        Placement.NormalizeResult dresult = Placement.normalizeArea(x1, y1, x2, y2, 0, false, mrc.maxSelection);

        for(int x = dresult.x; x <= dresult.x2; x++){
            for(int y = dresult.y; y <= dresult.y2; y++){
                Tile tile = world.tileBuilding(x, y);
                if(tile == null || !validBreak(tile.x, tile.y)) continue;

                drawBreaking(tile.x, tile.y);
            }
        }

        Lines.stroke(2f);

        Draw.color(Pal.accentBack);
        Lines.rect(result.x, result.y - 1, result.x2 - result.x, result.y2 - result.y);
        Draw.color(Pal.accent);
        Lines.rect(result.x, result.y, result.x2 - result.x, result.y2 - result.y);
    }

    private static void drawBreaking(int x, int y) {
        Tile tile = world.tile(x, y);
        if(tile == null) return;
        Block block = tile.block();

        drawSelected(x, y, block, Pal.accent);
    }

    private static void drawSelected(int x, int y, Block block, Color color) {
        Drawf.selected(x, y, block, color);
    }

    /*
    public static void addBooleanGameSetting(String key, boolean defaultBooleanValue){
        Vars.ui.settings.game.checkPref(key, Core.settings.getBool(key, defaultBooleanValue));
    }
     */

    public static class CustomLabel {
        private final matrix m;
        private Table table;
        private MenuType mt = MenuType.Calculation;

        private boolean rateLimit = true;
        private boolean complex = false;

        private final float worldX;
        private final float worldY;

        public CustomLabel(matrix m, float worldX, float worldY) {
            this.m = m;
            this.worldX = worldX;
            this.worldY = worldY;

            update();
        }

        public void update() {
            if (table != null) table.remove();
            table = new Table(Tex.whiteui.tint(0f, 0f, 0f, MRCSettings.MenuTransparency.num() * 0.1f)).margin(4);
            table.touchable = Touchable.enabled;
            table.update(() -> {
                if(state.isMenu()) table.remove();
                Vec2 v = Core.camera.project(worldX, worldY);
                table.setPosition(v.x, v.y, Align.center);
            });
            //
            setupMenu(table.table());
            /*
            table.table(buttons -> {
                buttons.button("Calculation", () -> {
                    mt = MenuType.Calculation;
                    update();
                }).height(30).disabled(mt == MenuType.Calculation).padRight(3).get().getLabel().setWrap(false);
                buttons.button("Editor", () -> {
                    mt = MenuType.Editor;
                    update();
                }).height(30).disabled(mt == MenuType.Editor).padRight(3).get().getLabel().setWrap(false);
                buttons.button("Close", table::remove).height(30).padRight(3).get().getLabel().setWrap(false);
            }).colspan(3);
             */
            table.row();

            switch (mt) {
                case Settings -> {
                    //key bind
                    table.button("Change KeyBind", () -> {
                        Dialog dialog = new Dialog(Core.bundle.get("keybind.press", "Press a key..."));
                        dialog.titleTable.getCells().first().pad(4);
                        dialog.addListener(new InputListener(){
                            @Override
                            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode keycode){
                                if(keycode == KeyCode.escape) {
                                    dialog.hide();
                                    return false;
                                }
                                key = keycode;
                                MRCSettings.KeyBind.set(key.value);
                                Vars.ui.showInfo("Max Rate Calculator Key set to " + key.value);
                                dialog.hide();
                                return false;
                            }

                            @Override
                            public boolean keyDown(InputEvent event, KeyCode keycode){
                                if(keycode == KeyCode.escape) {
                                    dialog.hide();
                                    return false;
                                }
                                key = keycode;
                                MRCSettings.KeyBind.set(key.value);
                                Vars.ui.showInfo("Max Rate Calculator Key set to " + key.value);
                                dialog.hide();
                                return false;
                            }
                        });
                        dialog.show();
                    }).height(30).get().getLabel().setWrap(false);
                    table.row();
                    //changing menu's transparency
                    table.table(t -> {
                        t.add("Menu Transparency").style(style);
                        t.button(String.valueOf(Iconc.up), () -> {
                            int i = MRCSettings.MenuTransparency.num();
                            if (i < 10) {
                                MRCSettings.MenuTransparency.set(++i);
                                update();
                            }
                        }).height(25).width(35);
                        t.add(MRCSettings.MenuTransparency.num() + "").style(style);
                        t.button(String.valueOf(Iconc.down), () -> {
                            int i = MRCSettings.MenuTransparency.num();
                            if (i > 0) {
                                MRCSettings.MenuTransparency.set(--i);
                                update();
                            }
                        }).height(25).width(35);
                    }).colspan(4);
                    table.row();
                    //changing text contrast
                    table.button((contrast ? "[lime]" : "[scarlet]") + "High Contrast Text", () -> {
                        contrast = !contrast;
                        MRCSettings.HighContrast.set(contrast);
                        style = contrast ? Styles.outlineLabel : Styles.defaultLabel;
                        update();
                    }).height(30).get().getLabel().setWrap(false);
                    table.row();
                }
                case Calculation -> {
                    if (rateLimit) {
                        m.setRecipeRateLimit();
                    } else {
                        m.setRecipeMaximum();
                    }

                    table.add((rateLimit ? translatedStringRealTitle : translatedStringMaxTitle) + "\n[orange]=========================[white]" + m.parse(rateLimit, complex)).style(style);
                    table.row();
                    if (complex) {
                        table.row();
                        table.table(t -> {
                            for (int i = 0; i < m.recipes.size(); i++) {
                                matrix.Recipe r = m.recipes.get(i);
                                t.add(r.count + "x").style(style);
                                t.add(r.block.emoji());
                                t.add(r.block.name).style(style);
                                t.add("([lightgray]" + matrix.df.format(r.efficiency * 100d) + "%[white])").style(style);
                                if (i != m.recipes.size() - 1) t.row();
                            }
                        }).colspan(4);
                        table.row();
                    }
                    table.table(buttons -> {
                        buttons.button((rateLimit ? "[lime]" : "[scarlet]") + "Rate Limit", () -> {
                            rateLimit = !rateLimit;
                            update();
                        }).height(30).padRight(3).get().getLabel().setWrap(false);
                        buttons.button((complex ? "[lime]" : "[scarlet]") + "Complex", () -> {
                            complex = !complex;
                            update();
                        }).height(30).padRight(3).get().getLabel().setWrap(false);
                    }).colspan(2);
                }
                case Editor -> {
                    table.add("Recipe Editor").style(style);
                    table.row();
                    table.table(buttons -> {
                        for (int i = 0; i < m.recipes.size(); i++) {
                            matrix.Recipe r = m.recipes.get(i);
                            buttons.add(r.block.emoji());
                            buttons.add(r.block.name).padRight(3).style(style);
                            buttons.button(String.valueOf(Iconc.up), () -> {
                                r.count++;
                                update();
                            }).height(25).width(35);
                            buttons.add(r.count + "").style(style);
                            buttons.button(String.valueOf(Iconc.down), () -> {
                                if (r.count > 0) {
                                    r.count--;
                                    update();
                                }
                            }).height(25).width(35);
                            buttons.button(String.valueOf(Iconc.refresh), () -> {
                                r.count = r.origCount;
                                update();
                            }).height(25).width(35);
                            if (i != m.recipes.size() - 1) buttons.row();
                        }
                    }).colspan(6);
                    table.row();
                    table.table(buttons -> {
                        for (int i = 0; i < m.sources.size(); i++) {
                            matrix.SourceRecipe r = m.sources.get(i);
                            buttons.add(r.out.emoji());
                            buttons.add(r.out.name).style(style);
                            buttons.button((r.enabled ? "[lime]" : "[scarlet]") + "Import", () -> {
                                r.enabled = !r.enabled;
                                update();
                            }).height(25).get().getLabel().setWrap(false);
                            if (i != m.sources.size() - 1) buttons.row();
                        }
                    }).colspan(4);
                }
            }
            //table.row().button(Iconc.cancel + " " + Iconc.ok, table::remove).width(80).height(30);
            table.pack();
            table.act(0);
            Core.scene.root.addChildAt(0, table);
            table.getChildren().first().act(0f);
        }

        public void setupMenu(Cell<Table> t) {
            for (MenuType mt : MenuType.values()) {
                var a = t.get().button(mt.label, () -> {
                    this.mt = mt;
                    update();
                }).height(30).disabled(this.mt == mt).padRight(3);
                if (mt.label.length() == 1) {
                    a.width(35);
                } else {
                    a.get().getLabel().setWrap(false);
                }
            }
            t.get().button("Close", table::remove).height(30).padRight(3).get().getLabel().setWrap(false);
            t.colspan(MenuType.values().length + 1);
        }

        public enum MenuType {
            Settings(String.valueOf(Iconc.settings)),
            Calculation(),
            Editor();

            public final String label;

            MenuType() {
                label = name();
            }

            MenuType(String label) {
                this.label = label;
            }
            //Optimizer
        }
    }
}
