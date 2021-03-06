package de.minestar.moneypit.manager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import de.minestar.minestarlibrary.utils.ConsoleUtils;
import de.minestar.moneypit.MoneyPitCore;
import de.minestar.moneypit.modules.Module;
import de.minestar.moneypit.modules.Module_Banner_Hanging;
import de.minestar.moneypit.modules.Module_Banner_Standing;
import de.minestar.moneypit.modules.Module_Chest;
import de.minestar.moneypit.modules.Module_Dispenser;
import de.minestar.moneypit.modules.Module_Dropper;
import de.minestar.moneypit.modules.Module_Furnace;
import de.minestar.moneypit.modules.Module_FurnaceBurning;
import de.minestar.moneypit.modules.Module_Hopper;
import de.minestar.moneypit.modules.Module_ItemFrame;
import de.minestar.moneypit.modules.Module_Jukebox;
import de.minestar.moneypit.modules.Module_Lever;
import de.minestar.moneypit.modules.Module_Painting;
import de.minestar.moneypit.modules.Module_SignPost;
import de.minestar.moneypit.modules.Module_Skull;
import de.minestar.moneypit.modules.Module_StoneButton;
import de.minestar.moneypit.modules.Module_TrappedChest;
import de.minestar.moneypit.modules.Module_WallSign;
import de.minestar.moneypit.modules.door.Module_Door_Acacia;
import de.minestar.moneypit.modules.door.Module_Door_Birch;
import de.minestar.moneypit.modules.door.Module_Door_DarkOak;
import de.minestar.moneypit.modules.door.Module_Door_Iron;
import de.minestar.moneypit.modules.door.Module_Door_Jungle;
import de.minestar.moneypit.modules.door.Module_Door_Spruce;
import de.minestar.moneypit.modules.door.Module_Door_Wooden;
import de.minestar.moneypit.modules.fencegate.Module_FenceGate_Acacia;
import de.minestar.moneypit.modules.fencegate.Module_FenceGate_Birch;
import de.minestar.moneypit.modules.fencegate.Module_FenceGate_DarkOak;
import de.minestar.moneypit.modules.fencegate.Module_FenceGate_Jungle;
import de.minestar.moneypit.modules.fencegate.Module_FenceGate_Spruce;
import de.minestar.moneypit.modules.fencegate.Module_FenceGate_Wooden;
import de.minestar.moneypit.modules.plate.Module_Plate_Golden;
import de.minestar.moneypit.modules.plate.Module_Plate_Iron;
import de.minestar.moneypit.modules.plate.Module_Plate_Stone;
import de.minestar.moneypit.modules.plate.Module_Plate_Wooden;
import de.minestar.moneypit.modules.trapdoor.Module_TrapDoor_Iron;
import de.minestar.moneypit.modules.trapdoor.Module_TrapDoor_Wooden;

public class ModuleManager {

    private static String FILENAME = "modules.yml";

    private HashMap<Material, Module> registeredModules = new HashMap<Material, Module>();

    public void init() {
        this.loadConfig();
        this.printInfo();
    }

    private void printInfo() {
        // PRINT INFO
        ConsoleUtils.printInfo(MoneyPitCore.NAME, this.registeredModules.size() + " Modules registered!");
    }

    public void registerModule(Module module) {
        // ONLY REGISTER IF NOT ALREADY REGISTERED
        if (!this.isModuleRegistered(module.getRegisteredType())) {
            this.registeredModules.put(module.getRegisteredType(), module);
        }
    }

    public boolean isModuleRegistered(Material material) {
        return this.registeredModules.containsKey(material);
    }

    public Module getRegisteredModule(Material material) {
        return this.registeredModules.get(material);
    }

