package de.minestar.moneypit.queues.entity;

import org.bukkit.entity.Player;

import de.minestar.minestarlibrary.utils.PlayerUtils;
import de.minestar.moneypit.MoneyPitCore;
import de.minestar.moneypit.data.protection.EntityProtection;

public class RemoveEntityProtectionQueue implements EntityQueue {

    private final Player player;
    private final EntityProtection protectedEntity;

    public RemoveEntityProtectionQueue(Player player, EntityProtection protectedEntity) {
        this.player = player;
        this.protectedEntity = protectedEntity;
    }

    @Override
    public boolean execute() {
        // try to delete the protection in the database
        // remove from EntityDatabase
        if (MoneyPitCore.databaseManager.deleteEntityProtection(this.protectedEntity)) {
            // remove from EntityProtectionManager
            MoneyPitCore.entityProtectionManager.removeProtection(this.protectedEntity.getUuid().toString());

            // send info
            PlayerUtils.sendSuccess(this.player, MoneyPitCore.NAME, "Protection removed.");
            return true;
        } else {
            PlayerUtils.sendError(player, MoneyPitCore.NAME, "Could not delete protection!");
            PlayerUtils.sendInfo(player, "Please contact an admin.");
            return false;
        }
    }
}
