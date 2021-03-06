package de.minestar.moneypit.modules.fencegate;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import com.bukkit.gemo.patchworking.IProtection;

import de.minestar.moneypit.manager.ModuleManager;
import de.minestar.moneypit.modules.Module;

public abstract class Module_FenceGate_Abstract extends Module {

    private final String _name;

    public Module_FenceGate_Abstract(YamlConfiguration ymlFile, String name) {
        _name = name;
        this.writeDefaultConfig(_name, ymlFile);
    }

    public Module_FenceGate_Abstract(ModuleManager moduleManager, YamlConfiguration ymlFile, String name, Material type) {
        super();
        _name = name;
        this.init(moduleManager, ymlFile, type, _name);
        this.setBlockRedstone(ymlFile.getBoolean("protect." + _name + ".handleRedstone", true));
    }

    @Override
    protected final void writeExtraConfig(String moduleName, YamlConfiguration ymlFile) {
        ymlFile.set("protect." + _name + ".handleRedstone", true);
    }

    @Override
    public final boolean addProtection(IProtection protection, byte subData, boolean saveToDatabase) {
        // register the protection
        return getProtectionManager().addProtection(protection);
    }
}
