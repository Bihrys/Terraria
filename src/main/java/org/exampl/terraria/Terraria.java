package org.exampl.terraria;

import org.bukkit.plugin.java.JavaPlugin;

public final class Terraria extends JavaPlugin {

    @Override
    public void onEnable() {
        getCommand("checkhouse").setExecutor(new HouseCommand());
        getLogger().info("Terraria 动态房屋检测已加载");
    }

    @Override
    public void onDisable() {
    }
}