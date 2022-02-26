package maximus;

import arc.struct.Seq;
import arc.util.Log;
import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Building;
import mindustry.gen.Iconc;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Boulder;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.units.Reconstructor;
import mindustry.world.blocks.units.UnitFactory;
import mindustry.world.consumers.ConsumeItems;
import mindustry.world.consumers.ConsumeLiquid;
import mindustry.world.consumers.ConsumePower;
import mindustry.world.consumers.ConsumeType;

import java.text.DecimalFormat;
import java.util.*;

public class matrix {
    public static final DecimalFormat df = new DecimalFormat("0.00");

    public final ArrayList<Recipe> recipes = new ArrayList<>();
    public final ArrayList<SourceRecipe> sources = new ArrayList<>();
    public final ArrayList<UnlockableContent> contentIndex = new ArrayList<>();
    public boolean makesPower = false;
    public CalculationResult lastCR = null;

    public matrix(int x1, int y1, int x2, int y2, boolean rateLimit) {
        try {
            //setup coordinates
            final int xl = Math.min(x1, x2);
            final int xr = Math.max(x1, x2);
            final int yb = Math.min(y1, y2);
            final int yt = Math.max(y1, y2);

            if (xl < 0 || yb < 0 || xr > Vars.world.width() || yt > Vars.world.height())
                throw new RuntimeException(mrc.bundle.getString("invalidCoordinates"));

            final ArrayList<Building> ignore = new ArrayList<>();
            final HashMap<Block, RecipeBuilder> recipeBuilders = new HashMap<>();

            for (int x = xl; x <= xr; x++) {
                for (int y = yb; y <= yt; y++) {
                    Tile t = Vars.world.tile(x, y);
                    if (t == null || t.block().isAir() || t.block() instanceof Boulder || t.build == null || ignore.contains(t.build))
                        continue; //ignore if null, decoration or already processed
                    ignore.add(t.build);
                    RecipeBuilder rb = recipeBuilders.get(t.block());
                    if (rb != null) {
                        rb.incrementCount();
                    } else {
                        try {
                            rb = new RecipeBuilder(t.block());
                            //common
                            if (t.block().consumes.has(ConsumeType.power) && t.block().consumes.get(ConsumeType.power) instanceof ConsumePower cp) {
                                rb.setPowerIn(cp.usage * 60f);
                            }
                            if (t.block().consumes.has(ConsumeType.liquid) && t.block().consumes.get(ConsumeType.liquid) instanceof ConsumeLiquid cl && !(t.block() instanceof Drill)) { //if uses liquid but not a drill
                                rb.addIngredient(cl.liquid, cl.amount * 60f);
                            }
                            //specialized
                            if (t.block() instanceof Drill d && t.build instanceof Drill.DrillBuild db) {
                                //check if drill is over ore veins
                                if (db.dominantItems > 0) {
                                    float boost = db.liquids.total() > 0 || !rateLimit ? d.liquidBoostIntensity * d.liquidBoostIntensity : 1f; //if water cooled or getting max rate
                                    if (boost > 1f && t.block().consumes.has(ConsumeType.liquid) && t.block().consumes.get(ConsumeType.liquid) instanceof ConsumeLiquid cl) { // && !(t.block() instanceof Drill) //if uses liquid but not a drill
                                        rb.addIngredient(cl.liquid, cl.amount * 60f);
                                    }
                                    float perSecond = db.dominantItems * boost;
                                    float difficulty = d.drillTime + d.hardnessDrillMultiplier * db.dominantItem.hardness;
                                    rb.addProduct(db.dominantItem, perSecond / difficulty * 60f);
                                }
                            } else if (t.block() instanceof SolidPump sp && t.build instanceof SolidPump.SolidPumpBuild spb) { //water extractor / oil extractor
                                float fraction = Math.max(sp.baseEfficiency + spb.boost + (sp.attribute == null ? 0 : sp.attribute.env()), 0);
                                float maxPump = sp.pumpAmount * fraction;
                                rb.addProduct(sp.result, maxPump * 60f);

                                if (t.block() instanceof Fracker fracker) { //instance off just in case a mod decides to extend this
                                    if (fracker.consumes.has(ConsumeType.item) && fracker.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                                        for (var a : ci.items) {
                                            rb.addIngredient(a.item, a.amount * 60f / fracker.itemUseTime);// 60/use time = crafting time in seconds
                                        }
                                    }
                                }
                            } else if (t.block() instanceof Pump pump) {//thermal / blue / mechanical pump
                                float pumpRate = 0;
                                Liquid liquid = null;
                                if (pump.isMultiblock()) {
                                    for (Tile other : t.build.tile().getLinkedTilesAs(pump, new Seq<>())) { //t.build.tile to make sure we are on the parent tile (cus getLinked is bad)
                                        if (other.floor().liquidDrop == null) continue;
                                        if (liquid != null && other.floor().liquidDrop != liquid) { //pumps can't be over 2 different liquids
                                            liquid = null;
                                            break;
                                        }
                                        liquid = other.floor().liquidDrop;
                                    }
                                    if (liquid != null) {
                                        pumpRate = pump.pumpAmount * pump.size;
                                    }
                                } else if (t.floor().liquidDrop != null) {
                                    liquid = t.floor().liquidDrop;
                                    pumpRate = pump.pumpAmount;
                                }

                                if (liquid != null && pumpRate > 0) {
                                    rb.addProduct(liquid, pumpRate * 60f); //pump rate is unit per tick, 60 tps
                                }
                            } else if (t.block() instanceof PowerGenerator pg) {
                                if (t.block() instanceof ThermalGenerator tg) {
                                    Tile tile = t.build.tile; //get origin block
                                    rb.setPowerOut(pg.powerProduction * tg.sumAttribute(tg.attribute, tile.x, tile.y) * 60f);
                                } else {
                                    rb.setPowerOut(pg.powerProduction * 60f);

                                    Block b = t.block();
                                    if (b instanceof BurnerGenerator bg) {
                                        //powerGenerators++;
                                        //todo: detect burners
                                    /*
                                    pc.usesFlammableItem = true;
                                    pc.rate = 60f / bg.itemDuration;
                                    */
                                    } else if (b instanceof DecayGenerator dg) {
                                        //powerGenerators++;
                                        //todo: detect radioactive reactors
                                    /*
                                    pc.usesRadioactiveItem = true;
                                    pc.rate = 60f / dg.itemDuration;
                                    */
                                    } else if (b instanceof ItemLiquidGenerator ilg) {
                                        //powerGenerators++;
                                        if (ilg.consumes.has(ConsumeType.item) && ilg.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                                            for (var a : ci.items) {
                                                rb.addIngredient(a.item, a.amount * 60f / ilg.itemDuration);
                                            }
                                        }
                                    } else if (b instanceof NuclearReactor nr) {
                                        //powerGenerators++;
                                        if (nr.consumes.has(ConsumeType.item) && nr.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                                            for (var a : ci.items) {
                                                rb.addIngredient(a.item, a.amount * 60f / nr.itemDuration);
                                            }
                                        }
                                    } else if (b instanceof ImpactReactor ir) {
                                        //powerGenerators++;
                                        if (ir.consumes.has(ConsumeType.item) && ir.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                                            for (var a : ci.items) {
                                                rb.addIngredient(a.item, a.amount * 60f / ir.itemDuration);
                                            }
                                        }
                                    }
                                }
                            } else if (t.block() instanceof GenericCrafter gc) {
                                if (gc.outputItem != null) {
                                    rb.addProduct(gc.outputItem.item, gc.outputItem.amount * 60f / gc.craftTime);
                                }
                                if (gc.outputLiquid != null) {
                                    float rate = gc.outputLiquid.amount * 60f;
                                    if (!(t.block() instanceof LiquidConverter)) { //if crafted
                                        rate /= gc.craftTime;
                                    }
                                    rb.addProduct(gc.outputLiquid.liquid, rate);
                                }
                                if (gc.consumes.has(ConsumeType.item) && gc.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                                    for (var a : ci.items) {
                                        rb.addIngredient(a.item, a.amount * 60f / gc.craftTime);
                                    }
                                }
                            } else if (t.block() instanceof Separator separator) {
                                //calculate average output
                                int totalSlots = 0; //how many slots for random selection
                                HashMap<Item, Integer> chances = new HashMap<>(); //how many slots a item has
                                for (ItemStack is : separator.results) {
                                    totalSlots += is.amount;
                                    chances.put(is.item, is.amount);
                                }
                                for (Item item : chances.keySet()) {
                                    //if chances.get(item) / totalSlots == 1.0, it will make x item every crafting cycle, if 0.5 it'll be every other cycle... so on and so forth
                                    rb.addProduct(item, (float) chances.get(item) / totalSlots * 60f / separator.craftTime);
                                }

                                if (separator.consumes.has(ConsumeType.item) && separator.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                                    for (var a : ci.items) {
                                        rb.addIngredient(a.item, a.amount * 60f / separator.craftTime);
                                    }
                                }
                            } else if (t.block() instanceof UnitFactory uf && t.build instanceof UnitFactory.UnitFactoryBuild ufb) {
                                if (ufb.currentPlan != -1) {
                                    UnitFactory.UnitPlan up = uf.plans.get(ufb.currentPlan);
                                    rb.addProduct(up.unit, 3600f / up.time);
                                    for (var a : up.requirements) {
                                        rb.addIngredient(a.item, a.amount * 60f / up.time);
                                    }
                                }
                            } else if (t.block() instanceof Reconstructor r && t.build instanceof Reconstructor.ReconstructorBuild build) {
                            /*
                            pc.isReconstructor = true;
                            pc.upgrades = r.upgrades;
                            */
                                if (r.consumes.has(ConsumeType.item) && r.consumes.get(ConsumeType.item) instanceof ConsumeItems ci) {
                                    for (var a : ci.items) {
                                        rb.addIngredient(a.item, a.amount * 60f / r.constructTime);
                                    }
                                }
                                UnitType u = build.unit();
                                if (u != null) {
                                    rb.addIngredient(build.payload.unit.type, 3600f / r.constructTime);
                                    rb.addProduct(u, 3600f / r.constructTime);//units per minute
                                }
                            }
                            if (rb.isCrafter()) {
                                recipeBuilders.put(t.block(), rb);
                            }
                        } catch (Exception e) {
                            Log.err(e);
                        }
                    }
                }
            }

            for (RecipeBuilder rb : recipeBuilders.values()) {
                recipes.add(rb.build());
            }

            for (Recipe r : recipes) {
                //add each unique ingredient to content list
                for (UnlockableContent uc : r.ingredients.keySet()) {
                    if (!contentIndex.contains(uc)) {
                        contentIndex.add(uc);
                    }
                }
                for (UnlockableContent uc : r.products.keySet()) {
                    if (!contentIndex.contains(uc)) {
                        contentIndex.add(uc);
                    }
                }
            }
            addSupplyRecipes();
            findDuplicates();
            if (rateLimit) {
                setRecipeRateLimit();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addSupplyRecipes() {
        ArrayList<UnlockableContent> external = new ArrayList<>(contentIndex);
        for (Recipe r : recipes) {
            for (UnlockableContent uc : r.products.keySet()) {
                external.remove(uc);
            }
        }
        for (UnlockableContent uc : contentIndex) {
            sources.add(new SourceRecipe(external.contains(uc), Blocks.air, 1, new HashMap<>(), new HashMap<>() {{ put(uc, 1f); }}, 0f, 0f, uc));
            //sources.add((SourceRecipe) new RecipeBuilder(Blocks.air).setSource(external.contains(uc)).addProduct(uc, 1).build());
        }
    }

    public void findDuplicates() {
        for (Recipe r1 : recipes) {
            for (Recipe r2 : recipes) {
                if (r2.duplicate || r1 == r2) continue;
                boolean eq = r1.products.size() == r2.products.size();
                if (eq) {
                    for (var a : r1.products.keySet()) {
                        if (!r2.products.containsKey(a)) {
                            eq = false;
                            break;
                        }
                    }
                }  else {
                    continue;
                }
                if (eq) {
                    float r1v  = 0;
                    for (Float f : r1.products.values()) {
                        if (f != null) {
                            r1v += f;
                        }
                    }
                    float r2v  = 0;
                    for (Float f : r2.products.values()) {
                        if (f != null) {
                            r2v += f;
                        }
                    }
                    if (r1v <= r2v) {
                        r1.duplicate = true;
                        System.out.println("duplicate found");
                        break;
                    }
                }
            }
        }
    }

    public HashMap<UnlockableContent, VariableDouble> getOutput() {
        ArrayList<UnlockableContent> products = new ArrayList<>(contentIndex);
        for (Recipe r : recipes) {
            if (!r.enabled) continue;
            for (UnlockableContent uc : r.ingredients.keySet()) {
                products.remove(uc);
            }
        }
        HashMap<UnlockableContent, VariableDouble> out = new HashMap<>();
        for (Recipe r : recipes) {
            if (!r.enabled) continue;
            for (UnlockableContent uc : r.products.keySet()) {
                if (products.contains(uc)) {
                    out.putIfAbsent(uc, new VariableDouble());
                    out.get(uc).add(r.products.get(uc) * r.count);
                }
            }
        }
        return out;
    }

    public HashMap<UnlockableContent, IO> getIO() {
        HashMap<UnlockableContent, IO> out = new HashMap<>();
        for (Recipe r : recipes) {
            if (!r.enabled) continue;
            //add each unique ingredient to content list
            for (UnlockableContent uc : r.ingredients.keySet()) {
                out.putIfAbsent(uc, new IO());
                out.get(uc).i.add(r.ingredients.get(uc) * r.count * r.efficiency);
            }
            for (UnlockableContent uc : r.products.keySet()) {
                out.putIfAbsent(uc, new IO());
                out.get(uc).o.add(r.products.get(uc) * r.count * r.efficiency);
            }
        }
        return out;
    }

    public CalculationResult rateLimit() {
        if (recipes.isEmpty()) {
            CalculationResult cr = new CalculationResult(ExitCode.NoRecipes, null);
            lastCR = cr;
            return cr;
        }
        HashMap<Recipe, Double> perfectRatio = new HashMap<>();
        try {
            //load OR-Tools
            Loader.loadNativeLibraries();

            //final variables
            final double infinity = Double.POSITIVE_INFINITY;

            //create solver
            MPSolver solver = MPSolver.createSolver("GLOP");

            //add recipe variables and costs
            HashMap<Recipe, MPVariable> hm1 = new HashMap<>();
            HashMap<Recipe, HashMap<UnlockableContent, VariableDouble>> hm2 = new HashMap<>();
            double totalPowerOut = 0;
            for (Recipe r : recipes) {
                if (!r.enabled) continue;
                if (r.isPowerGenerator) totalPowerOut += r.powerOut;
                HashMap<UnlockableContent, VariableDouble> hm3 = new HashMap<>();
                //name of the recipe
                MPVariable mpv = solver.makeNumVar(0.0, r.duplicate ? 1 : infinity, r.block + " Recipe");
                hm1.put(r, mpv);
                //add ingredient var
                for (var e : r.ingredients.entrySet()) {
                    VariableDouble vd = hm3.get(e.getKey());
                    if (vd == null) {
                        vd = new VariableDouble();
                        hm3.put(e.getKey(), vd);
                    }
                    vd.sub(e.getValue());
                }
                //add product var
                for (var e : r.products.entrySet()) {
                    VariableDouble vd = hm3.get(e.getKey());
                    if (vd == null) {
                        vd = new VariableDouble();
                        hm3.put(e.getKey(), vd);
                    }
                    vd.add(e.getValue());
                }
                hm2.put(r, hm3);
            }
            for (SourceRecipe sr : sources) {
                if (sr.enabled) {
                    HashMap<UnlockableContent, VariableDouble> hm3 = new HashMap<>();
                    //name of the recipe and cost
                    hm1.put(sr, solver.makeNumVar(0.0, infinity, sr.out.name + " Source"));
                    //add product var
                    for (var e : sr.products.entrySet()) {
                        VariableDouble vd = hm3.get(e.getKey());
                        if (vd == null) {
                            vd = new VariableDouble();
                            hm3.put(e.getKey(), vd);
                        }
                        vd.add(e.getValue());
                    }

                    hm2.put(sr, hm3);
                }
            }

            //add constraints
            HashMap<UnlockableContent, VariableDouble> desiredProduction = getOutput();
            for (int i = 0; i < contentIndex.size(); i++) {
                final UnlockableContent uc = contentIndex.get(i);
                final VariableDouble temp = desiredProduction.get(uc);
                final double lb;
                if (temp != null) {
                    lb = temp.get();
                } else {
                    lb = 0;
                }

                MPConstraint mpc = solver.makeConstraint(lb, infinity, "uc" + i);
                for (var e : hm2.entrySet()) {
                    var vd = e.getValue().get(uc);
                    if (vd != null) {
                        mpc.setCoefficient(hm1.get(e.getKey()), vd.get());
                    }
                }
            }
            //add power constraint
            if (totalPowerOut > 0) {
                makesPower = true;
                MPConstraint mpc = solver.makeConstraint(totalPowerOut, infinity, "pc");
                for (Recipe r : recipes) {
                    double net = r.powerOut - r.powerIn;
                    if (net != 0) {
                        mpc.setCoefficient(hm1.get(r), net * r.count);
                    }
                }
            }

            //calculate
            MPObjective objective = solver.objective();
            for (SourceRecipe sr : sources) {
                if (sr.enabled) {
                    var var = hm1.get(sr);
                    objective.setCoefficient(var, sr.out instanceof Item i ? i.cost : Items.copper.cost);
                }
            }
            objective.setMinimization();
            //
            final MPSolver.ResultStatus resultStatus = solver.solve();

            if (resultStatus == MPSolver.ResultStatus.OPTIMAL) {
                double max = 0;
                for (var e : hm1.entrySet()) {
                    if (e.getKey() instanceof SourceRecipe) continue;
                    double sv = e.getValue().solutionValue() / e.getKey().count;
                    if (sv > max) {
                        max = sv;
                    }
                }

                double fix = 1 / max;

                for (var e : hm1.entrySet()) {
                    perfectRatio.put(e.getKey(), e.getValue().solutionValue() * fix);
                }
            } else {
                Log.warn("No solution found");
                Log.warn(resultStatus.toString());
                CalculationResult cr = new CalculationResult(ExitCode.NotFound, null);
                lastCR = cr;
                return cr;
            }
        } catch (Exception e) {
            e.printStackTrace();
            CalculationResult cr = new CalculationResult(ExitCode.Error, null);
            lastCR = cr;
            return cr;
        }

        CalculationResult cr = new CalculationResult(ExitCode.Success, perfectRatio);
        lastCR = cr;
        return cr;
    }

    public void setRecipeMaximum() {
        for (Recipe r : recipes) {
            r.efficiency = 1f;
        }
    }

    public void setRecipeRateLimit() {
        try {
            var cr = rateLimit();
            if (cr.code == ExitCode.Success) {
                for (var e : cr.pr.entrySet()) {
                    e.getKey().efficiency = (float) (e.getValue() / e.getKey().count);
                }
            } else {
                for (Recipe r : recipes) {
                    r.efficiency = 1f;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public HashMap<UnlockableContent, VariableDouble> getO() {
        HashMap<UnlockableContent, VariableDouble> out = new HashMap<>();
        for (Recipe r : recipes) {
            for (UnlockableContent uc : r.products.keySet()) {
                out.putIfAbsent(uc, new VariableDouble());
                out.get(uc).add(r.products.get(uc) * r.count * r.efficiency);
            }
        }
        return out;
    }

    public HashMap<UnlockableContent, VariableDouble> getMaxO() {
        HashMap<UnlockableContent, VariableDouble> out = new HashMap<>();
        for (Recipe r : recipes) {
            for (UnlockableContent uc : r.products.keySet()) {
                out.putIfAbsent(uc, new VariableDouble());
                out.get(uc).add(r.products.get(uc) * r.count);
            }
        }
        return out;
    }

    public String parse(boolean rateLimit, boolean complex) {
        StringBuilder out = new StringBuilder();
        //get total in/out
        HashMap<UnlockableContent, IO> io = getIO();
        HashMap<UnlockableContent, VariableDouble> o = getO();
        HashMap<UnlockableContent, VariableDouble> oMax = getMaxO();
        for (var e : io.entrySet()) {
            UnlockableContent uc = e.getKey();
            double net =  e.getValue().net();
            out.append("\n[white]");
            if (rateLimit) {
                VariableDouble vd1 = o.get(uc);
                VariableDouble vd2 = oMax.get(uc);
                if (vd1 != null && vd2 != null){
                    double multi = (vd1.get() / vd2.get()) * 100d;
                    if (Double.isFinite(multi)) {
                        out.append("([lightgray]").append(df.format(multi)).append("%[white]) ");
                    }
                }
            }
            out.append(uc.emoji());
            String speed = "/s";
            if (uc instanceof Item i) {
                out.append("[#").append(i.color).append("]");
            } else if (uc instanceof Liquid l) {
                out.append("[#").append(l.color).append("]");
            } else if (uc instanceof UnitType) {
                speed = "/m";
            }
            out.append(uc.name).append(": ");
            if (0.005 > net && net > -0.005) {
                out.append("[lightgray]0.00").append(speed);
            } else {
                out.append(net > 0 ? "[lime]+" : "[scarlet]").append(df.format(net)).append(speed);
            }
            if (complex) {
                final double totalI = e.getValue().i.get();
                final double totalO = e.getValue().o.get();
                final ArrayList<String> subBuilder = new ArrayList<>();
                final StringBuilder builder = new StringBuilder();
                for (Recipe r : recipes) {
                    if (r.enabled) {
                        //builder.clear()
                        builder.setLength(0);
                        //add consumer subsection
                        Float f = r.ingredients.get(uc);
                        if (f != null) {
                            f *= r.count * r.efficiency;
                            builder.append(" [white]([lightgray]").append(df.format((f / totalI) * 100d)).append("%[white]) ").append(r.block.emoji()).append(r.block.name).append(": [scarlet]-").append(df.format(f)).append(speed);
                        }
                        //add producer subsection
                        f = r.products.get(uc);
                        if (f != null) {
                            f *= r.count * r.efficiency;
                            builder.append(" [white]([lightgray]").append(df.format((f / totalO) * 100d)).append("%[white]) ").append(r.block.emoji()).append(r.block.name).append(": [lime]+").append(df.format(f)).append(speed);
                        }
                        //add to sub builder
                        if (builder.length() > 0) {
                            subBuilder.add(builder.toString());
                        }
                    }
                }
                for (int i = 0; i < subBuilder.size(); i++) {
                    out.append("\n [gray]").append(i == subBuilder.size() - 1 ? "└" : "├").append(subBuilder.get(i));
                }
            }

        }
        if (makesPower) {
            double powerIn = 0;
            double powerOut = 0;
            double powerOutMax = 0;
            for (Recipe r : recipes) {
                if (r.enabled) {
                    powerIn += r.powerIn * r.efficiency;
                    powerOut += r.powerOut * r.efficiency;
                    powerOutMax += r.powerOut;
                }
            }
            out.append("\n[white]([lightgray]").append(df.format((powerOut / powerOutMax) * 100d)).append("%[white]) [yellow]").append(Iconc.power).append(mrc.translatedStringPower).append(": ");
            double net = powerOut - powerIn;
            if (0.005 > net && net > -0.005) {
                out.append("[lightgray]0.00/s");
            } else {
                out.append(net > 0 ? "[lime]+" : "[scarlet]").append(df.format(net)).append("/s");
            }
            if (complex) {
                final ArrayList<String> subBuilder = new ArrayList<>();
                final StringBuilder builder = new StringBuilder();

                for (Recipe r : recipes) {
                    if (r.enabled) {
                        //add consumer subsection
                        if (r.powerIn > 0) {
                            //builder.clear()
                            builder.setLength(0);

                            double pi = r.powerIn * r.efficiency;
                            builder.append(" [white]([lightgray]").append(df.format((pi / powerIn) * 100d)).append("%[white]) ").append(r.block.emoji()).append(r.block.name).append(": [scarlet]-").append(df.format(pi)).append("/s");
                            subBuilder.add(builder.toString());
                        }
                        //add producer subsection
                        if (r.powerOut > 0) {
                            //builder.clear()
                            builder.setLength(0);

                            double pi = r.powerOut * r.efficiency;
                            builder.append(" [white]([lightgray]").append(df.format((pi / powerOut) * 100d)).append("%[white]) ").append(r.block.emoji()).append(r.block.name).append(": [lime]+").append(df.format(pi)).append("/s");
                            subBuilder.add(builder.toString());
                        }
                    }
                }

                for (int i = 0; i < subBuilder.size(); i++) {
                    out.append("\n [gray]").append(i == subBuilder.size() - 1 ? "└" : "├").append(subBuilder.get(i));
                }
            }
        }
        return out.toString();
    }

    //recipe classes

    public static class Recipe {
        public boolean enabled = true;
        public float efficiency = 1f;
        public boolean duplicate = false;

        public final Block block;
        public final boolean isPowerGenerator;

        public final int origCount;
        public int count;

        public final HashMap<UnlockableContent, Float> ingredients = new HashMap<>();
        public final HashMap<UnlockableContent, Float> products = new HashMap<>();
        public final float powerIn;
        public final float powerOut;

        public Recipe(Block block) {
            this.block = block;
            this.isPowerGenerator = block instanceof PowerGenerator;
            this.origCount = 1;
            this.count = 1;
            this.powerIn = 0;
            this.powerOut = 0;
        }

        public Recipe(boolean enabled, Block block, int count, HashMap<UnlockableContent, Float> ingredients, HashMap<UnlockableContent, Float> products, float powerIn, float powerOut) {
            this.enabled = enabled;
            this.block = block;
            this.isPowerGenerator = block instanceof PowerGenerator;
            this.origCount = count;
            this.count = count;
            this.ingredients.putAll(ingredients);
            this.products.putAll(products);
            this.powerIn = powerIn;
            this.powerOut = powerOut;
        }

        public boolean toggleRecipe() {
            enabled = !enabled;
            return enabled;
        }
    }

    public static class SourceRecipe extends Recipe {
        public final UnlockableContent out;

        public SourceRecipe(boolean enabled, Block block, int count, HashMap<UnlockableContent, Float> ingredients, HashMap<UnlockableContent, Float> products, float powerIn, float powerOut, UnlockableContent out) {
            super(enabled, block, count, ingredients, products, powerIn, powerOut);

            this.out = out;
        }
    }

    private static class RecipeBuilder {
        public boolean enabled = true;
        public boolean source = false;
        public final Block block;
        public int count = 1;
        public HashMap<UnlockableContent, Float> ingredients = new HashMap<>();
        public HashMap<UnlockableContent, Float> products = new HashMap<>();
        public float powerIn = 0;
        public float powerOut = 0;

        public UnlockableContent temp;

        public RecipeBuilder(Block block) {
            this.block = block;
        }

        public RecipeBuilder setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public RecipeBuilder setSource(boolean source) {
            this.source = source;
            return this;
        }

        public void incrementCount() {
            count++;
        }

        public void addIngredient(UnlockableContent uc, float rate) {
            if (rate <= 0) throw new RuntimeException("rate for ingredient <= 0");
            this.ingredients.put(uc, rate);
        }

        public void addProduct(UnlockableContent uc, float rate) {
            if (rate <= 0) throw new RuntimeException("rate for product <= 0");
            this.products.put(uc, rate);
            temp = uc;
        }

        public void setPowerIn(float powerIn) {
            this.powerIn = powerIn;
        }

        public void setPowerOut(float powerOut) {
            this.powerOut = powerOut;
        }

        public Recipe build() {
            if (source) {
                return new SourceRecipe(enabled, block, count, ingredients, products, powerIn, powerOut, temp);
            } else {
                return new Recipe(enabled, block, count, ingredients, products, powerIn, powerOut);
            }
        }

        public boolean isCrafter() {
            return !ingredients.isEmpty() || !products.isEmpty();
        }
    }

    //calculation results

    public enum ExitCode {
        Success,
        NotFound,
        Error,
        NoRecipes
    }

    public static final class CalculationResult {
        private final ExitCode code;
        private final HashMap<Recipe, Double> pr;

        public CalculationResult(ExitCode code, HashMap<Recipe, Double> pr) {
            this.code = code;
            this.pr = pr;
        }

        public ExitCode code() {
            return code;
        }

        public HashMap<Recipe, Double> pr() {
            return pr;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (CalculationResult) obj;
            return Objects.equals(this.code, that.code) &&
                    Objects.equals(this.pr, that.pr);
        }

        @Override
        public int hashCode() {
            return Objects.hash(code, pr);
        }

        @Override
        public String toString() {
            return "CalculationResult[" +
                    "code=" + code + ", " +
                    "pr=" + pr + ']';
        }

    }

    //math

    public static class VariableDouble {
        double i = 0;

        public VariableDouble() {
        }

        public void add(double amount) {
            i += amount;
        }

        public void sub(double amount) {
            i -= amount;
        }

        public double get() {
            return i;
        }
    }

    //parse recipes

    public static class IO {
        public final VariableDouble i = new VariableDouble();
        public final VariableDouble o = new VariableDouble();

        public double net() {
            return o.get() - i.get();
        }
    }
}
