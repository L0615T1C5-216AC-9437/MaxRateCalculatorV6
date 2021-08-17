package maximus;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.World;
import mindustry.game.EventType;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.input.Placement;
import mindustry.mod.Mod;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;

import java.util.Locale;
import java.util.ResourceBundle;

import static arc.Core.settings;
import static mindustry.Vars.player;
import static mindustry.Vars.world;

public class mrc extends Mod {
    private static final KeyCode key = KeyCode.backtick;
    private static final int maxSelection = 500;
    public static ResourceBundle bundle;
    //translations
    public static String translatedStringPower = "";
    public static String translatedStringOptional = "";

    private static int x1 = -1, y1 = -1, x2 = -1, y2 = -1;

    public mrc(){
        Log.info("Loading Events in Max Rate Calculator.");

        //listen for game load event
        Events.run(Trigger.draw, () -> {
            if (Core.input.keyDown(key) && x1 != -1 && y1 != -1) {
                drawSelection(x1, y1, tileX(Core.input.mouseX()), tileY(Core.input.mouseY()), maxSelection);
            }
        });
        Events.on(ClientLoadEvent.class, event -> {
            Locale locale;
            String loc = settings.getString("locale");
            if(loc.equals("default")){
                locale = Locale.getDefault();
            }else{
                Locale lastLocale;
                if(loc.contains("_")){
                    String[] split = loc.split("_");
                    lastLocale = new Locale(split[0], split[1]);
                }else{
                    lastLocale = new Locale(loc);
                }
                locale = lastLocale;
            }
            try {
                bundle = ResourceBundle.getBundle("languagePack", locale);
            } catch (Exception ignored) {
                Log.err("No language pack available for Max Rate Calculator, defaulting to english");
                bundle = ResourceBundle.getBundle("languagePack", Locale.ENGLISH);
            }
            //setup
            translatedStringPower = Core.bundle.get("bar.power");
            translatedStringOptional = mrc.bundle.getString("optional");

            calculateReal.translatedStringLabel = mrc.bundle.getString("calculateReal") + mrc.bundle.getString("calculateReal.label");
            calculateReal.translatedStringPowerGeneration = mrc.bundle.getString("powerGeneration");

            calculateMax.translatedStringLabel = mrc.bundle.getString("calculateMaximum") + "\n[orange]=========================[white]";


            if (!Core.settings.has("mrcFirstTime")) {
                Vars.ui.showInfo(bundle.getString("mrc.firstTimeMessage"));
                Core.settings.put("mrcFirstTime", false);
                Core.settings.forceSave();
            }
        });
        Events.run(EventType.Trigger.update, () -> {
            int rawCursorX = World.toTile(Core.input.mouseWorld().x), rawCursorY = World.toTile(Core.input.mouseWorld().y);

            if (Core.input.keyTap(key)) {
                x1 = rawCursorX;
                y1 = rawCursorY;
            }
            if (Core.input.keyRelease(key) && x1 != -1 && y1 != -1) {
                x2 = rawCursorX;
                y2 = rawCursorY;
                String infoMessage = "";
                try {
                    calculation cal = new calculateMax(x1, y1, x2, y2);
                    cal.calculate();
                    if (!cal.formattedMessage.isEmpty()) {
                        infoMessage += cal.formattedMessage + "\n\n[white]";
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    calculation cal = new calculateReal(x1, y1, x2, y2);
                    cal.calculate();
                    if (!cal.formattedMessage.isEmpty()) {
                        infoMessage += cal.formattedMessage;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (!infoMessage.isEmpty()) {
                    Vars.ui.showInfo(infoMessage);
                }
                x1 = -1;
                y1 = -1;
                x2 = -1;
                y2 = -1;
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
    void drawSelection(int x1, int y1, int x2, int y2, int maxLength) {
        //todo: fix weird bloom effect
        Draw.reset();
        Placement.NormalizeDrawResult result = Placement.normalizeDrawArea(Blocks.air, x1, y1, x2, y2, false, maxLength, 1f);
        Placement.NormalizeResult dresult = Placement.normalizeArea(x1, y1, x2, y2, 0, false, maxLength);

        for(int x = dresult.x; x <= dresult.x2; x++){
            for(int y = dresult.y; y <= dresult.y2; y++){
                Tile tile = world.tileBuilding(x, y);
                if(tile == null || !validBreak(tile.x, tile.y)) continue;

                drawBreaking(tile.x, tile.y);
            }
        }

        Lines.stroke(2f);

        Draw.color(Pal.accent);
        Draw.alpha(0.3f);
        float x = (result.x2 + result.x) / 2;
        float y = (result.y2 + result.y) / 2;
        Fill.rect(x, y, result.x2 - result.x, result.y2 - result.y);
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

    public static class calculation {
        public final int xl;
        public final int xr;
        public final int yb;
        public final int yt;

        public String formattedMessage = "";

        public calculation(int x1, int y1, int x2, int y2) {
            xl = Math.min(x1, x2);
            xr = Math.max(x1, x2);
            yb = Math.min(y1, y2);
            yt = Math.max(y1, y2);
        }

        public void calculate() {

        }

        public void callInfoMessage() {
            Vars.ui.showInfo(formattedMessage);
        }
    }
}
