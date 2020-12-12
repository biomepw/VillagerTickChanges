package pw.biome.villagertickchanges;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@CommandAlias("disablevillager|dv")
@Description("Disables ticking of a villager")
public class CommandHandler extends BaseCommand {

    @Getter
    private static final Set<UUID> selecting = new HashSet<>();

    @Default
    public void onCommand(Player player) {
        var uuid = player.getUniqueId();
        if (selecting.contains(uuid)) {
            selecting.remove(uuid);
            player.sendMessage(ChatColor.RED + "Cancelled villager selection!");
        } else {
            selecting.add(uuid);
            player.sendMessage(ChatColor.GREEN + "Please right click the villager you want to toggle!");
        }
    }

    @Subcommand("stats")
    @CommandPermission("villagertickchanges.admin")
    public void check(Player player) {
        var size = VillagerTickChanges.getNoTickVillagers().size();
        player.sendMessage(ChatColor.GREEN + "There are currently " + ChatColor.AQUA + size + ChatColor.GREEN
                + " villagers manually set to no AI");
    }

    @Subcommand("radius")
    public void radius(Player player, Integer radius) {
        player.getNearbyEntities(radius, radius, radius)
                .stream()
                .filter(entity -> entity instanceof Villager)
                .forEach(villager -> VillagerTickChanges.getPlugin().toggleVillager((Villager) villager));
    }
}
