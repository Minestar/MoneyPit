package de.minestar.moneypit.manager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.bukkit.configuration.file.YamlConfiguration;

import de.minestar.minestarlibrary.utils.ConsoleUtils;
import de.minestar.moneypit.Core;
import de.minestar.moneypit.modules.Module;
import de.minestar.moneypit.modules.Module_Chest;
import de.minestar.moneypit.modules.Module_SignPost;
import de.minestar.moneypit.modules.Module_WallSign;

public class ModuleManager {

    private static String FILENAME = "modules.yml";

    private boolean protectChests = true, protectLevers = true, protectButtons = true, protectFenceGates = true;
    private boolean protectSigns = true, protectWoodDoors = true, protectIronDoors = true, protectTrapDoors = true;

    private boolean autoLockChests = true, autoLockLevers = true, autoLockButtons = true, autoLockFenceGates = true;
    private boolean autoLockSigns = true, autoLockWoodDoors = true, autoLockIronDoors = true, autoLockTrapDoors = true;

    private HashMap<Integer, Module> registeredModules = new HashMap<Integer, Module>();

    public void init() {
        this.loadConfig();
        this.printInfo();
    }

    private void printInfo() {
        // PRINT INFO
        ConsoleUtils.printInfo(Core.NAME, this.registeredModules.size() + " Modules registered!");
    }

    public void registerModule(Module module) {
        // ONLY REGISTER IF NOT ALREADY REGISTERED
        if (!this.isModuleRegistered(module.getRegisteredTypeID())) {
            this.registeredModules.put(module.getRegisteredTypeID(), module);
        }
    }

    public boolean isModuleRegistered(int TypeId) {
        return this.registeredModules.containsKey(TypeId);
    }

    public Module getModule(int TypeId) {
        return this.registeredModules.get(TypeId);
    }

    private void loadConfig() {
        File file = new File(Core.INSTANCE.getDataFolder(), FILENAME);
        if (!file.exists()) {
            this.writeDefaultConfig();
        }

        try {
            // CREATE
            YamlConfiguration ymlFile = new YamlConfiguration();

            // LOAD
            ymlFile.load(file);

            // @formatter:off
            // INIT MODULES
            new Module_Chest    (this, ymlFile);
            new Module_SignPost (this, ymlFile);
            new Module_WallSign (this, ymlFile);
            // @formatter:on

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writeDefaultConfig() {
        File file = new File(Core.INSTANCE.getDataFolder(), FILENAME);
        if (file.exists()) {
            file.delete();
        }

        try {
            // CREATE
            YamlConfiguration ymlFile = new YamlConfiguration();

            // @formatter:off
            // PROTECTIONS
            ymlFile.set("protect.chests",           protectChests);
            ymlFile.set("protect.signs",            protectSigns);
            ymlFile.set("protect.levers",           protectLevers);
            ymlFile.set("protect.buttons",          protectButtons);
            ymlFile.set("protect.doors.wood",       protectWoodDoors);
            ymlFile.set("protect.doors.iron",       protectIronDoors);
            ymlFile.set("protect.doors.trap",       protectTrapDoors);
            ymlFile.set("protect.doors.fencegate",  protectFenceGates);
            
            // AUTOLOCK
            ymlFile.set("protect.chests.autolock",          autoLockChests);
            ymlFile.set("protect.signs.autolock",           autoLockSigns);
            ymlFile.set("protect.levers.autolock",          autoLockLevers);
            ymlFile.set("protect.buttons.autolock",         autoLockButtons);
            ymlFile.set("protect.doors.wood.autolock",      autoLockWoodDoors);
            ymlFile.set("protect.doors.iron.autolock",      autoLockIronDoors);
            ymlFile.set("protect.doors.trap.autolock",      autoLockTrapDoors);
            ymlFile.set("protect.doors.fencegate.autolock", autoLockFenceGates);
            // @formatter:on

            // SAVE
            ymlFile.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
