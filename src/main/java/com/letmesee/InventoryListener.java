package com.letmesee;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class InventoryListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (isReadOnly(event.getView().getTopInventory().getHolder())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (isReadOnly(event.getView().getTopInventory().getHolder())) {
            event.setCancelled(true);
        }
    }

    private boolean isReadOnly(InventoryHolder holder) {
        return holder instanceof ReadOnlyHolder;
    }
}
