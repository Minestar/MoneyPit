package de.minestar.moneypit.modules;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.bukkit.gemo.patchworking.BlockVector;
import com.bukkit.gemo.patchworking.IProtection;

import de.minestar.moneypit.MoneyPitCore;
import de.minestar.moneypit.data.EventResult;
import de.minestar.moneypit.manager.ModuleManager;
import de.minestar.moneypit.manager.ProtectionManager;

public abstract class Module {

    private ProtectionManager protectionManager;
    private String moduleName = "UNKNOWN";
    private Material registeredType;
    private boolean enabled = true;
    private boolean autoLock = false;
    private boolean doNeighbourCheck = false;
    private boolean blockRedstone = false;
    private boolean isGiftable = false;

    protected Module() {
        this.protectionManager = MoneyPitCore.protectionManager;
    }

    public void setGiftable(boolean isGiftable) {
        this.isGiftable = isGiftable;
    }

    public boolean isGiftable() {
        return isGiftable;
    }

    public boolean doNeighbourCheck() {
        return doNeighbourCheck;
    }

    public boolean blockRedstone() {
        return blockRedstone;
    }

    public void setDoNeighbourCheck(boolean doNeighbourCheck) {
        this.doNeighbourCheck = doNeighbourCheck;
    }

    public void setBlockRedstone(boolean blockRedstone) {
        this.blockRedstone = blockRedstone;
    }

    public Material getRegisteredType() {
        return this.registeredType;
    }

    public String getModuleName() {
        return this.moduleName;
    }

    public boolean isAutoLock() {
        return autoLock;
    }

    protected void setAutolock(boolean autoLock) {
        this.autoLock = autoLock;
    }

    protected ProtectionManager getProtectionManager() {
        return this.protectionManager;
    }

    protected void init(ModuleManager moduleManager, YamlConfiguration ymlFile, Material registeredType, String moduleName) {
        this.registeredType = registeredType;
        this.moduleName = moduleName;
        this.enabled = ymlFile.getBoolean("protect." + this.getModuleName() + ".enabled", true);
        this.autoLock = ymlFile.getBoolean("protect." + this.getModuleName() + ".lockOnPlace", false);
        if (this.enabled) {
            moduleManager.registerModule(this);
        }
        this.writeDefaultConfig(this.getModuleName(), ymlFile);
    }

    protected void writeDefaultConfig(String moduleName, YamlConfiguration ymlFile) {
        ymlFile.set("protect." + moduleName + ".enabled", this.enabled);
        ymlFile.set("protect." + moduleName + ".lockOnPlace", this.autoLock);
        this.writeExtraConfig(moduleName, ymlFile);
    }

    protected void writeExtraConfig(String moduleName, YamlConfiguration ymlFile) {
    }

    public void removeProtection(BlockVector vector) {
        getProtectionManager().removeProtection(vector);
    }

    /**
     * This method is called on a blockplace of a registered module
     * 
     * @param event
     * @param vector
     * @return <b>true</b> if the blockplace-event should abort after the check, <b>false</b> if it should continue
     */
    public EventResult onPlace(Player player, BlockVector vector) {
        return new EventResult(false, false, null);
    }

    public abstract boolean addProtection(IProtection protection, byte subData, boolean saveToDatabase);
}
