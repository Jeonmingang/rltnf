package gg.pokebuilderlite;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class PokebuilderLite extends JavaPlugin {
    @Override
    public void onEnable() {
        PBCommand executor = new PBCommand(this);
        PluginCommand cmd = this.getCommand("pb");
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }
        getLogger().info("[PokebuilderLite] Enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("[PokebuilderLite] Disabled.");
    }
}
