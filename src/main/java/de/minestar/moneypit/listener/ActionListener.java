package de.minestar.moneypit.listener;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.DoubleChest;
import org.bukkit.craftbukkit.v1_10_R1.block.CraftBlockState;
import org.bukkit.craftbukkit.v1_10_R1.block.CraftHopper;
import org.bukkit.craftbukkit.v1_10_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import com.bukkit.gemo.patchworking.BlockVector;
import com.bukkit.gemo.patchworking.Guest;
import com.bukkit.gemo.patchworking.IProtection;
import com.bukkit.gemo.patchworking.IProtectionInfo;
import com.bukkit.gemo.patchworking.ISubProtectionHolder;
import com.bukkit.gemo.patchworking.ProtectionType;
import com.bukkit.gemo.utils.UtilPermissions;

import de.minestar.minestarlibrary.events.PlayerChangedNameEvent;
import de.minestar.minestarlibrary.utils.PlayerUtils;
import de.minestar.moneypit.MoneyPitCore;
import de.minestar.moneypit.data.EventResult;
import de.minestar.moneypit.data.PlayerState;
import de.minestar.moneypit.data.guests.GuestHelper;
import de.minestar.moneypit.data.protection.ProtectionInfo;
import de.minestar.moneypit.manager.ModuleManager;
import de.minestar.moneypit.manager.PlayerManager;
import de.minestar.moneypit.manager.ProtectionManager;
import de.minestar.moneypit.manager.QueueManager;
import de.minestar.moneypit.modules.Module;
import de.minestar.moneypit.queues.AddProtectionQueue;
import de.minestar.moneypit.queues.RemoveProtectionQueue;
import de.minestar.moneypit.queues.RemoveSubProtectionQueue;
import de.minestar.moneypit.utils.DoorHelper;

public class ActionListener implements Listener {

    private ModuleManager moduleManager;
    private PlayerManager playerManager;
    private ProtectionManager protectionManager;
    private QueueManager queueManager;

    private BlockVector vector;
    private ProtectionInfo protectionInfo;

    private HashSet<BlockVector> redstoneQueuedDoors = new HashSet<BlockVector>();

    private Block[] redstoneCheckBlocks = new Block[6];

    private HashSet<String> openedGiftChests;
    private HashMap<String, Boolean> wasPlayerInv;

    public ActionListener() {
        this.moduleManager = MoneyPitCore.moduleManager;
        this.playerManager = MoneyPitCore.playerManager;
        this.protectionManager = MoneyPitCore.protectionManager;
        this.queueManager = MoneyPitCore.queueManager;
        this.vector = new BlockVector("", 0, 0, 0);
        this.protectionInfo = new ProtectionInfo();
        this.openedGiftChests = new HashSet<String>();
        this.wasPlayerInv = new HashMap<String, Boolean>();
    }

    @EventHandler
    public void onPlayerChangeNick(PlayerChangedNameEvent event) {
        MoneyPitCore.databaseManager.updateOwner(event.getOldName(), event.getNewName());
        MoneyPitCore.protectionManager.transferProtections(event.getOldName(), event.getNewName());
        Player player = PlayerUtils.getOnlinePlayer(event.getCommandSender());
        if (player != null) {
            PlayerUtils.sendInfo(player, MoneyPitCore.NAME, "Transfer complete.");
        }
    }

