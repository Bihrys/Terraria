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

        Block start = player.getLocation().getBlock();

        if (!start.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "请站在房屋内部空气中");
            return false;
        }

        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();

        queue.add(start);
        visited.add(start);

        int airCount = 0;
        boolean hasDoor = false;
        boolean hasBed = false;
        boolean hasLight = false;

        Location origin = start.getLocation();

        while (!queue.isEmpty()) {

            Block current = queue.poll();

            if (visited.size() > MAX_BLOCKS) {
                return false; // 太大，判为不合法
            }

            if (current.getLocation().distance(origin) > MAX_RADIUS) {
                return false; // 扩散太远，说明没封闭
            }

            airCount++;

            // 扫描六个方向
            for (int[] dir : new int[][]{
                    {1,0,0},{-1,0,0},
                    {0,1,0},{0,-1,0},
                    {0,0,1},{0,0,-1}
            }) {

                Block neighbor = current.getRelative(dir[0], dir[1], dir[2]);

                String name = neighbor.getType().name();

                if (name.contains("DOOR")) hasDoor = true;
                if (name.contains("BED")) hasBed = true;
                if (name.contains("TORCH")) hasLight = true;

                if (neighbor.getType().isAir() && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return airCount >= 20 && hasDoor && hasBed && hasLight;
    }
}