package de.minestar.moneypit.modules.door;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import de.minestar.moneypit.manager.ModuleManager;

public class Module_Door_DarkOak extends Module_Door_Abstract {

    private final static Material TYPE = Material.DARK_OAK_DOOR;
    private final static String NAME = TYPE.name();

    public Module_Door_DarkOak(YamlConfiguration ymlFile) {
        super(ymlFile, NAME, TYPE);
    }

    public Module_Door_DarkOak(ModuleManager moduleManager, YamlConfiguration ymlFile) {
        super(moduleManager, ymlFile, NAME, TYPE);
    }
}