    public void closeInventories() {
        Player player = null;
        for (String playerName : this.openedGiftChests) {
            player = Bukkit.getPlayer(playerName);
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
    }

    private void refreshRedstoneCheckBlocks(Block block) {
        redstoneCheckBlocks[0] = block.getRelative(BlockFace.UP);
        redstoneCheckBlocks[1] = redstoneCheckBlocks[0].getRelative(BlockFace.UP);
        redstoneCheckBlocks[2] = block.getRelative(BlockFace.NORTH);
        redstoneCheckBlocks[3] = block.getRelative(BlockFace.WEST);
        redstoneCheckBlocks[4] = block.getRelative(BlockFace.EAST);
        redstoneCheckBlocks[5] = block.getRelative(BlockFace.SOUTH);
    }

    // //////////////////////////////////////////////////////////////////////
    //
    // INVENTORYCHANGES
    //
    // //////////////////////////////////////////////////////////////////////

    @EventHandler(ignoreCancelled = true)
    public void onInventoryItemPickup(InventoryPickupItemEvent event) {
        if (event.getInventory() != null) {
            InventoryHolder holder = event.getInventory().getHolder();
            if (holder != null && holder instanceof CraftHopper) {
                // get the hopper
                CraftHopper hopper = (CraftHopper) holder;

                // update the BlockVector & the ProtectionInfo
                this.vector.update(hopper.getLocation());
                this.protectionInfo.update(this.vector);

                // Block is not protected => return
                if (!this.protectionInfo.hasAnyProtection()) {
                    return;
                }

                if (this.protectionInfo.hasProtection()) {
                    // so we have a main-protection
                    IProtection protection = this.protectionInfo.getProtection();
                    if (protection.isPrivate()) {
                        event.setCancelled(true);
                        return;
                    }
                } else {
                    // so we have a sub-protection
                    IProtection protection = this.protectionInfo.getFirstProtection();
                    if (protection.isPrivate()) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryItemMove(InventoryMoveItemEvent event) {
        IProtectionInfo infoSource = new ProtectionInfo();
        IProtectionInfo infoDestination = new ProtectionInfo();

        // update source
        if (event.getSource() != null) {
            InventoryHolder holder = event.getSource().getHolder();
            if (holder != null) {
                if (holder instanceof CraftBlockState) {
                    CraftBlockState blockState = (CraftBlockState) holder;
                    // update the BlockVector & the ProtectionInfo
                    this.vector.update(blockState.getLocation());
                    infoSource.update(this.vector);
                } else if (holder instanceof DoubleChest) {
                    DoubleChest chest = (DoubleChest) holder;
                    this.vector.update(chest.getLocation());
                    infoSource.update(this.vector);
                }
            }
        }

        // update destination
        if (event.getDestination() != null) {
            InventoryHolder destination = event.getDestination().getHolder();
            if (destination != null) {
                if (destination instanceof CraftBlockState) {
                    CraftBlockState blockState = (CraftBlockState) destination;
                    // update the BlockVector & the ProtectionInfo
                    this.vector.update(blockState.getLocation());
                    infoDestination.update(this.vector);
                } else if (destination instanceof DoubleChest) {
                    DoubleChest chest = (DoubleChest) destination;
                    this.vector.update(chest.getLocation());
                    infoDestination.update(this.vector);
                }
            }
        }

        // obviously, we have two protections..
        // ... so we need to check if the owners of both protections are equal
        if (!MoneyPitCore.protectionsAreEqual(infoSource, infoDestination)) {
            event.setCancelled(true);
            return;
        }
    }

    // //////////////////////////////////////////////////////////////////////
    //
    // BLOCKCHANGES
    //
    // //////////////////////////////////////////////////////////////////////

    @EventHandler(ignoreCancelled = true)
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
                IProtection protection = this.protectionInfo.getProtection();
                if (protection != null) {
                    // normal protection

                    // only private protections are blocked
                    if (protection.isPublic()) {
                        if (block.getType().equals(Material.IRON_DOOR_BLOCK)) {
                            this.redstoneQueuedDoors.add(new BlockVector(DoorHelper.getLowerDoorPart(block).getLocation()));
                            Block bl = DoorHelper.getOppositeLowerDoorPart(block);
                            if (bl != null && bl.getType().equals(Material.IRON_DOOR_BLOCK)) {
                                this.redstoneQueuedDoors.add(new BlockVector(bl.getLocation()));
                            }
                        } else if (DoorHelper.isDoor(block.getType())) {
                            this.redstoneQueuedDoors.add(new BlockVector(DoorHelper.getLowerDoorPart(block).getLocation()));
                            Block bl = DoorHelper.getOppositeLowerDoorPart(block);
                            if (bl != null && DoorHelper.isDoor(bl.getType()) && !bl.getType().equals(Material.IRON_DOOR_BLOCK)) {
                                this.redstoneQueuedDoors.add(new BlockVector(bl.getLocation()));
                            }
                        }
                        continue;
                    }

                    // get the module
                    module = this.moduleManager.getRegisteredModule(block.getType());
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
                    Material moduleType = Material.AIR;
                    ISubProtectionHolder holder = this.protectionInfo.getSubProtections();
                    for (IProtection subProtection : holder.getProtections()) {
                        // only private protections are blocked
                        if (subProtection.isPublic()) {
                            if (block.getType().equals(Material.IRON_DOOR_BLOCK)) {
                                this.redstoneQueuedDoors.add(new BlockVector(DoorHelper.getLowerDoorPart(block).getLocation()));
                                Block bl = DoorHelper.getOppositeLowerDoorPart(block);
                                if (bl != null && bl.getType().equals(Material.IRON_DOOR_BLOCK)) {
                                    this.redstoneQueuedDoors.add(new BlockVector(bl.getLocation()));
                                }
                            } else if (DoorHelper.isDoor(block.getType())) {
                                this.redstoneQueuedDoors.add(new BlockVector(DoorHelper.getLowerDoorPart(block).getLocation()));
                                Block bl = DoorHelper.getOppositeLowerDoorPart(block);
                                if (bl != null && DoorHelper.isDoor(bl.getType()) && !bl.getType().equals(Material.IRON_DOOR_BLOCK)) {
                                    this.redstoneQueuedDoors.add(new BlockVector(bl.getLocation()));
                                }
                            }
                            continue;
                        }

                        // get the module
                        moduleType = subProtection.getBlockType();
                        module = this.moduleManager.getRegisteredModule(moduleType);
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
            } else {
                if (DoorHelper.isDoor(block.getType()) && !block.getType().equals(Material.IRON_DOOR_BLOCK)) {
                    this.redstoneQueuedDoors.add(new BlockVector(DoorHelper.getLowerDoorPart(block).getLocation()));
                    Block bl = DoorHelper.getOppositeLowerDoorPart(block);
                    if (bl != null && DoorHelper.isDoor(bl.getType()) && !bl.getType().equals(Material.IRON_DOOR_BLOCK)) {
                        this.redstoneQueuedDoors.add(new BlockVector(bl.getLocation()));
                    }
                }
            }
        }

        if (!this.redstoneQueuedDoors.isEmpty()) {
            boolean openDoor = event.getNewCurrent() > 0;
            for (BlockVector vector : this.redstoneQueuedDoors) {
                if (openDoor) {
                    DoorHelper.openDoor(vector.getLocation().getBlock(), true);
                } else {
                    DoorHelper.closeDoor(vector.getLocation().getBlock());
                }
            }
            this.redstoneQueuedDoors.clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // event is already cancelled => return
        if (event.isCancelled() || !event.canBuild()) {
            return;
        }

        // get the module
        Module module = this.moduleManager.getRegisteredModule(event.getBlockPlaced().getType());
        if (module == null) {
            return;
        }

        // check for neighbours, if the module wants it
        if (module.doNeighbourCheck()) {
            EventResult result = module.onPlace(event.getPlayer(), new BlockVector(event.getBlockPlaced().getLocation()));
            if (result.isCancelEvent()) {
                event.setBuild(false);
                event.setCancelled(true);

                BlockVector vector = result.getProtection().getVector();
                CraftPlayer cPlayer = (CraftPlayer) event.getPlayer();
                cPlayer.sendBlockChange(vector.getLocation(), vector.getLocation().getBlock().getType(), vector.getLocation().getBlock().getData());
            }
            if (result.isAbort()) {
                return;
            }
        }

        // only act, if the module is in autolockmode
        if (!module.isAutoLock() || this.playerManager.noAutoLock(event.getPlayer().getName())) {
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
                AddProtectionQueue queue = new AddProtectionQueue(event.getPlayer(), module, tempVector, ProtectionType.PRIVATE);
                this.queueManager.addQueue(queue);
            } else {
                PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "You don't have permissions to protect this block.");
                return;
            }
        } else {
            if (this.protectionInfo.hasSubProtection()) {
                if (!this.protectionInfo.getSubProtections().canEditAll(event.getPlayer())) {
                    PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "You are not allowed to edit this protected block!");
                    event.setCancelled(true);
                    return;
                }
            }
            PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "Cannot create protection!");
            PlayerUtils.sendInfo(event.getPlayer(), "This block is already protected.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
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
            Module module = this.moduleManager.getRegisteredModule(event.getBlock().getType());
            if (module == null) {
                PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "Module for block '" + event.getBlock().getType().name() + "' is not registered!");
                return;
            }

            // get the protection
            IProtection protection = this.protectionInfo.getProtection();

            // check permission
            boolean isOwner = protection.isOwner(event.getPlayer().getName());
            boolean isAdmin = UtilPermissions.playerCanUseCommand(event.getPlayer(), "moneypit.admin");
            if (!isOwner && !isAdmin) {
                PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "You are not allowed to break this protected block.");
                event.setCancelled(true);
                return;
            }

            // create the vector
            BlockVector tempVector = new BlockVector(event.getBlock().getLocation());

            // queue the event for later use in MonitorListener
            RemoveProtectionQueue queue = new RemoveProtectionQueue(event.getPlayer(), tempVector);
            this.queueManager.addQueue(queue);
        } else {
            // we have a SubProtection => check permissions and handle it
            if (!this.protectionInfo.getSubProtections().canEditAll(event.getPlayer())) {
                PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "You are not allowed to remove this subprotection!");
                event.setCancelled(true);
                return;
            }

            // create the vector
            BlockVector tempVector = new BlockVector(event.getBlock().getLocation());

            // queue the event for later use in MonitorListener
            RemoveSubProtectionQueue queue = new RemoveSubProtectionQueue(event.getPlayer(), tempVector, this.protectionInfo.clone());
            this.queueManager.addQueue(queue);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (event.getChangedType() != null) {
            if (event.getChangedType().equals(Material.TNT)) {
                // update the BlockVector & the ProtectionInfo
                this.vector.update(event.getBlock().getLocation());
                this.protectionInfo.update(this.vector);

                if (this.protectionInfo.hasAnyProtection()) {
                    event.setCancelled(true);
                }
            }
        }
    }

