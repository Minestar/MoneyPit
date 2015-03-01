package de.minestar.moneypit.modules;

import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.bukkit.gemo.patchworking.BlockVector;
import com.bukkit.gemo.patchworking.IProtection;

import de.minestar.minestarlibrary.utils.PlayerUtils;
import de.minestar.moneypit.MoneyPitCore;
import de.minestar.moneypit.data.EventResult;
import de.minestar.moneypit.data.protection.Protection;
import de.minestar.moneypit.manager.ModuleManager;
import de.minestar.moneypit.utils.DoorHelper;
import de.minestar.moneypit.utils.PhysicsHelper;

public class Module_IronDoor extends Module {

    private final String NAME = "irondoor";

    public Module_IronDoor(YamlConfiguration ymlFile) {
        this.writeDefaultConfig(NAME, ymlFile);
    }

    public Module_IronDoor(ModuleManager moduleManager, YamlConfiguration ymlFile) {
        super();
        this.init(moduleManager, ymlFile, Material.IRON_DOOR_BLOCK.getId(), NAME);
        this.setDoNeighbourCheck(true);
        this.setBlockRedstone(ymlFile.getBoolean("protect." + NAME + ".handleRedstone", true));
    }

    @Override
    protected void writeExtraConfig(String moduleName, YamlConfiguration ymlFile) {
        ymlFile.set("protect." + NAME + ".handleRedstone", true);
    }

    @Override
    public boolean addProtection(IProtection protection, byte subData, boolean saveToDatabase) {
        // protect the block above
        IProtection subProtection = new Protection(protection.getVector().getRelative(0, 1, 0), protection);
        protection.addSubProtection(subProtection);
        MoneyPitCore.databaseManager.createSubProtection(subProtection, saveToDatabase);

        // protect the block below
        subProtection = new Protection(protection.getVector().getRelative(0, -1, 0), protection);
        protection.addSubProtection(subProtection);
        MoneyPitCore.databaseManager.createSubProtection(subProtection, saveToDatabase);

        // fetch non-solid-blocks
        PhysicsHelper.protectNonSolidBlocks(protection, subProtection.getVector(), saveToDatabase);

        // protect the second door
        Block[] secondDoor = DoorHelper.getOppositeDoorBlocks(protection.getVector().getLocation().getBlock());
        if (secondDoor[0] != null && secondDoor[1] != null) {
            Block[] firstDoor = DoorHelper.getDoorBlocks(protection.getVector().getLocation().getBlock());
            if (DoorHelper.validateDoorBlocks(firstDoor, secondDoor)) {
                // protect the upper block of the second door
                subProtection = new Protection(new BlockVector(secondDoor[1].getLocation()), protection);
                protection.addSubProtection(subProtection);
                MoneyPitCore.databaseManager.createSubProtection(subProtection, saveToDatabase);

                // protect the lower block of the second door
                subProtection = new Protection(new BlockVector(secondDoor[0].getLocation()), protection);
                protection.addSubProtection(subProtection);
                MoneyPitCore.databaseManager.createSubProtection(subProtection, saveToDatabase);

                // protect the block below
                subProtection = new Protection(subProtection.getVector().getRelative(0, -1, 0), protection);
                protection.addSubProtection(subProtection);
                MoneyPitCore.databaseManager.createSubProtection(subProtection, saveToDatabase);

                // fetch non-solid-blocks
                PhysicsHelper.protectNonSolidBlocks(protection, subProtection.getVector(), saveToDatabase);
            }
        }

        // register the protection
        return getProtectionManager().addProtection(protection);
    }

    @Override
    public EventResult onPlace(Player player, BlockVector vector) {
        // search a second chest
        BlockVector doubleDoor = DoorHelper.getSecondWoodDoor(vector);
        if (doubleDoor == null) {
            return new EventResult(false, false, null);
        }

        // check if there is a protection
        IProtection protection = MoneyPitCore.protectionManager.getProtection(doubleDoor);
        if (protection == null) {
            return new EventResult(false, false, null);
        }

        // check permissions
        if (!protection.canEdit(player)) {
            PlayerUtils.sendError(player, MoneyPitCore.NAME, "You cannot place a door here.");
            PlayerUtils.sendInfo(player, "The neighbour is a protected door.");
            return new EventResult(true, true, protection);
        }

        // protect the second door
        Block[] secondDoor = DoorHelper.getDoorBlocks(vector.getLocation().getBlock());
        if (secondDoor[0] != null && secondDoor[1] != null) {
            // protect the upper block of the second door
            IProtection subProtection = new Protection(new BlockVector(secondDoor[1].getLocation()), protection);
            protection.addSubProtection(subProtection);
            MoneyPitCore.protectionManager.addSubProtection(subProtection);
            MoneyPitCore.databaseManager.createSubProtection(subProtection, true);

            // protect the lower block of the second door
            subProtection = new Protection(new BlockVector(secondDoor[0].getLocation()), protection);
            protection.addSubProtection(subProtection);
            MoneyPitCore.protectionManager.addSubProtection(subProtection);
            MoneyPitCore.databaseManager.createSubProtection(subProtection, true);

            // protect the block below
            subProtection = new Protection(subProtection.getVector().getRelative(0, -1, 0), protection);
            protection.addSubProtection(subProtection);
            MoneyPitCore.protectionManager.addSubProtection(subProtection);
            MoneyPitCore.databaseManager.createSubProtection(subProtection, true);

            // fetch non-solid-blocks
            ArrayList<IProtection> list = PhysicsHelper.protectNonSolidBlocks(protection, subProtection.getVector(), true);
            for (IProtection sub : list) {
                MoneyPitCore.protectionManager.addSubProtection(sub);
            }

            // send info
            PlayerUtils.sendInfo(player, MoneyPitCore.NAME, "Subprotection created.");

        }

        // return true to abort the event
        return new EventResult(false, true, protection);
    }
}
