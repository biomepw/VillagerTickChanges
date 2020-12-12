package pw.biome.villagertickchanges;

import co.aikar.commands.PaperCommandManager;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VillagerTickChanges extends JavaPlugin implements Listener {

    @Getter
    private static final Map<UUID, Villager> noTickVillagers = new HashMap<>();

    @Getter
    private static VillagerTickChanges plugin;

    private NamespacedKey key;
    private PaperCommandManager commandManager;
    private List<String> villagerUUIDs;


    @Override
    public void onEnable() {
        plugin = this;
        key = new NamespacedKey(this, "lastRestock");
        saveDefaultConfig();

        villagerUUIDs = getConfig().getStringList("disabled-villagers");

        // Register commands
        commandManager = new PaperCommandManager(this);
        commandManager.registerCommand(new CommandHandler());

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, this::restockTask, 20 * 60, 20 * 60);

        // Loop to continue to try load all villagers every minute
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () ->
                villagerUUIDs.forEach(stringUUID -> {
                    UUID uuid = UUID.fromString(stringUUID);
                    Entity entity = Bukkit.getEntity(uuid);
                    if (entity != null) {
                        Villager villager = (Villager) entity;
                        noTickVillagers.put(uuid, villager);
                    }
                }), 1, 20 * 10);
    }

    /**
     * On disable, save all entities
     */
    @Override
    public void onDisable() {
        saveData();
        commandManager.unregisterCommands();
        getServer().getScheduler().cancelTasks(this);
    }

    /**
     * Task to refresh villager trades
     */
    private void restockTask() {
        noTickVillagers.values().forEach(this::refreshTrades);
    }

    /**
     * Attempts to refresh trades
     *
     * @param villager to try refresh
     */
    private void refreshTrades(Villager villager) {
        if (villager == null) return;
        if (!villager.getLocation().getChunk().isLoaded()) return;

        var pdc = villager.getPersistentDataContainer();
        var lastRestock = pdc.get(this.key, PersistentDataType.LONG);
        if (lastRestock == null) {
            lastRestock = 0L;
        }

        // Restock every 10 minutes
        if (System.currentTimeMillis() - lastRestock > (10 * 60 * 1000)) {
            lastRestock = System.currentTimeMillis();
            pdc.set(this.key, PersistentDataType.LONG, lastRestock);

            var recipes = new ArrayList<>(villager.getRecipes());
            for (var recipe : recipes) {
                recipe.setUses(0);
            }

            villager.setRecipes(recipes);
        }
    }

    /**
     * Save the entities UUID string list to config
     */
    private void saveData() {
        getConfig().set("disabled-villagers", villagerUUIDs);
        saveConfig();
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        var player = event.getPlayer();
        var uuid = player.getUniqueId();
        var entity = event.getRightClicked();

        // Only proceed if it is a villager
        if (!(entity instanceof Villager)) return;

        var villager = (Villager) entity;

        // if the player is selecting!
        if (CommandHandler.getSelecting().contains(uuid)) {
            if (!toggleVillager(villager)) {
                player.sendMessage(ChatColor.GOLD + "This villager is now " + ChatColor.RED + "INACTIVE!");
            } else {
                player.sendMessage(ChatColor.GOLD + "This villager is now " + ChatColor.RED + "ACTIVE!");
            }

            // Remove and cancel event
            CommandHandler.getSelecting().remove(uuid);
            event.setCancelled(true);
        }
    }

    /**
     * Toggle a villager
     *
     * @param villager to toggle
     * @return whether or not they are active after the toggle
     */
    public boolean toggleVillager(Villager villager) {
        var isActive = villager.isAware();
        if (isActive) {
            noTickVillagers.put(villager.getUniqueId(), villager);
            villagerUUIDs.add(villager.getUniqueId().toString());
            villager.setAware(false);
            getServer().getScheduler().runTaskAsynchronously(this, this::saveData);
        } else {
            villager.setAware(true);
            noTickVillagers.remove(villager.getUniqueId());
        }
        return !isActive;
    }

    /**
     * If a villager is removed from the world for some reason, try and remove them from the no tick list
     *
     * @param event entity removed from world event
     */
    @EventHandler
    public void removedFromWorld(EntityRemoveFromWorldEvent event) {
        var entity = event.getEntity();
        if (entity instanceof Villager) {
            var villager = (Villager) entity;
            noTickVillagers.remove(villager.getUniqueId());
        }
    }
}