    // //////////////////////////////////////////////////////////////////////
    //
    // HANGING ENTITIES
    //
    // //////////////////////////////////////////////////////////////////////

    public void handleHangingBreakByEntity(HangingBreakByEntityEvent event, Module module, Player player) {
        // Block is protected => check: Protection OR SubProtection
        if (this.protectionInfo.hasProtection()) {
            // get the protection
            IProtection protection = this.protectionInfo.getProtection();

            // check permission
            boolean isOwner = protection.isOwner(player.getName());
            boolean isAdmin = UtilPermissions.playerCanUseCommand(player, "moneypit.admin");
            if (!isOwner && !isAdmin) {
                PlayerUtils.sendError(player, MoneyPitCore.NAME, "You are not allowed to break this protected block.");
                event.setCancelled(true);
                return;
            }

            // create the vector
            BlockVector tempVector = new BlockVector(event.getEntity().getLocation());

            // queue the event for later use in MonitorListener
            RemoveProtectionQueue queue = new RemoveProtectionQueue(player, tempVector);
            this.queueManager.addQueue(queue);
        } else {
            // we have a SubProtection => check permissions and handle it
            if (!this.protectionInfo.getSubProtections().canEditAll(player)) {
                PlayerUtils.sendError(player, MoneyPitCore.NAME, "You are not allowed to remove this subprotection!");
                event.setCancelled(true);
                return;
            }

            // create the vector
            BlockVector tempVector = new BlockVector(event.getEntity().getLocation());

            // queue the event for later use in MonitorListener
            RemoveSubProtectionQueue queue = new RemoveSubProtectionQueue(player, tempVector, this.protectionInfo.clone());
            this.queueManager.addQueue(queue);
        }
    }

