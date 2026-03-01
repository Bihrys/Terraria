package org.exampl.terraria;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class HouseCommand implements CommandExecutor {

    private static final int MAX_BLOCKS = 500;
    private static final int MAX_RADIUS = 12;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能玩家使用");
            return true;
        }

        boolean result = checkHouse(player);

        if (result) {
            player.sendMessage(ChatColor.GREEN + "✔ 这是一个合格房屋！");
        } else {
            player.sendMessage(ChatColor.RED + "✘ 房屋不合格或未封闭！");
        }

        return true;
    }

    private boolean checkHouse(Player player) {

        Block start = findStartingAir(player);

        if (start == null) {
            player.sendMessage(ChatColor.RED + "附近没有可检测的空气空间");
            return false;
        }

        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();

        queue.add(start);
        visited.add(start);

        int airCount = 0;
        boolean hasDoor = false;
        boolean hasWorkbench = false;
        boolean hasLight = false;

        Location origin = start.getLocation();

        while (!queue.isEmpty()) {

            Block current = queue.poll();

            if (visited.size() > MAX_BLOCKS) {
                return false; // 空间太大
            }

            if (current.getLocation().distance(origin) > MAX_RADIUS) {
                return false; // 扩散到外界 → 不封闭
            }

            airCount++;

            for (int[] dir : new int[][]{
                    {1,0,0},{-1,0,0},
                    {0,1,0},{0,-1,0},
                    {0,0,1},{0,0,-1}
            }) {

                Block neighbor = current.getRelative(dir[0], dir[1], dir[2]);
                Material type = neighbor.getType();
                String name = type.name();

                // 家具检测
                if (name.contains("DOOR")) hasDoor = true;
                if (type == Material.CRAFTING_TABLE) hasWorkbench = true;
                if (isLightSource(type)) hasLight = true;

                // 只在空气中扩散
                if (type.isAir() && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return airCount >= 20 && hasDoor && hasWorkbench && hasLight;
    }

    //自动寻找玩家脚下或周围的空气块作为起点

    private Block findStartingAir(Player player) {

        Block base = player.getLocation().getBlock();

        // 如果玩家站在空气中
        if (base.getType().isAir()) {
            return base;
        }

        // 尝试检测脚下1格
        Block down = base.getRelative(0, -1, 0);
        if (down.getType().isAir()) {
            return down;
        }

        // 检测周围一圈是否有空气
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {

                    Block nearby = base.getRelative(x, y, z);

                    if (nearby.getType().isAir()) {
                        return nearby;
                    }
                }
            }
        }

        return null;
    }

    //判断是否为光源

    private boolean isLightSource(Material material) {

        String name = material.name();

        return name.contains("TORCH") ||
                name.contains("LANTERN") ||
                name.contains("CANDLE") ||
                name.contains("SEA_LANTERN") ||
                name.contains("GLOWSTONE") ||
                name.contains("SHROOMLIGHT");
    }
}