package de.minestar.moneypit.listener;

import java.util.Random;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import com.bukkit.gemo.utils.UtilPermissions;

import de.minestar.minestarlibrary.utils.PlayerUtils;
import de.minestar.moneypit.Core;
import de.minestar.moneypit.data.BlockVector;
import de.minestar.moneypit.data.PlayerState;
import de.minestar.moneypit.data.protection.Protection;
import de.minestar.moneypit.data.protection.ProtectionInfo;
import de.minestar.moneypit.data.protection.ProtectionType;
import de.minestar.moneypit.data.subprotection.SubProtection;
import de.minestar.moneypit.data.subprotection.SubProtectionHolder;
import de.minestar.moneypit.manager.ModuleManager;
import de.minestar.moneypit.manager.PlayerManager;
import de.minestar.moneypit.manager.ProtectionManager;
import de.minestar.moneypit.manager.QueueManager;
import de.minestar.moneypit.modules.Module;
import de.minestar.moneypit.queues.AddQueue;

public class ActionListener implements Listener {

    private ModuleManager moduleManager;
    private PlayerManager playerManager;
    private ProtectionManager protectionManager;
    private QueueManager queueManager;

    private BlockVector vector;
    private ProtectionInfo protectionInfo;

    private Block[] redstoneCheckBlocks = new Block[6];

    public ActionListener() {
        this.moduleManager = Core.moduleManager;
        this.playerManager = Core.playerManager;
        this.protectionManager = Core.protectionManager;
        this.queueManager = Core.queueManager;
        this.vector = new BlockVector("", 0, 0, 0);
        this.protectionInfo = new ProtectionInfo();
    }

    private void refreshRedstoneCheckBlocks(Block block) {
        redstoneCheckBlocks[0] = block.getRelative(BlockFace.UP);
        redstoneCheckBlocks[1] = redstoneCheckBlocks[0].getRelative(BlockFace.UP);
        redstoneCheckBlocks[2] = block.getRelative(BlockFace.NORTH);
        redstoneCheckBlocks[3] = block.getRelative(BlockFace.WEST);
        redstoneCheckBlocks[4] = block.getRelative(BlockFace.EAST);
        redstoneCheckBlocks[5] = block.getRelative(BlockFace.SOUTH);
    }

