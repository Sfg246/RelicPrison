package site.mcrelicworld.relicprison.admin;

import org.bukkit.Bukkit;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.mine.MineDefinition;
import site.mcrelicworld.relicprison.mine.PackagedMineResourceStatus;
import site.mcrelicworld.relicprison.mine.composition.BlockTypeRef;

import java.util.ArrayList;
import java.util.List;

public final class ValidationService {
    private final RelicPrisonPlugin plugin;
    public ValidationService(RelicPrisonPlugin plugin){this.plugin=plugin;}

    public ValidationReport validate(){
        List<String> errors=new ArrayList<>(),warnings=new ArrayList<>(),info=new ArrayList<>();
        for(MineDefinition mine:plugin.mineService().mines()){
            var world=Bukkit.getWorld(mine.worldId());
            if(world==null)errors.add("Mine "+mine.id()+" world is not loaded: "+mine.worldName());
            if(mine.spawn()==null)warnings.add("Mine "+mine.id()+" has no explicit spawn.");
            else if(mine.bounds().contains((int)Math.floor(mine.spawn().x()),(int)Math.floor(mine.spawn().y()),(int)Math.floor(mine.spawn().z())))
                warnings.add("Mine "+mine.id()+" spawn is inside the refill cuboid.");
            for(var entry:mine.composition().entries()){
                BlockTypeRef block=entry.block();
                if(block.kind()==BlockTypeRef.Kind.ITEMSADDER){
                    if(!plugin.config().snapshot().features().itemsAdder())errors.add("Mine "+mine.id()+" uses "+block.id()+" but ItemsAdder support is disabled.");
                    else if(!plugin.itemsAdder().connected())errors.add("Mine "+mine.id()+" uses "+block.id()+" but ItemsAdder is unavailable.");
                    else if(!plugin.itemsAdder().isRegisteredBlock(block.id()))errors.add("Mine "+mine.id()+" references unknown ItemsAdder block "+block.id());
                }
            }
        }
        for(var entry:plugin.sellService().catalog().customPrices().entrySet()){
            if(!plugin.config().snapshot().features().itemsAdder())warnings.add("Custom sell price "+entry.getKey()+" is inactive because ItemsAdder support is disabled.");
            else if(!plugin.itemsAdder().connected())warnings.add("Custom sell price "+entry.getKey()+" cannot be verified while ItemsAdder is unavailable.");
            else if(plugin.itemsAdder().item(entry.getKey(),1).isEmpty())errors.add("Unknown ItemsAdder item in sell-prices.yml: "+entry.getKey());
        }
        for(var entry:plugin.miningService().customDrops().rules().entrySet()){
            String blockId=entry.getKey();
            if(blockId.contains(":") && plugin.itemsAdder().connected() && !plugin.itemsAdder().isRegisteredBlock(blockId)) errors.add("Unknown ItemsAdder block in custom-drops.yml: "+blockId);
            for(var rule:entry.getValue()){
                if(rule.itemId().contains(":") && plugin.itemsAdder().connected() && plugin.itemsAdder().item(rule.itemId(),1).isEmpty()) errors.add("Unknown ItemsAdder item in custom-drops.yml: "+rule.itemId());
            }
        }
        if(plugin.config().snapshot().features().advancedEnchantments()&&!plugin.advancedEnchantments().connected())warnings.add("AdvancedEnchantments feature is enabled but integration is disconnected.");
        if(plugin.config().snapshot().features().itemsAdder()&&!plugin.itemsAdder().connected())errors.add("ItemsAdder feature is enabled but integration is disconnected.");
        PackagedMineResourceStatus packagedMines = plugin.packagedMineResourceStatus();
        if(packagedMines.missingApprovedAsset()) {
            errors.add("Fresh-install 33-mine mines.yml asset is unresolved: " + String.join("; ", packagedMines.errors()));
        } else {
            info.add("Packaged fresh-install mines.yml contains the approved 33 mines.");
        }
        info.add(plugin.mineService().mines().size()+" mines, "+plugin.rankService().ranks().size()+" ranks, "+plugin.prestigeService().prestiges().size()+" prestiges loaded.");
        info.add(plugin.sellService().catalog().prices().size()+" vanilla and "+plugin.sellService().catalog().customPrices().size()+" custom sell prices loaded.");
        info.add("Database schema version supported: "+site.mcrelicworld.relicprison.database.DatabaseManager.SUPPORTED_SCHEMA_VERSION);
        return new ValidationReport(System.currentTimeMillis(),errors,warnings,info);
    }
}