    private void loadConfig() {
        File file = new File(MoneyPitCore.INSTANCE.getDataFolder(), FILENAME);
        if (!file.exists()) {
            this.writeDefaultConfig();
        }

        try {
            // CREATE
            YamlConfiguration ymlFile = new YamlConfiguration();

            // LOAD
            ymlFile.load(file);

            // INIT MODULES
            this.initModules(true, ymlFile);

            // SAVE
            ymlFile.save(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initModules(boolean withInstance, YamlConfiguration ymlFile) {
        // @formatter:off
        if (withInstance) {
            // LOAD MODULES            
            // signs
            new Module_SignPost         (this, ymlFile);
            new Module_WallSign         (this, ymlFile);
            // banner
            new Module_Banner_Hanging   (this, ymlFile);
            new Module_Banner_Standing  (this, ymlFile);
            // doors
            new Module_Door_Acacia      (this, ymlFile);
            new Module_Door_Birch       (this, ymlFile);
            new Module_Door_DarkOak     (this, ymlFile);
            new Module_Door_Iron        (this, ymlFile);
            new Module_Door_Jungle      (this, ymlFile);
            new Module_Door_Spruce      (this, ymlFile);
            new Module_Door_Wooden      (this, ymlFile);
            // trapdoors
            new Module_TrapDoor_Iron    (this, ymlFile);
            new Module_TrapDoor_Wooden  (this, ymlFile);
            // fencegates
            new Module_FenceGate_Acacia (this, ymlFile);
            new Module_FenceGate_Birch  (this, ymlFile);
            new Module_FenceGate_DarkOak(this, ymlFile);
            new Module_FenceGate_Jungle (this, ymlFile);
            new Module_FenceGate_Spruce (this, ymlFile);
            new Module_FenceGate_Wooden (this, ymlFile);
            // plates
            new Module_Plate_Golden     (this, ymlFile);
            new Module_Plate_Iron       (this, ymlFile);
            new Module_Plate_Stone      (this, ymlFile);
            new Module_Plate_Wooden     (this, ymlFile);            
            // other stuff
            new Module_Chest            (this, ymlFile);
            new Module_Dispenser        (this, ymlFile);
            new Module_Dropper          (this, ymlFile);   
            new Module_Furnace          (this, ymlFile); 
            new Module_FurnaceBurning   (this, ymlFile);
            new Module_Hopper           (this, ymlFile);
            new Module_ItemFrame        (this, ymlFile);
            new Module_Jukebox          (this, ymlFile);
            new Module_Lever            (this, ymlFile);
            new Module_Painting         (this, ymlFile);
            new Module_Skull            (this, ymlFile);
            new Module_StoneButton      (this, ymlFile);
            new Module_TrappedChest     (this, ymlFile); 
        } else {
            // CREATE DEFAULT CONFIGS
            // signs
            new Module_SignPost         (ymlFile);
            new Module_WallSign         (ymlFile);
            // banner
            new Module_Banner_Hanging   (ymlFile);
            new Module_Banner_Standing  (ymlFile);
            // doors
            new Module_Door_Acacia      (ymlFile);
            new Module_Door_Birch       (ymlFile);
            new Module_Door_DarkOak     (ymlFile);
            new Module_Door_Iron        (ymlFile);
            new Module_Door_Jungle      (ymlFile);
            new Module_Door_Spruce      (ymlFile);
            new Module_Door_Wooden      (ymlFile);
            // trapdoors
            new Module_TrapDoor_Iron    (ymlFile);
            new Module_TrapDoor_Wooden  (ymlFile);
            // fencegates
            new Module_FenceGate_Acacia (ymlFile);
            new Module_FenceGate_Birch  (ymlFile);
            new Module_FenceGate_DarkOak(ymlFile);
            new Module_FenceGate_Jungle (ymlFile);
            new Module_FenceGate_Spruce (ymlFile);
            new Module_FenceGate_Wooden (ymlFile);
            // plates
            new Module_Plate_Golden     (ymlFile);
            new Module_Plate_Iron       (ymlFile);
            new Module_Plate_Stone      (ymlFile);
            new Module_Plate_Wooden     (ymlFile);            
            // other stuff
            new Module_Chest            (ymlFile);
            new Module_Dispenser        (ymlFile);
            new Module_Dropper          (ymlFile);   
            new Module_Furnace          (ymlFile); 
            new Module_FurnaceBurning   (ymlFile);
            new Module_Hopper           (ymlFile);
            new Module_ItemFrame        (ymlFile);
            new Module_Jukebox          (ymlFile);
            new Module_Lever            (ymlFile);
            new Module_Painting         (ymlFile);
            new Module_Skull            (ymlFile);
            new Module_StoneButton      (ymlFile);
            new Module_TrappedChest     (ymlFile);     
        }
        // @formatter:on
    }

    private void writeDefaultConfig() {
        File file = new File(MoneyPitCore.INSTANCE.getDataFolder(), FILENAME);
        if (file.exists()) {
            file.delete();
        }

        try {
            // CREATE
            YamlConfiguration ymlFile = new YamlConfiguration();

            // write default configs for modules
            this.initModules(false, ymlFile);

            // SAVE
            ymlFile.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
