package de.minestar.moneypit.modules;

import java.util.ArrayList;
import java.util.Collection;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Painting;

import com.bukkit.gemo.patchworking.BlockVector;
import com.bukkit.gemo.patchworking.IProtection;

import de.minestar.moneypit.MoneyPitCore;
import de.minestar.moneypit.data.protection.Protection;
import de.minestar.moneypit.manager.ModuleManager;
import de.minestar.moneypit.utils.HangingHelper;
import de.minestar.moneypit.utils.PhysicsHelper;

public class Module_Painting extends Module {

    private final Material TYPE = Material.PAINTING;
    private final String NAME = TYPE.name();

    public Module_Painting(YamlConfiguration ymlFile) {
        this.writeDefaultConfig(NAME, ymlFile);
        this.setAutolock(false);
    }

    public Module_Painting(ModuleManager moduleManager, YamlConfiguration ymlFile) {
        super();
        this.init(moduleManager, ymlFile, TYPE, NAME);
        this.setAutolock(false);
    }

    @Override
    public boolean addProtection(IProtection protection, byte subData, boolean saveToDatabase) {
        // get the anchor
        BlockVector vector = protection.getVector();
        Collection<Painting> paintings = vector.getLocation().getWorld().getEntitiesByClass(Painting.class);
        Painting painting = null;

        for (Painting paint : paintings) {
            BlockVector otherVector = new BlockVector(paint.getLocation());
            if (vector.equals(otherVector)) {
                painting = paint;
                break;
            }
        }
        if (painting == null) {
            return false;
        }

        // get the anchors
        ArrayList<BlockVector> anchors = HangingHelper.getSubProtectedPaintingBlocks(protection.getVector(), painting);

        // protect the block below
        for (BlockVector anchor : anchors) {
            IProtection subProtection = new Protection(anchor, protection);
            protection.addSubProtection(subProtection);
            MoneyPitCore.databaseManager.createSubProtection(subProtection, saveToDatabase);

            // fetch non-solid-blocks
            PhysicsHelper.protectNonSolidBlocks(protection, anchor, saveToDatabase);
        }

        // register the protection
        return getProtectionManager().addProtection(protection);
    }
}