    public void handleHangingInteract(PlayerInteractEntityEvent event, Module module) {
        // get PlayerState
        final PlayerState state = this.playerManager.getState(event.getPlayer().getName());

        Hanging entity = (Hanging) event.getRightClicked();

        // decide what to do
        switch (state) {
            case PROTECTION_INFO : {
                // handle info
                this.handleHangingInfoInteract(event);
                break;
            }
            case PROTECTION_REMOVE : {
                // handle remove
                this.handleHangingRemoveInteract(event);
                break;
            }
            case PROTECTION_ADD_PRIVATE : {
                // handle add
                this.handleHangingAddInteract(event, module, state, entity);
                break;
            }
            case PROTECTION_INVITE :
            case PROTECTION_UNINVITE :
            case PROTECTION_UNINVITEALL :
            case PROTECTION_ADD_GIFT :
            case PROTECTION_ADD_PUBLIC : {
                // return to normalmode
                if (!this.playerManager.keepsMode(event.getPlayer().getName())) {
                    this.playerManager.setState(event.getPlayer().getName(), PlayerState.NORMAL);
                }

                // cancel event and send error
                event.setCancelled(true);
                PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "Action is not allowed for '" + entity.getType().name() + "'!");
                return;
            }
            default : {
                // handle normal interact
                this.handleHangingNormalInteract(event);
                break;
            }
        }
    }

    public void onHangingBreak(HangingBreakEvent event, Module module) {
        // update the BlockVector & the ProtectionInfo
        this.vector.update(event.getEntity().getLocation());
        this.protectionInfo.update(this.vector);

        // Block is not protected => return
        if (!this.protectionInfo.hasAnyProtection()) {
            return;
        }

        if (event.getCause().equals(RemoveCause.PHYSICS) || event.getCause().equals(RemoveCause.OBSTRUCTION)) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        // Only handle ItemFrames & Paintings
        if (!event.getEntity().getType().equals(EntityType.ITEM_FRAME) && !event.getEntity().getType().equals(EntityType.PAINTING)) {
            return;
        }

        // get the module
        Module module = this.moduleManager.getRegisteredModule(Material.ITEM_FRAME);
        if (event.getEntity().getType().equals(EntityType.PAINTING)) {
            module = this.moduleManager.getRegisteredModule(Material.PAINTING);
        }

        // update the BlockVector & the ProtectionInfo
        this.vector.update(event.getEntity().getLocation());
        this.protectionInfo.update(this.vector);

        // Block is not protected => return
        if (!this.protectionInfo.hasAnyProtection()) {
            return;
        }

        // Removed by a player?
        if (event.getRemover().getType().equals(EntityType.PLAYER)) {
            // get the player
            Player player = (Player) event.getRemover();

            // is the module registered?
            if (module == null) {
                PlayerUtils.sendError(player, MoneyPitCore.NAME, "Module for '" + event.getEntity().getType().name() + "' is not registered!");
                return;
            }

            // handle
            this.handleHangingBreakByEntity(event, module, player);
        } else {
            // block removal by other entities
            if (this.cancelBlockEvent(event.getEntity().getLocation().getBlock())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        Entity damager = event.getDamager();

        // get the player

        // Only handle ItemFrames & Paintings
        if (!entity.getType().equals(EntityType.ITEM_FRAME) && !entity.getType().equals(EntityType.PAINTING)) {
            return;
        }

        // get the module
        Module module = this.moduleManager.getRegisteredModule(Material.ITEM_FRAME);
        if (entity.getType().equals(EntityType.PAINTING)) {
            module = this.moduleManager.getRegisteredModule(Material.PAINTING);
        }

        // is the module registered?
        if (module == null && !damager.getType().equals(EntityType.PLAYER)) {
            return;
        }

        // update the BlockVector & the ProtectionInfo
        this.vector.update(entity.getLocation());
        this.protectionInfo.update(this.vector);

        if (damager.getType().equals(EntityType.PLAYER)) {
            Player player = (Player) damager;
            // handle players
            if (module == null) {
                PlayerUtils.sendError(player, MoneyPitCore.NAME, "Module for '" + entity.getType().name() + "' is not registered!");
                return;
            }

            PlayerInteractEntityEvent newEvent = new PlayerInteractEntityEvent(player, entity);
            this.handleHangingInteract(newEvent, module);
            if (newEvent.isCancelled()) {
                event.setDamage(0d);
                event.setCancelled(true);
            }
            return;
        } else {
            // handle other entities
            HangingBreakByEntityEvent newEvent = new HangingBreakByEntityEvent((Hanging) event.getEntity(), damager);
            this.onHangingBreakByEntity(newEvent);
            if (newEvent.isCancelled()) {
                event.setDamage(0d);
                event.setCancelled(true);
            }
            return;
        }

    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingInteract(PlayerInteractEntityEvent event) {
        // Only handle ItemFrames & Paintings
        if (!event.getRightClicked().getType().equals(EntityType.ITEM_FRAME) && !event.getRightClicked().getType().equals(EntityType.PAINTING)) {
            return;
        }

        // get the module
        Module module = this.moduleManager.getRegisteredModule(Material.ITEM_FRAME);
        if (event.getRightClicked().getType().equals(EntityType.PAINTING)) {
            module = this.moduleManager.getRegisteredModule(Material.PAINTING);
        }

        // is the module registered?
        if (module == null) {
            PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "Module for '" + event.getRightClicked().getType().name() + "' is not registered!");
            return;
        }

        // update the BlockVector & the ProtectionInfo
        this.vector.update(event.getRightClicked().getLocation());
        this.protectionInfo.update(this.vector);

        // handle
        this.handleHangingInteract(event, module);
    }

    // //////////////////////////////////////////////////////////////////////
    //
    // INTERACT & INVENTORIES
    //
    // //////////////////////////////////////////////////////////////////////

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle Left- & Right-Click on a block
        Action action = event.getAction();

        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL) {
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
            case PROTECTION_ADD_PRIVATE :
            case PROTECTION_ADD_GIFT : {
                // the module must be registered
                Module module = this.moduleManager.getRegisteredModule(event.getClickedBlock().getType());
                if (module == null) {
                    PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "Module for block '" + event.getClickedBlock().getType().name() + "' is not registered!");
                    return;
                }

                // handle add
                this.handleAddInteract(event, module, state, module.isGiftable());
                break;
            }
            case PROTECTION_ADD_PUBLIC : {
                // the module must be registered
                Module module = this.moduleManager.getRegisteredModule(event.getClickedBlock().getType());
                if (module == null) {
                    PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "Module for block '" + event.getClickedBlock().getType().name() + "' is not registered!");
                    return;
                }

                // handle add
                this.handleAddInteract(event, module, state, module.isGiftable());
                break;
            }
            case PROTECTION_INVITE : {
                this.handleInviteInteract(event, true);
                break;
            }
            case PROTECTION_UNINVITE : {
                this.handleInviteInteract(event, false);
                break;
            }
            case PROTECTION_UNINVITEALL : {
                this.handleUninviteAllInteract(event);
                break;
            }
            default : {
                // handle normal interact
                this.handleNormalInteract(event);
                break;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        // handle inventory-events for gift-chests
        InventoryType type = event.getInventory().getType();
        if (type != InventoryType.CHEST) {
            return;
        }

        // remove a player from the giftchest-list when closing the inventory, no matter which inventory to be safe
        Player player = (Player) event.getPlayer();
        this.openedGiftChests.remove(player.getName());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // handle inventory-events for gift-chests
        InventoryType type = event.getInventory().getType();
        if (type != InventoryType.CHEST) {
            return;
        }

        Player player = (Player) event.getView().getPlayer();
        if (this.openedGiftChests.contains(player.getName())) {
            // get all needed vars
            ItemStack inSlot = event.getView().getItem(event.getRawSlot());
            boolean slotNull = (inSlot == null || inSlot.getType().equals(Material.AIR));
            boolean inChest = event.getRawSlot() <= event.getInventory().getSize() - 1;
            boolean shiftClick = event.isShiftClick();

            if (!inChest) {
                // click in normal inventory
                if (!slotNull && !shiftClick) {
                    this.wasPlayerInv.put(player.getName(), true);
                    return;
                } else {
                    this.wasPlayerInv.put(player.getName(), false);
                    return;
                }
            } else {
                // click in chest inventory
                Boolean lastPlayerInv = this.wasPlayerInv.get(player.getName());
                if (lastPlayerInv == null) {
                    lastPlayerInv = false;
                }

                if (!lastPlayerInv || !slotNull) {
                    PlayerUtils.sendError(player, MoneyPitCore.NAME, "You cannot take/move any items of this chest!");
                    event.setCancelled(true);
                    return;
                }

                this.wasPlayerInv.put(player.getName(), false);
            }
        }
    }

    // //////////////////////////////////////////////////////////////////////
    //
    // FROM HERE ON: METHODS TO HANDLE THE PLAYERINTERACT
    //
    // //////////////////////////////////////////////////////////////////////

    private void handleUninviteAllInteract(PlayerInteractEvent event) {
        // cancel the event
        event.setCancelled(true);

        // return to normalmode
        if (!this.playerManager.keepsMode(event.getPlayer().getName())) {
            this.playerManager.setState(event.getPlayer().getName(), PlayerState.NORMAL);
        }

        if (this.protectionInfo.hasProtection()) {
            // MainProtection

            if (this.protectionInfo.getProtection().isPublic()) {
                PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "You must click on a private protection.");
                this.showInformation(event.getPlayer());
                return;
            }

            boolean canEdit = this.protectionInfo.getProtection().canEdit(event.getPlayer());
            if (canEdit) {
                // clear guestlist
                this.protectionInfo.getProtection().clearGuestList();

                if (MoneyPitCore.databaseManager.updateGuestList(this.protectionInfo.getProtection(), this.protectionInfo.getProtection().getGuestList())) {
                    // send info
                    PlayerUtils.sendSuccess(event.getPlayer(), MoneyPitCore.NAME, "The guestlist has been cleared.");
                } else {
                    PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "Error while saving guestlist to database.");
                    PlayerUtils.sendInfo(event.getPlayer(), "Please contact an admin.");
                }
            } else {
                PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "You are not allowed to edit this protection.");
                this.showInformation(event.getPlayer());
            }
        } else if (this.protectionInfo.hasSubProtection()) {
            boolean canEdit = this.protectionInfo.getSubProtections().canEditAll(event.getPlayer());
            if (canEdit) {
                // for each SubProtection...
                boolean result = true;
                for (IProtection subProtection : this.protectionInfo.getSubProtections().getProtections()) {
                    if (subProtection.isPrivate()) {
                        // clear guestlist
                        subProtection.clearGuestList();
                    }
                    if (!MoneyPitCore.databaseManager.updateGuestList(subProtection.getMainProtection(), subProtection.getGuestList())) {
                        result = false;
                    }
                }

                // send info
                if (result) {
                    PlayerUtils.sendSuccess(event.getPlayer(), MoneyPitCore.NAME, "The guestlist has been cleared.");
                } else {
                    PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "Error while saving guestlist to database.");
                    PlayerUtils.sendInfo(event.getPlayer(), "Please contact an admin.");
                }
            } else {
                PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "You are not allowed to edit this protection.");
                this.showInformation(event.getPlayer());
            }
        } else {
            PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "This block is not protected.");
        }
    }

    private void handleInviteInteract(PlayerInteractEvent event, boolean add) {
        // cancel the event
        event.setCancelled(true);

        // return to normalmode
        if (!this.playerManager.keepsMode(event.getPlayer().getName())) {
            this.playerManager.setState(event.getPlayer().getName(), PlayerState.NORMAL);
        }

        if (this.protectionInfo.hasProtection()) {
            // MainProtection

            if (this.protectionInfo.getProtection().isPublic()) {
                PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "You must click on a private protection.");
                this.showInformation(event.getPlayer());
                return;
            }

            boolean canEdit = this.protectionInfo.getProtection().canEdit(event.getPlayer());
            if (canEdit) {
                // add people to guestlist
                for (String guest : this.playerManager.getGuestList(event.getPlayer().getName())) {
                    if (add) {
                        if (!this.protectionInfo.getProtection().isOwner(guest)) {
                            this.protectionInfo.getProtection().addGuest(guest);
                        }
                    } else {
                        this.protectionInfo.getProtection().removeGuest(guest);
                    }
                }
                // send info

                if (MoneyPitCore.databaseManager.updateGuestList(this.protectionInfo.getProtection(), this.protectionInfo.getProtection().getGuestList())) {
                    if (add)
                        PlayerUtils.sendSuccess(event.getPlayer(), MoneyPitCore.NAME, "Players have been added to the guestlist.");
                    else
                        PlayerUtils.sendSuccess(event.getPlayer(), MoneyPitCore.NAME, "Players have been removed from the guestlist.");
                } else {
                    PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "Error while saving guestlist to database.");
                    PlayerUtils.sendInfo(event.getPlayer(), "Please contact an admin.");
                }
            } else {
                PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "You are not allowed to edit this protection.");
                this.showInformation(event.getPlayer());
            }
        } else if (this.protectionInfo.hasSubProtection()) {
            boolean canEdit = this.protectionInfo.getSubProtections().canEditAll(event.getPlayer());
            if (canEdit) {
                // for each SubProtection...
                boolean result = true;
                for (IProtection subProtection : this.protectionInfo.getSubProtections().getProtections()) {
                    if (subProtection.isPrivate()) {
                        // add people to guestlist
                        for (String guest : this.playerManager.getGuestList(event.getPlayer().getName())) {
                            if (add) {
                                if (!subProtection.isOwner(guest)) {
                                    subProtection.addGuest(guest);
                                }
                            } else {
                                subProtection.removeGuest(guest);
                            }
                        }
                    }

                    if (!MoneyPitCore.databaseManager.updateGuestList(subProtection.getMainProtection(), subProtection.getGuestList())) {
                        result = false;
                    }
                }
                // send info
                if (result) {
                    if (add)
                        PlayerUtils.sendSuccess(event.getPlayer(), MoneyPitCore.NAME, "Players have been added to the guestlist.");
                    else
                        PlayerUtils.sendSuccess(event.getPlayer(), MoneyPitCore.NAME, "Players have been removed from the guestlist.");
                } else {
                    PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "Error while saving guestlist to database.");
                    PlayerUtils.sendInfo(event.getPlayer(), "Please contact an admin.");
                }
            } else {
                PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "You are not allowed to edit this protection.");
                this.showInformation(event.getPlayer());
            }
        } else {
            PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "This block is not protected.");
        }

        // clear guestlist
        if (!this.playerManager.keepsMode(event.getPlayer().getName())) {
            this.playerManager.clearGuestList(event.getPlayer().getName());
        }
    }

    private void showInformation(Player player) {
        this.showInformation(player, false);
    }

    private void showInformation(Player player, boolean showErrorMessage) {
        // we need a protection to show some information about it
        if (!this.protectionInfo.hasAnyProtection()) {
            if (showErrorMessage) {
                PlayerUtils.sendError(player, MoneyPitCore.NAME, "This block is not protected.");
            }
            return;
        }

        if (this.protectionInfo.hasProtection()) {
            // handle mainprotections
            String pType = " " + this.protectionInfo.getProtection().getType().toString() + " ";
            int moduleID = this.protectionInfo.getProtection().getBlockTypeID();  // get rid of this shit
            Material moduleType = this.protectionInfo.getProtection().getBlockType();
            if (moduleID < 1) {  // get rid of this shit
                Chunk chunk = this.protectionInfo.getProtection().getVector().getLocation().getBlock().getChunk();
                Entity[] entities = chunk.getEntities();
                for (Entity entity : entities) {
                    if (!entity.getType().equals(EntityType.ITEM_FRAME) && !entity.getType().equals(EntityType.PAINTING)) {
                        continue;
                    }
                    if (this.protectionInfo.getProtection().getVector().equals(new BlockVector(entity.getLocation()))) {
                        if (entity.getType().equals(EntityType.ITEM_FRAME)) {
                            moduleType = Material.ITEM_FRAME;
                        } else {
                            moduleType = Material.PAINTING;
                        }
                        break;
                    }
                }
                entities = null;
            }
            String message = "This" + ChatColor.RED + pType + moduleType.name() + ChatColor.GRAY + " is protected by " + ChatColor.YELLOW + this.protectionInfo.getProtection().getOwner() + ".";
            PlayerUtils.sendInfo(player, message);
            return;
        } else {
            // handle subprotections
            if (this.protectionInfo.getSubProtections().getSize() == 1) {
                String pType = " " + this.protectionInfo.getFirstProtection().getType().toString() + " ";
                Material moduleType = this.protectionInfo.getFirstProtection().getVector().getLocation().getBlock().getType();
                int moduleID = this.protectionInfo.getFirstProtection().getVector().getLocation().getBlock().getTypeId();  // get rid of this shit
                if (moduleID < 1) {  // get rid of this shit
                    Chunk chunk = this.protectionInfo.getFirstProtection().getVector().getLocation().getBlock().getChunk();
                    Entity[] entities = chunk.getEntities();
                    for (Entity entity : entities) {
                        if (!entity.getType().equals(EntityType.ITEM_FRAME) && !entity.getType().equals(EntityType.PAINTING)) {
                            continue;
                        }
                        if (this.protectionInfo.getFirstProtection().getVector().equals(new BlockVector(entity.getLocation()))) {
                            if (entity.getType().equals(EntityType.ITEM_FRAME)) {
                                moduleType = Material.ITEM_FRAME;
                            } else {
                                moduleType = Material.PAINTING;
                            }
                            break;
                        }
                    }
                    entities = null;
                }
                String message = "This" + ChatColor.RED + pType + moduleType.name() + ChatColor.GRAY + " is protected by " + ChatColor.YELLOW + this.protectionInfo.getFirstProtection().getOwner() + ".";
                PlayerUtils.sendInfo(player, message);
                return;
            } else if (this.protectionInfo.getSubProtections().getSize() > 1) {
                String message = "This " + ChatColor.RED + this.protectionInfo.getOrigin().getLocation().getBlock().getType() + ChatColor.GRAY + " is protected with " + ChatColor.YELLOW + "multiple protections.";
                PlayerUtils.sendInfo(player, message);
                return;
            }
        }
    }

    private void displayGuestList(Player player, Collection<Guest> guestList) {
        PlayerUtils.sendMessage(player, ChatColor.GRAY, "-------------------");
        PlayerUtils.sendMessage(player, ChatColor.DARK_AQUA, "Guestlist:");
        for (Guest guest : guestList) {
            if (guest.getName().length() < 1) {
                continue;
            }
            if (guest.getName().startsWith(GuestHelper.GROUP_PREFIX)) {
                PlayerUtils.sendMessage(player, ChatColor.GRAY, " - " + guest.getName().replaceFirst(GuestHelper.GROUP_PREFIX, "group: "));
            } else {
                PlayerUtils.sendMessage(player, ChatColor.GRAY, " - " + guest.getName());
            }
        }
        PlayerUtils.sendMessage(player, ChatColor.GRAY, "-------------------");
    }

    private void showExtendedInformation(Player player) {
        // we need a protection to show some information about it
        if (!this.protectionInfo.hasAnyProtection()) {
            PlayerUtils.sendError(player, MoneyPitCore.NAME, "This block is not protected.");
            return;
        }

        if (this.protectionInfo.hasProtection()) {
            // handle mainprotections
            String pType = " " + this.protectionInfo.getProtection().getType().toString() + " ";
            Material moduleType = this.protectionInfo.getProtection().getBlockType();
            int moduleID = this.protectionInfo.getProtection().getBlockTypeID();    // get rid of this shit
            if (moduleID < 1) { // get rid of this shit
                Chunk chunk = this.protectionInfo.getProtection().getVector().getLocation().getBlock().getChunk();
                Entity[] entities = chunk.getEntities();
                for (Entity entity : entities) {
                    if (!entity.getType().equals(EntityType.ITEM_FRAME) && !entity.getType().equals(EntityType.PAINTING)) {
                        continue;
                    }
                    if (this.protectionInfo.getProtection().getVector().equals(new BlockVector(entity.getLocation()))) {
                        if (entity.getType().equals(EntityType.ITEM_FRAME)) {
                            moduleType = Material.ITEM_FRAME;
                        } else {
                            moduleType = Material.PAINTING;
                        }
                        break;
                    }
                }
                entities = null;
            }
            String message = "This" + ChatColor.RED + pType + moduleType.name() + ChatColor.GRAY + " is protected by " + ChatColor.YELLOW + this.protectionInfo.getProtection().getOwner() + ".";

            if (this.protectionInfo.getProtection().canAccess(player)) {
                Collection<Guest> guestList = this.protectionInfo.getProtection().getGuestList();
                if (guestList != null) {
                    this.displayGuestList(player, guestList);
                }
            }

            PlayerUtils.sendInfo(player, message);
            return;
        } else {
            // handle subprotections
            if (this.protectionInfo.getSubProtections().getSize() == 1) {
                String pType = " " + this.protectionInfo.getFirstProtection().getType().toString() + " ";
                Material moduleType = this.protectionInfo.getFirstProtection().getVector().getLocation().getBlock().getType();
                int moduleID = this.protectionInfo.getFirstProtection().getVector().getLocation().getBlock().getTypeId();   // get rid of this shit
                if (moduleID < 1) { // get rid of this shit
                    Chunk chunk = this.protectionInfo.getFirstProtection().getVector().getLocation().getBlock().getChunk();
                    Entity[] entities = chunk.getEntities();
                    for (Entity entity : entities) {
                        if (!entity.getType().equals(EntityType.ITEM_FRAME) && !entity.getType().equals(EntityType.PAINTING)) {
                            continue;
                        }
                        if (this.protectionInfo.getFirstProtection().getVector().equals(new BlockVector(entity.getLocation()))) {
                            if (entity.getType().equals(EntityType.ITEM_FRAME)) {
                                moduleType = Material.ITEM_FRAME;
                            } else {
                                moduleType = Material.PAINTING;
                            }
                            break;
                        }
                    }
                    entities = null;
                }
                String message = "This" + ChatColor.RED + pType + moduleType.name() + ChatColor.GRAY + " is protected by " + ChatColor.YELLOW + this.protectionInfo.getFirstProtection().getOwner() + ".";
                PlayerUtils.sendInfo(player, message);

                if (this.protectionInfo.getFirstProtection().canAccess(player)) {
                    Collection<Guest> guestList = this.protectionInfo.getFirstProtection().getGuestList();
                    if (guestList != null) {
                        this.displayGuestList(player, guestList);
                    }
                }

                return;
            } else if (this.protectionInfo.getSubProtections().getSize() > 1) {
                String message = "This " + ChatColor.RED + this.protectionInfo.getOrigin().getLocation().getBlock().getType().name() + ChatColor.GRAY + " is protected with " + ChatColor.YELLOW + "multiple protections.";
                PlayerUtils.sendInfo(player, message);
                return;
            }
        }
    }

    private void handleInfoInteract(PlayerInteractEvent event) {
        // cancel the event
        event.setCancelled(true);

        // return to normalmode
        if (!this.playerManager.keepsMode(event.getPlayer().getName())) {
            this.playerManager.setState(event.getPlayer().getName(), PlayerState.NORMAL);
        }

        // show information
        this.showExtendedInformation(event.getPlayer());
    }

    private void handleRemoveInteract(PlayerInteractEvent event) {
        // return to normalmode
        if (!this.playerManager.keepsMode(event.getPlayer().getName())) {
            this.playerManager.setState(event.getPlayer().getName(), PlayerState.NORMAL);
        }

        // try to remove the protection
        if (!this.protectionInfo.hasAnyProtection()) {
            PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "This block is not protected!");
            return;
        } else if (this.protectionInfo.hasProtection()) {
            // get protection
            IProtection protection = this.protectionInfo.getProtection();

            // check permission
            if (!protection.canEdit(event.getPlayer())) {
                PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "You are not allowed to remove this protection!");
                event.setCancelled(true);
                return;
            }

            // create the vector
            BlockVector tempVector = new BlockVector(event.getClickedBlock().getLocation());

            // queue the event for later use in MonitorListener
            RemoveProtectionQueue queue = new RemoveProtectionQueue(event.getPlayer(), tempVector);
            this.queueManager.addQueue(queue);
        } else {
            // we have a SubProtection => check permissions and handle it
            if (!this.protectionInfo.getSubProtections().canEditAll(event.getPlayer())) {
                PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "You are not allowed to remove this subprotection!");
                event.setCancelled(true);
                return;
            }

            // create the vector
            BlockVector tempVector = new BlockVector(event.getClickedBlock().getLocation());

            // queue the event for later use in MonitorListener
            RemoveSubProtectionQueue queue = new RemoveSubProtectionQueue(event.getPlayer(), tempVector, this.protectionInfo.clone());
            this.queueManager.addQueue(queue);
        }
    }

    private void handleAddInteract(PlayerInteractEvent event, Module module, PlayerState state, boolean giftable) {
        // return to normalmode
        if (!this.playerManager.keepsMode(event.getPlayer().getName())) {
            this.playerManager.setState(event.getPlayer().getName(), PlayerState.NORMAL);
        }

        // check permissions
        if (!UtilPermissions.playerCanUseCommand(event.getPlayer(), "moneypit.protect." + module.getModuleName()) && !UtilPermissions.playerCanUseCommand(event.getPlayer(), "moneypit.admin")) {
            PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "You are not allowed to protect this block!");
            return;
        }

        // add protection, if it isn't protected yet
        if (!this.protectionInfo.hasAnyProtection()) {
            // create the vector
            BlockVector tempVector = new BlockVector(event.getClickedBlock().getLocation());

            if (state == PlayerState.PROTECTION_ADD_PRIVATE) {
                // create a private protection
                // queue the event for later use in MonitorListener
                AddProtectionQueue queue = new AddProtectionQueue(event.getPlayer(), module, tempVector, ProtectionType.PRIVATE);
                this.queueManager.addQueue(queue);
            } else if (state == PlayerState.PROTECTION_ADD_PUBLIC) {
                // create a public protection

                // queue the event for later use in MonitorListener
                AddProtectionQueue queue = new AddProtectionQueue(event.getPlayer(), module, tempVector, ProtectionType.PUBLIC);
                this.queueManager.addQueue(queue);
            } else if (state == PlayerState.PROTECTION_ADD_GIFT) {
                // create a gift protection
                if (giftable) {
                    // queue the event for later use in MonitorListener
                    AddProtectionQueue queue = new AddProtectionQueue(event.getPlayer(), module, tempVector, ProtectionType.GIFT);
                    this.queueManager.addQueue(queue);
                } else {
                    PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "This block cannot be a gift protection!");
                    event.setCancelled(true);
                    return;
                }
            }
        } else {
            // Send errormessage
            PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "Cannot create protection!");
            event.setCancelled(true);

            // show information about the protection
            this.showInformation(event.getPlayer());
        }
    }

    private void handleNormalInteract(PlayerInteractEvent event) {
        // ---------> WORKAROUND FOR CHESTS BEING ROTATED
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.CHEST)) {
            Module module = this.moduleManager.getRegisteredModule(Material.CHEST);
            if (module != null) {
                EventResult result = module.onPlace(event.getPlayer(), new BlockVector(event.getClickedBlock().getRelative(event.getBlockFace()).getLocation()));
                if (result.isCancelEvent()) {
                    event.setCancelled(true);
                    event.setUseInteractedBlock(Event.Result.DENY);
                    event.setUseItemInHand(Event.Result.DENY);
                    BlockVector vector = result.getProtection().getVector();
                    CraftPlayer cPlayer = (CraftPlayer) event.getPlayer();
                    cPlayer.sendBlockChange(vector.getLocation(), vector.getLocation().getBlock().getType(), vector.getLocation().getBlock().getData());
                }
                if (result.isAbort()) {
                    return;
                }
            }
        }
        // ---------> END WORKAROUND FOR CHESTS BEING ROTATED

        // CHECK: Protection?
        if (this.protectionInfo.hasProtection()) {
            boolean isAdmin = UtilPermissions.playerCanUseCommand(event.getPlayer(), "moneypit.admin");
            // is this protection private?
            if (!this.protectionInfo.getProtection().canAccess(event.getPlayer())) {
                // show information about the protection
                this.showInformation(event.getPlayer());
                // cancel the event
                event.setCancelled(true);
                return;
            }

            if (isAdmin) {
                // show information about the protection
                this.showInformation(event.getPlayer());
            }

            // handle gift-protections
            if (this.protectionInfo.getProtection().isGift()) {
                if (!this.protectionInfo.getProtection().canEdit(event.getPlayer()) && !this.protectionInfo.getProtection().isGuest(event.getPlayer().getName())) {
                    this.openedGiftChests.add(event.getPlayer().getName());
                }
            }

            // toggle both doors
            if (event.getAction() != Action.PHYSICAL) {
                if (event.getClickedBlock().getType().equals(Material.IRON_DOOR_BLOCK)) {
                    if (this.protectionInfo.getProtection().isPrivate()) {
                        DoorHelper.toggleDoor(event.getClickedBlock(), true);
                        DoorHelper.toggleSecondDoor(event.getClickedBlock(), true);
                    } else {
                        DoorHelper.toggleDoor(event.getClickedBlock(), false);
                        DoorHelper.toggleSecondDoor(event.getClickedBlock(), false);
                    }
                } else if (DoorHelper.isDoor(event.getClickedBlock().getType())) {
                    if (this.protectionInfo.getProtection().isPrivate()) {
                        MoneyPitCore.autoCloseTask.queue(event.getClickedBlock());
                        DoorHelper.toggleSecondDoor(event.getClickedBlock(), true);
                    } else {
                        DoorHelper.toggleSecondDoor(event.getClickedBlock(), false);
                    }
                }
            }
            return;
        }

        // CHECK: SubProtection?
        if (this.protectionInfo.hasSubProtection()) {
            boolean isAdmin = UtilPermissions.playerCanUseCommand(event.getPlayer(), "moneypit.admin");
            ISubProtectionHolder holder = this.protectionManager.getSubProtectionHolder(vector);
            for (IProtection subProtection : holder.getProtections()) {
                // is this protection private?
                if (!subProtection.isPrivate()) {
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
            if (isAdmin) {
                this.showInformation(event.getPlayer());
            }

            // handle gift-protections
            if (this.protectionInfo.getFirstProtection().isGift()) {
                if (!this.protectionInfo.getFirstProtection().canEdit(event.getPlayer()) && !this.protectionInfo.getFirstProtection().isGuest(event.getPlayer().getName())) {
                    this.openedGiftChests.add(event.getPlayer().getName());
                }
            }

            // toggle both doors
            if (event.getAction() != Action.PHYSICAL) {
                IProtection subProtection = this.protectionInfo.getSubProtections().getProtection(0);
                if (event.getClickedBlock().getType().equals(Material.IRON_DOOR_BLOCK)) {
                    if (subProtection.isPrivate()) {
                        DoorHelper.toggleDoor(event.getClickedBlock(), true);
                        DoorHelper.toggleSecondDoor(event.getClickedBlock(), true);
                    } else {
                        DoorHelper.toggleDoor(event.getClickedBlock(), false);
                        DoorHelper.toggleSecondDoor(event.getClickedBlock(), false);
                    }
                } else if (DoorHelper.isDoor(event.getClickedBlock().getType())) {
                    if (subProtection.isPrivate()) {
                        MoneyPitCore.autoCloseTask.queue(event.getClickedBlock());
                        DoorHelper.toggleSecondDoor(event.getClickedBlock(), true);
                    } else {
                        DoorHelper.toggleSecondDoor(event.getClickedBlock(), false);
                    }
                }
            }

            return;
        }

        // toggle both doors
        if (event.getAction() != Action.PHYSICAL) {
            if (DoorHelper.isDoor(event.getClickedBlock().getType()) && !event.getClickedBlock().getType().equals(Material.IRON_DOOR_BLOCK)) {
                DoorHelper.toggleSecondDoor(event.getClickedBlock(), false);
            }
        }
    }
    // //////////////////////////////////////////////////////////////////////
    //
    // FROM HERE ON: METHODS TO HANDLE THE HANGING ENTITIES
    //
    // //////////////////////////////////////////////////////////////////////

    private void handleHangingInfoInteract(PlayerInteractEntityEvent event) {
        // cancel the event
        event.setCancelled(true);

        // return to normalmode
        if (!this.playerManager.keepsMode(event.getPlayer().getName())) {
            this.playerManager.setState(event.getPlayer().getName(), PlayerState.NORMAL);
        }

        // show information
        this.showExtendedInformation(event.getPlayer());
    }

    private void handleHangingAddInteract(PlayerInteractEntityEvent event, Module module, PlayerState state, Hanging entity) {
        // return to normalmode
        if (!this.playerManager.keepsMode(event.getPlayer().getName())) {
            this.playerManager.setState(event.getPlayer().getName(), PlayerState.NORMAL);
        }

        // check permissions
        if (!UtilPermissions.playerCanUseCommand(event.getPlayer(), "moneypit.protect." + module.getModuleName()) && !UtilPermissions.playerCanUseCommand(event.getPlayer(), "moneypit.admin")) {
            PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "You are not allowed to protect this block!");
            return;
        }

        // add protection, if it isn't protected yet
        if (!this.protectionInfo.hasAnyProtection()) {
            // create the vector
            BlockVector tempVector = new BlockVector(event.getRightClicked().getLocation());
            if (state == PlayerState.PROTECTION_ADD_PRIVATE) {
                // create a private protection
                // queue the event for later use in MonitorListener
                AddProtectionQueue queue = new AddProtectionQueue(event.getPlayer(), module, tempVector, ProtectionType.PRIVATE, (byte) entity.getAttachedFace().ordinal());
                this.queueManager.addQueue(queue);
            } else if (state == PlayerState.PROTECTION_ADD_PUBLIC) {
                // create protection
                PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "This cannot be a public protection!");
                event.setCancelled(true);
                return;
            } else if (state == PlayerState.PROTECTION_ADD_GIFT) {
                // create protection
                PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "This cannot be a gift protection!");
                event.setCancelled(true);
                return;
            }
        } else {
            // Send errormessage
            PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "Cannot create protection!");
            event.setCancelled(true);

            // show information about the protection
            this.showInformation(event.getPlayer());
        }
    }

    private void handleHangingRemoveInteract(PlayerInteractEntityEvent event) {
        // return to normalmode
        if (!this.playerManager.keepsMode(event.getPlayer().getName())) {
            this.playerManager.setState(event.getPlayer().getName(), PlayerState.NORMAL);
        }

        // try to remove the protection
        if (!this.protectionInfo.hasAnyProtection()) {
            PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "This block is not protected!");
            return;
        } else if (this.protectionInfo.hasProtection()) {
            // get protection
            IProtection protection = this.protectionInfo.getProtection();

            // check permission
            if (!protection.canEdit(event.getPlayer())) {
                PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "You are not allowed to remove this protection!");
                event.setCancelled(true);
                return;
            }

            // create the vector
            BlockVector tempVector = new BlockVector(event.getRightClicked().getLocation());

            // queue the event for later use in MonitorListener
            RemoveProtectionQueue queue = new RemoveProtectionQueue(event.getPlayer(), tempVector);
            this.queueManager.addQueue(queue);
        } else {
            // we have a SubProtection => check permissions and handle it
            if (!this.protectionInfo.getSubProtections().canEditAll(event.getPlayer())) {
                PlayerUtils.sendError(event.getPlayer(), MoneyPitCore.NAME, "You are not allowed to remove this subprotection!");
                event.setCancelled(true);
                return;
            }

            // create the vector
            BlockVector tempVector = new BlockVector(event.getRightClicked().getLocation());

            // queue the event for later use in MonitorListener
            RemoveSubProtectionQueue queue = new RemoveSubProtectionQueue(event.getPlayer(), tempVector, this.protectionInfo.clone());
            this.queueManager.addQueue(queue);
        }
    }

    private void handleHangingNormalInteract(PlayerInteractEntityEvent event) {
        // CHECK: Protection?
        if (this.protectionInfo.hasProtection()) {
            boolean isAdmin = UtilPermissions.playerCanUseCommand(event.getPlayer(), "moneypit.admin");
            // is this protection private?
            if (!this.protectionInfo.getProtection().canAccess(event.getPlayer())) {
                // show information about the protection
                this.showInformation(event.getPlayer());
                // cancel the event
                event.setCancelled(true);
                return;
            }

            if (isAdmin) {
                // show information about the protection
                this.showInformation(event.getPlayer());
            }

            // handle gift-protections
            if (this.protectionInfo.getProtection().isGift()) {
                if (!this.protectionInfo.getProtection().canEdit(event.getPlayer()) && !this.protectionInfo.getProtection().isGuest(event.getPlayer().getName())) {
                    this.openedGiftChests.add(event.getPlayer().getName());
                }
            }
            return;
        }

        // CHECK: SubProtection?
        if (this.protectionInfo.hasSubProtection()) {
            boolean isAdmin = UtilPermissions.playerCanUseCommand(event.getPlayer(), "moneypit.admin");
            ISubProtectionHolder holder = this.protectionManager.getSubProtectionHolder(vector);
            for (IProtection subProtection : holder.getProtections()) {
                // is this protection private?
                if (!subProtection.isPrivate()) {
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
            if (isAdmin) {
                this.showInformation(event.getPlayer());
            }

            // handle gift-protections
            if (this.protectionInfo.getFirstProtection().isGift()) {
                if (!this.protectionInfo.getFirstProtection().canEdit(event.getPlayer()) && !this.protectionInfo.getFirstProtection().isGuest(event.getPlayer().getName())) {
                    this.openedGiftChests.add(event.getPlayer().getName());
                }
            }
            return;
        }
    }

    // //////////////////////////////////////////////////////////////////////
    //
    // FROM HERE ON: EVENTS THAT ARE NOT DIRECTLY TRIGGERED BY A PLAYER
    //
    // //////////////////////////////////////////////////////////////////////

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        List <Block> changedBlocks = event.getBlocks();
        for (Block block : changedBlocks) {
            if (this.cancelBlockEvent(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        List<Block> blocks = event.getBlocks();
        for (Block block : blocks)
        {
            if (this.cancelBlockEvent(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (this.cancelBlockEvent(event.getToBlock())) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (this.cancelBlockEvent(block)) {
                event.setCancelled(true);
                event.setYield(0f);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (this.cancelBlockEvent(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBreakDoor(EntityBreakDoorEvent event) {
        if (this.cancelBlockEvent(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
    }

    private boolean cancelBlockEvent(Block block) {
        // update the BlockVector & the ProtectionInfo
        this.vector.update(block.getLocation());
        this.protectionInfo.update(this.vector);

        // cancel the event, if the block is protected
        if (this.protectionInfo.hasAnyProtection()) {
            return true;
        }

        return false;
    }
}