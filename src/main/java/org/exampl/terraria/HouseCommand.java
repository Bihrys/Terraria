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

        // 执行检测并把调试/统计信息直接反馈给玩家
        checkHouseAndReport(player);

        return true;
    }

    /**
     * 执行检测并向玩家输出统计信息与最终判定
     */
    private void checkHouseAndReport(Player player) {
        Block start = findStartingAir(player);

        if (start == null) {
            player.sendMessage(ChatColor.RED + "附近没有可检测的空气空间或你站在不支持检测的方块上！");
            return;
        }

        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();

        queue.add(start);
        visited.add(start);

        int airBlocks = 0;               // 真实空气计数
        int nonFullLightBlocks = 0;      // 非完整光源（如火把/挂火把/灵魂火把/烛台等）
        int carpetBlocks = 0;            // 地毯
        int passThroughOther = 0;        // 其它可穿透装饰物计数（按钮、压力板、花等）
        boolean hasDoor = false;
        boolean hasWorkbench = false;
        boolean hasBed = false;          // 新增：床检测
        boolean hasAnyLight = false;     // 是否存在任意光源（包括非完整光源）
        boolean leaked = false;
        boolean tooLarge = false;

        Location origin = start.getLocation();

        while (!queue.isEmpty()) {
            Block current = queue.poll();

            // 防止遍历过多块导致性能问题
            if (visited.size() > MAX_BLOCKS) {
                tooLarge = true;
                break;
            }

            // 半径泄露判断（如果某个块超出最大半径则视为泄露）
            if (current.getLocation().distance(origin) > MAX_RADIUS) {
                leaked = true;
                break;
            }

            Material currType = current.getType();

            // 计数规则：把光源与地毯等"非完整方块"当作可用空间（等同空气）统计
            if (currType.isAir()) {
                airBlocks++;
            } else if (isNonFullLightSource(currType)) {
                nonFullLightBlocks++;
                hasAnyLight = true;
            } else if (isCarpet(currType)) {
                carpetBlocks++;
            } else if (isPassThrough(currType)) {
                passThroughOther++;
            }

            // ===== 家具检测 【优先】：对所有相邻方块检测门/工作台/床/光源 =====
            for (int[] dir : new int[][]{
                    {1,0,0},{-1,0,0},
                    {0,1,0},{0,-1,0},
                    {0,0,1},{0,0,-1}
            }) {
                Block neighbor = current.getRelative(dir[0], dir[1], dir[2]);
                Material type = neighbor.getType();
                String name = type.name();

                // 家具检测（放在前面，对所有相邻方块都检测）
                if (name.contains("DOOR")) hasDoor = true;
                if (type == Material.CRAFTING_TABLE) hasWorkbench = true;
                if (name.contains("BED")) hasBed = true;  // 检测床
                if (isLightSource(type)) hasAnyLight = true;

                // ===== 扩散规则 【次之】：只有满足条件的才加入 BFS 队列 =====
                if ((type.isAir() || isNonFullLightSource(type) || isCarpet(type) || isPassThrough(type))
                        && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        // 合并统计：把所有被视为"可用空间"的块相加
        int totalAvailable = airBlocks + nonFullLightBlocks + carpetBlocks + passThroughOther;

        // 输出统计信息，便于你验证算法
        player.sendMessage(ChatColor.AQUA + "=== 房屋检测统计 ===");
        player.sendMessage(ChatColor.YELLOW + "起点: " + formatLocation(start.getLocation()));
        player.sendMessage(ChatColor.YELLOW + "遍历总块数（visited）: " + visited.size());
        player.sendMessage(ChatColor.YELLOW + "可用空间总数: " + totalAvailable
                + " (空气:" + airBlocks + ", 火把/小光源:" + nonFullLightBlocks
                + ", 地毯:" + carpetBlocks + ", 其它可穿透:" + passThroughOther + ")");

        player.sendMessage(ChatColor.YELLOW + "门: " + (hasDoor ? ChatColor.GREEN + "有" : ChatColor.RED + "无")
                + ChatColor.YELLOW + " ，工作台: " + (hasWorkbench ? ChatColor.GREEN + "有" : ChatColor.RED + "无")
                + ChatColor.YELLOW + " ，床: " + (hasBed ? ChatColor.GREEN + "有" : ChatColor.RED + "无")
                + ChatColor.YELLOW + " ，光源: " + (hasAnyLight ? ChatColor.GREEN + "有" : ChatColor.RED + "无"));

        player.sendMessage(ChatColor.YELLOW + "是否泄露(超出半径): " + (leaked ? ChatColor.RED + "是" : ChatColor.GREEN + "否"));
        player.sendMessage(ChatColor.YELLOW + "是否过大(超过 MAX_BLOCKS=" + MAX_BLOCKS + "): " + (tooLarge ? ChatColor.RED + "是" : ChatColor.GREEN + "否"));

        // 最终判定（至少 20 个可用块，且具备门/工作台/床/光源，且未泄露且未过大）
        boolean isValid = !leaked && !tooLarge && totalAvailable >= 20 && hasDoor && hasWorkbench && hasBed && hasAnyLight;

        if (isValid) {
            player.sendMessage(ChatColor.GREEN + "✔ 这是一个合格房屋！");
        } else {
            player.sendMessage(ChatColor.RED + "✘ 房屋不合格或未封闭！");
        }
    }

    /**
     * 自动寻找玩家脚下或周围的"起点"。
     * 起点优先选空气，其次非完整光源/地毯/可穿透装饰物，最后在附近小范围内搜索。
     */
    private Block findStartingAir(Player player) {
        Block base = player.getLocation().getBlock();

        // 先检查玩家当前位置与头顶（玩家身高 2 格）
        Block body1 = base;
        Block body2 = base.getRelative(0, 1, 0);

        if (isCandidateStart(body1)) return body1;
        if (isCandidateStart(body2)) return body2;

        // 尝试脚下1格
        Block down = base.getRelative(0, -1, 0);
        if (isCandidateStart(down)) return down;

        // 在玩家周围小范围（-1..1）内查找
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block nearby = base.getRelative(x, y, z);
                    if (isCandidateStart(nearby)) {
                        return nearby;
                    }
                }
            }
        }

        return null;
    }

    /** 判定能否作为 BFS 起点：空气 / 非完整光源 / 地毯 / 其他可穿透装饰物 */
    private boolean isCandidateStart(Block b) {
        Material t = b.getType();
        return t.isAir() || isNonFullLightSource(t) || isCarpet(t) || isPassThrough(t);
    }

    /**
     * 判定是否为"非完整光源"（小型/挂墙类光源，视作占位但可穿透）
     */
    private boolean isNonFullLightSource(Material type) {
        String n = type.name();
        return n.contains("TORCH") ||
                n.contains("CANDLE") ||
                n.contains("SOUL_TORCH") ||
                n.contains("WALL_TORCH") ||
                n.contains("SOUL_WALL_TORCH");
    }

    /** 判定是否为地毯 */
    private boolean isCarpet(Material type) {
        return type.name().contains("CARPET");
    }

    /**
     * 判定是否为可穿过类装饰物（不计为完整方块，但不是光源/地毯）
     */
    private boolean isPassThrough(Material type) {
        String n = type.name();
        return n.contains("BUTTON") ||
                n.contains("PRESSURE_PLATE") ||
                n.contains("FLOWER") ||
                n.contains("MUSHROOM") ||
                n.contains("LEVER") ||
                n.contains("SAPLING") ||
                n.contains("TRAPDOOR") ||
                n.contains("SIGN") ||
                n.contains("BELL") ||
                n.contains("BANNER") ||
                n.contains("LILY_PAD");
    }

    /** 判定是否为光源（用于 hasAnyLight 检测）——包含完整光源与非完整光源 */
    private boolean isLightSource(Material material) {
        String name = material.name();
        return name.contains("TORCH") ||
                name.contains("LANTERN") ||
                name.contains("CANDLE") ||
                name.contains("SEA_LANTERN") ||
                name.contains("GLOWSTONE") ||
                name.contains("SHROOMLIGHT");
    }

    private String formatLocation(Location loc) {
        return "World=" + loc.getWorld().getName() + " x=" + loc.getBlockX() + " y=" + loc.getBlockY() + " z=" + loc.getBlockZ();
    }
}