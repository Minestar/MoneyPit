package de.minestar.moneypit.manager;

import java.util.Collection;
import java.util.HashMap;

import de.minestar.moneypit.data.BlockVector;
import de.minestar.moneypit.data.Protection;
import de.minestar.moneypit.data.SubProtection;
import de.minestar.moneypit.data.SubProtectionHolder;

public class ProtectionManager {

    private HashMap<BlockVector, Protection> protections;
    private HashMap<BlockVector, SubProtectionHolder> subProtections;

    /**
     * Initialize the manager
     */
    public void init() {
        this.protections = new HashMap<BlockVector, Protection>();
        this.subProtections = new HashMap<BlockVector, SubProtectionHolder>();

        // TODO: load protections
    }
    // ////////////////////////////////////////////////////////////////
    //
    // Protection
    //
    // ////////////////////////////////////////////////////////////////

    public Protection getProtection(BlockVector vector) {
        return this.protections.get(vector);
    }

    /**
     * Add a protection
     * 
     * @param vector
     * @param protection
     */
    public void addProtection(Protection protection) {
        // register the protection
        this.protections.put(protection.getVector(), protection);

        // register the subprotections
        if (protection.hasAnySubProtection()) {
            Collection<SubProtection> subs = protection.getSubProtections();
            for (SubProtection sub : subs) {
                this.addSubProtection(sub);
            }
        }
    }

    private void addSubProtection(SubProtection subProtection) {
        SubProtectionHolder holder = this.getSubProtectionHolder(subProtection.getVector());
        if (holder == null) {
            holder = new SubProtectionHolder();
            this.addSubProtectionHolder(subProtection.getVector(), holder);
        }
        holder.addProtection(subProtection);
    }

    /**
     * Remove a protection
     * 
     * @param vector
     * @param protection
     */
    public void removeProtection(BlockVector vector) {
        Protection protection = this.protections.get(vector);
        if (protection != null) {
            // remove the protection
            this.protections.remove(vector);
            // remove the subprotections
            if (protection.hasAnySubProtection()) {
                Collection<SubProtection> subs = protection.getSubProtections();
                for (SubProtection sub : subs) {
                    this.removeSubProtection(sub);
                }
            }
        }
    }

    private void removeSubProtection(SubProtection subProtection) {
        SubProtectionHolder holder = this.getSubProtectionHolder(subProtection.getVector());
        if (holder != null) {
            holder.removeProtection(subProtection);
        }

        if (holder.getSize() < 1) {
            this.removeProtectionHolder(subProtection.getVector());
        }
    }

    public boolean hasProtection(BlockVector vector) {
        return this.protections.containsKey(vector);
    }

    // ////////////////////////////////////////////////////////////////
    //
    // ProtectionHolder
    //
    // ////////////////////////////////////////////////////////////////

    /**
     * Check if a block has a ProtectionHolder
     * 
     * @param vector
     * @return <b>true</b> if the block is protected, otherwise <b>false</b>
     */
    public boolean hasSubProtectionHolder(BlockVector vector) {
        return this.subProtections.containsKey(vector);
    }

    /**
     * Add a ProtectionHolder to a block
     * 
     * @param vector
     */
    private void addSubProtectionHolder(BlockVector vector, SubProtectionHolder holder) {
        if (!this.hasSubProtectionHolder(vector)) {
            this.subProtections.put(vector, holder);
        }
    }

    /**
     * Get the ProtectionHolder from a block
     * 
     * @param vector
     * @return
     */
    public SubProtectionHolder getSubProtectionHolder(BlockVector vector) {
        return this.subProtections.get(vector);
    }

    /**
     * Remove the ProtectionHolder from a block
     * 
     * @param vector
     */
    private void removeProtectionHolder(BlockVector vector) {
        if (this.hasSubProtectionHolder(vector)) {
            this.subProtections.remove(vector);
        }
    }
}