    @EventHandler
    public void onBlockRedstoneChange(BlockRedstoneEvent event) {
        // event is already cancelled => return
        if (event.getNewCurrent() == event.getOldCurrent()) {
            return;
        }

        this.refreshRedstoneCheckBlocks(event.getBlock());
        Module module;
        for (Block block : this.redstoneCheckBlocks) {
            // update the BlockVector & the ProtectionInfo
            this.vector.update(block.getLocation());
            this.protectionInfo.update(this.vector);
            if (this.protectionInfo.hasAnyProtection()) {
                Protection protection = this.protectionInfo.getProtection();
                if (protection != null) {
                    // normal protection

                    // only private protections are blocked
                    if (protection.isPublic()) {
                        continue;
                    }

                    // get the module
                    module = this.moduleManager.getRegisteredModule(block.getTypeId());
                    if (module == null) {
                        continue;
                    }

                    // check for redstone only, if the module wants it
                    if (!module.blockRedstone()) {
                        continue;
                    }
                    event.setNewCurrent(event.getOldCurrent());
                    return;
                } else {
                    // SubProtection here
                    // check all subprotections at this place and see if we
                    // handle the redstone-event
                    int moduleID = 0;
                    SubProtectionHolder holder = this.protectionInfo.getSubProtections();
                    for (SubProtection subProtection : holder.getProtections()) {
                        // only private protections are blocked
                        if (subProtection.getParent().isPublic()) {
                            continue;
                        }

                        // get the module
                        moduleID = subProtection.getModuleID();
                        module = this.moduleManager.getRegisteredModule(moduleID);
                        if (module == null) {
                            continue;
                        }

                        // check for redstone only, if the module wants it
                        if (!module.blockRedstone()) {
                            continue;
                        }
                        event.setNewCurrent(event.getOldCurrent());
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // event is already cancelled => return
        if (event.isCancelled()) {
            return;
        }

        // get the module
        Module module = this.moduleManager.getRegisteredModule(event.getBlockPlaced().getTypeId());
        if (module == null) {
            return;
        }

        // check for neighbours, if the module wants it
        if (module.doNeighbourCheck()) {
            if (module.onPlace(event, new BlockVector(event.getBlockPlaced().getLocation()))) {
                return;
            }
        }

        // only act, if the module is in autolockmode
        if (!module.isAutoLock()) {
            return;
        }

        // update the BlockVector & the ProtectionInfo
        this.vector.update(event.getBlock().getLocation());
        this.protectionInfo.update(this.vector);

        // add protection, if it isn't protected yet
        if (!this.protectionInfo.hasAnyProtection()) {
            // check the permission
            boolean canProtect = UtilPermissions.playerCanUseCommand(event.getPlayer(), "moneypit.protect." + module.getModuleName()) || UtilPermissions.playerCanUseCommand(event.getPlayer(), "moneypit.admin");
            if (canProtect) {
                // create the vector
                BlockVector tempVector = new BlockVector(event.getBlockPlaced().getLocation());

                // queue the event for later use in MonitorListener
                AddQueue addQueue = new AddQueue(event.getPlayer(), module, tempVector, event.getBlockPlaced().getTypeId(), this.protectionInfo.clone());
                this.queueManager.addBlockPlace(addQueue);
            } else {
                PlayerUtils.sendError(event.getPlayer(), Core.NAME, "You don't have permissions to protect this block.");
                return;
            }
        } else {
            if (this.protectionInfo.hasSubProtection()) {
                if (!this.protectionInfo.getSubProtections().canEditAll(event.getPlayer())) {
                    PlayerUtils.sendError(event.getPlayer(), Core.NAME, "You are not allowed to edit this protected block!");
                    event.setCancelled(true);
                    return;
                }
            }
            PlayerUtils.sendError(event.getPlayer(), Core.NAME, "Cannot create protection!");
            PlayerUtils.sendInfo(event.getPlayer(), "This block is already protected.");
        }
    }
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // event is already cancelled => return
        if (event.isCancelled()) {
            return;
        }

        // update the BlockVector & the ProtectionInfo
        this.vector.update(event.getBlock().getLocation());
        this.protectionInfo.update(this.vector);

        // Block is not protected => return
        if (!this.protectionInfo.hasAnyProtection()) {
            return;
        }

        // Block is protected => check: Protection OR SubProtection
        if (this.protectionInfo.hasProtection()) {
            // we have a regular protection => get the module (must be
            // registered)
            Module module = this.moduleManager.getRegisteredModule(event.getBlock().getTypeId());
            if (module == null) {
                PlayerUtils.sendError(event.getPlayer(), Core.NAME, "Module for block '" + event.getBlock().getType().name() + "' is not registered!");
                return;
            }

            // get the protection
            Protection protection = this.protectionInfo.getProtection();

            // check permission
            boolean isOwner = protection.isOwner(event.getPlayer().getName());
            boolean isAdmin = UtilPermissions.playerCanUseCommand(event.getPlayer(), "moneypit.admin");
            if (!isOwner && !isAdmin) {
                PlayerUtils.sendError(event.getPlayer(), Core.NAME, "You are not allowed to break this protected block.");
                event.setCancelled(true);
                return;
            }

            // remove protection
            this.protectionManager.removeProtection(this.vector);

            // send info
            PlayerUtils.sendSuccess(event.getPlayer(), Core.NAME, "Protection removed.");
        } else {
            // we have a SubProtection => check permissions and handle it
            if (!this.protectionInfo.getSubProtections().canEditAll(event.getPlayer())) {
                PlayerUtils.sendError(event.getPlayer(), Core.NAME, "You are not allowed to remove this subprotection!");
                event.setCancelled(true);
                return;
            }

            // Remove all SubProtections
            SubProtection protection;
            for (int i = 0; i < this.protectionInfo.getSubProtections().getSize(); i++) {
                protection = this.protectionInfo.getSubProtections().getProtection(i);
                if (protection != null) {
                    this.protectionManager.removeProtection(protection.getParent().getVector());
                }
            }

            // Send info
            PlayerUtils.sendSuccess(event.getPlayer(), Core.NAME, "Protection removed!");
            return;
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // event is already cancelled => return
        if (event.isCancelled()) {
            return;
        }

        // Only handle Left- & Right-Click on a block
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // update the BlockVector & the ProtectionInfo
        this.vector.update(event.getClickedBlock().getLocation());
        this.protectionInfo.update(this.vector);

        // get PlayerState
        final PlayerState state = this.playerManager.getState(event.getPlayer().getName());

        // decide what to do
        switch (state) {
            case PROTECTION_INFO : {
                // handle info
                this.handleInfoInteract(event);
                break;
            }
            case PROTECTION_REMOVE : {
                // handle remove
                this.handleRemoveInteract(event);
                break;
            }
            case PROTECTION_ADD_PRIVATE : {
                // the module must be registered
                Module module = this.moduleManager.getRegisteredModule(event.getClickedBlock().getTypeId());
                if (module == null) {
                    PlayerUtils.sendError(event.getPlayer(), Core.NAME, "Module for block '" + event.getClickedBlock().getType().name() + "' is not registered!");
                    return;
                }

                // handle add
                this.handleAddInteract(event, module, state);
                break;
            }
            case PROTECTION_ADD_PUBLIC : {
                // the module must be registered
                Module module = this.moduleManager.getRegisteredModule(event.getClickedBlock().getTypeId());
                if (module == null) {
                    PlayerUtils.sendError(event.getPlayer(), Core.NAME, "Module for block '" + event.getClickedBlock().getType().name() + "' is not registered!");
                    return;
                }

                // handle add
                this.handleAddInteract(event, module, state);
                break;
            }
            default : {
                // handle normal interact
                this.handleNormalInteract(event);
                break;
            }
        }
    }

    // //////////////////////////////////////////////////////////////////////
    //
    // FROM HERE ON: METHODS TO HANDLE THE PLAYERINTERACT
    //
    // //////////////////////////////////////////////////////////////////////

    private void showInformation(Player player) {
        // we need a protection to show some information about it
        if (!this.protectionInfo.hasAnyProtection()) {
            return;
        }

        if (this.protectionInfo.hasProtection()) {
            // handle mainprotections
            String pType = " PRIVATE ";
            if (this.protectionInfo.getProtection().isPublic()) {
                pType = " PUBLIC ";
            }

            int moduleID = this.protectionInfo.getProtection().getModuleID();
            String message = "This" + ChatColor.RED + pType + Material.getMaterial(moduleID) + ChatColor.GRAY + " is protected by " + ChatColor.YELLOW + player.getName() + ".";
            PlayerUtils.sendInfo(player, message);
            return;
        } else {
            // handle subprotections
            if (this.protectionInfo.getSubProtections().getSize() == 1) {
                String pType = " PRIVATE ";
                if (this.protectionInfo.getSubProtections().getProtection(0).getParent().isPublic()) {
                    pType = " PUBLIC ";
                }

                int moduleID = this.protectionInfo.getSubProtections().getProtection(0).getVector().getLocation().getBlock().getTypeId();
                String message = "This" + ChatColor.RED + pType + Material.getMaterial(moduleID) + ChatColor.GRAY + " is protected by " + ChatColor.YELLOW + player.getName() + ".";
                PlayerUtils.sendInfo(player, message);
                return;
            } else if (this.protectionInfo.getSubProtections().getSize() > 1) {
                int moduleID = this.protectionInfo.getSubProtections().getProtection(0).getVector().getLocation().getBlock().getTypeId();
                String message = "This" + ChatColor.RED + Material.getMaterial(moduleID) + ChatColor.GRAY + " is protected by " + ChatColor.YELLOW + "multiple protections" + ".";
                PlayerUtils.sendInfo(player, message);
                return;
            }
        }
    }

    private void handleInfoInteract(PlayerInteractEvent event) {
        // cancel the event
        event.setCancelled(true);

        // return to normalmode
        this.playerManager.setState(event.getPlayer().getName(), PlayerState.NORMAL);

        // show information
        this.showInformation(event.getPlayer());
    }

    private void handleRemoveInteract(PlayerInteractEvent event) {
        // cancel the event
        event.setCancelled(true);

        // return to normalmode
        this.playerManager.setState(event.getPlayer().getName(), PlayerState.NORMAL);

        // try to remove the protection
        if (!this.protectionInfo.hasAnyProtection()) {
            PlayerUtils.sendError(event.getPlayer(), Core.NAME, "This block is not protected!");
            return;
        } else if (this.protectionInfo.hasProtection()) {
            // get protection
            Protection protection = this.protectionInfo.getProtection();

            // check permission
            boolean isOwner = protection.isOwner(event.getPlayer().getName());
            boolean isAdmin = UtilPermissions.playerCanUseCommand(event.getPlayer(), "moneypit.admin");
            if (!isOwner && !isAdmin) {
                PlayerUtils.sendError(event.getPlayer(), Core.NAME, "You are not allowed to remove this protection!");
                return;
            }

            // print info
            this.protectionManager.removeProtection(this.vector);
            PlayerUtils.sendSuccess(event.getPlayer(), Core.NAME, "Protection removed.");
        } else {
            // we have a SubProtection => check permissions and handle it
            if (!this.protectionInfo.getSubProtections().canEditAll(event.getPlayer())) {
                PlayerUtils.sendError(event.getPlayer(), Core.NAME, "You are not allowed to remove this subprotection!");
                return;
            }

            // Remove all SubProtections
            SubProtection protection;
            for (int i = 0; i < this.protectionInfo.getSubProtections().getSize(); i++) {
                protection = this.protectionInfo.getSubProtections().getProtection(i);
                if (protection != null) {
                    this.protectionManager.removeProtection(protection.getParent().getVector());
                }
            }

            // Send info
            PlayerUtils.sendSuccess(event.getPlayer(), Core.NAME, "Protection removed!");
            return;
        }
    }

    private void handleAddInteract(PlayerInteractEvent event, Module module, PlayerState state) {
        // cancel event
        event.setCancelled(true);

        // return to normalmode
        this.playerManager.setState(event.getPlayer().getName(), PlayerState.NORMAL);

        // check permissions
        if (!UtilPermissions.playerCanUseCommand(event.getPlayer(), "moneypit.protect." + module.getModuleName()) && !UtilPermissions.playerCanUseCommand(event.getPlayer(), "moneypit.admin")) {
            PlayerUtils.sendError(event.getPlayer(), Core.NAME, "You are not allowed to protect this block!");
            return;
        }

        // add protection, if it isn't protected yet
        if (!this.protectionInfo.hasAnyProtection()) {
            // create the vector
            BlockVector tempVector = new BlockVector(event.getClickedBlock().getLocation());

            if (state == PlayerState.PROTECTION_ADD_PRIVATE) {
                // protect private
                Random random = new Random();
                module.addProtection(random.nextInt(1000000), tempVector, event.getPlayer().getName(), ProtectionType.PRIVATE, event.getClickedBlock().getData());
                PlayerUtils.sendSuccess(event.getPlayer(), Core.NAME, "Private protection created.");
            } else {
                // protect public
                Random random = new Random();
                module.addProtection(random.nextInt(1000000), tempVector, event.getPlayer().getName(), ProtectionType.PUBLIC, event.getClickedBlock().getData());
                PlayerUtils.sendSuccess(event.getPlayer(), Core.NAME, "Public protection created.");
            }
        } else {
            PlayerUtils.sendError(event.getPlayer(), Core.NAME, "Cannot create protection!");

            // show information about the protection
            this.showInformation(event.getPlayer());
        }
    }

    private void handleNormalInteract(PlayerInteractEvent event) {
        // CHECK: Protection?
        if (this.protectionInfo.hasProtection()) {
            // is this protection private?
            if (!this.protectionInfo.getProtection().canAccess(event.getPlayer())) {
                // show information about the protection
                this.showInformation(event.getPlayer());
                // cancel the event
                event.setCancelled(true);
                return;
            }
            // show information about the protection
            this.showInformation(event.getPlayer());
            return;
        }

        // CHECK: SubProtection?
        if (this.protectionInfo.hasSubProtection()) {
            SubProtectionHolder holder = this.protectionManager.getSubProtectionHolder(vector);
            for (SubProtection subProtection : holder.getProtections()) {
                // is this protection private?
                if (!subProtection.getParent().isPrivate()) {
                    continue;
                }

                // check the access
                if (!subProtection.canAccess(event.getPlayer())) {
                    // cancel event
                    event.setCancelled(true);
                    // show information about the protection
                    this.showInformation(event.getPlayer());
                    return;
                }
            }
            // show information about the protection
            this.showInformation(event.getPlayer());
            return;
        }
    }

    // //////////////////////////////////////////////////////////////////////
    //
    // FROM HERE ON: EVENTS THAT ARE NOT DIRECTLY TRIGGERED BY A PLAYER
    //
    // //////////////////////////////////////////////////////////////////////

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        // /////////////////////////////////
        // event cancelled => return
        // /////////////////////////////////
        if (event.isCancelled())
            return;

        for (Block block : event.getBlocks()) {
            // update the BlockVector & the ProtectionInfo
            this.vector.update(block.getLocation());
            this.protectionInfo.update(this.vector);

            // cancel the event, if the block is protected
            if (this.protectionInfo.hasAnyProtection()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        // /////////////////////////////////
        // event cancelled or normal piston => return
        // /////////////////////////////////
        if (event.isCancelled() || !event.isSticky())
            return;

        // update the BlockVector & the ProtectionInfo
        this.vector.update(event.getRetractLocation());
        this.protectionInfo.update(this.vector);

        // cancel the event, if the block is protected
        if (this.protectionInfo.hasAnyProtection()) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockFromTo(BlockFromToEvent event) {
        // /////////////////////////////////
        // event cancelled => return
        // /////////////////////////////////
        if (event.isCancelled())
            return;

        final Block toBlock = event.getToBlock();
        // update the BlockVector & the ProtectionInfo
        this.vector.update(toBlock.getLocation());
        this.protectionInfo.update(this.vector);

        // cancel the event, if the block is protected
        if (this.protectionInfo.hasAnyProtection()) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockExplode(EntityExplodeEvent event) {
        // /////////////////////////////////
        // event cancelled => return
        // /////////////////////////////////
        if (event.isCancelled())
            return;

        for (Block block : event.blockList()) {
            // update the BlockVector & the ProtectionInfo
            this.vector.update(block.getLocation());
            this.protectionInfo.update(this.vector);

            // cancel the event, if the block is protected
            if (this.protectionInfo.hasAnyProtection()) {
                event.setCancelled(true);
                event.setYield(0f);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        // /////////////////////////////////
        // event cancelled => return
        // /////////////////////////////////
        if (event.isCancelled())
            return;

        // update the BlockVector & the ProtectionInfo
        this.vector.update(event.getBlock().getLocation());
        this.protectionInfo.update(this.vector);

        // cancel the event, if the block is protected
        if (this.protectionInfo.hasAnyProtection()) {
            event.setCancelled(true);
            return;
        }
    }
}
