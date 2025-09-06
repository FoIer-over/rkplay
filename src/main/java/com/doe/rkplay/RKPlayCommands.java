package com.doe.rkplay;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;

public class RKPlayCommands {
    
    private static final Map<UUID, DuelRequest> duelRequests = new HashMap<>();
    private static final Map<UUID, Team> teams = new HashMap<>();
    private static final Map<UUID, Integer> playerMarks = new HashMap<>();
    private static final int DEFAULT_MARK = 20;
    private static final Map<UUID, Set<UUID>> teamInvites = new HashMap<>();
    
    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("rkp")
            .then(CommandManager.literal("duel")
                .then(CommandManager.argument("player", StringArgumentType.word())
                    .executes(RKPlayCommands::duelChallenge))
                .then(CommandManager.literal("accept")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .executes(RKPlayCommands::duelAccept)))
                .then(CommandManager.literal("cancel")
                    .executes(RKPlayCommands::duelCancel)))
            .then(CommandManager.literal("team")
                .then(CommandManager.literal("create")
                    .executes(RKPlayCommands::teamCreate))
                .then(CommandManager.literal("delete")
                    .then(CommandManager.argument("teamId", StringArgumentType.word())
                        .executes(RKPlayCommands::teamDelete)))
                .then(CommandManager.literal("list")
                    .then(CommandManager.literal("joinlist")
                        .executes(RKPlayCommands::teamJoinList))
                    .then(CommandManager.literal("myteamlist")
                        .executes(RKPlayCommands::teamMyTeamList))
                    .then(CommandManager.literal("teamplayerlist")
                        .then(CommandManager.argument("teamId", StringArgumentType.word())
                            .executes(RKPlayCommands::teamPlayerList))))
                .then(CommandManager.literal("accept")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .executes(RKPlayCommands::teamAccept)))
                .then(CommandManager.literal("cancel")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .executes(RKPlayCommands::teamCancel)))
                .then(CommandManager.literal("join")
                    .then(CommandManager.argument("teamId", StringArgumentType.word())
                        .executes(RKPlayCommands::teamJoin)))
                .then(CommandManager.literal("start")
                    .executes(RKPlayCommands::teamStart))
                .then(CommandManager.literal("set")
                    .then(CommandManager.literal("wins")
                        .then(CommandManager.argument("winners", IntegerArgumentType.integer(1))
                            .executes(RKPlayCommands::teamSetWins)))))
            .then(CommandManager.literal("mark")
                .then(CommandManager.literal("list")
                    .executes(RKPlayCommands::markList))
                .then(CommandManager.literal("mymark")
                    .executes(RKPlayCommands::markMyMark))));
    }

    // Duel command implementations
    private static int duelChallenge(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        String targetName = StringArgumentType.getString(context, "player");
        ServerPlayerEntity target = context.getSource().getServer().getPlayerManager().getPlayer(targetName);
        
        if (target == null) {
            player.sendMessage(Text.literal("玩家不存在或不在线"));
            return 0;
        }
        
        if (player.getUuid().equals(target.getUuid())) {
            player.sendMessage(Text.literal("不能挑战自己"));
            return 0;
        }
        
        DuelRequest request = new DuelRequest();
        request.challenger = player.getUuid();
        request.target = target.getUuid();
        request.timestamp = System.currentTimeMillis();
        duelRequests.put(target.getUuid(), request);
        
        player.sendMessage(Text.literal("已向 " + targetName + " 发起决斗挑战"));
        target.sendMessage(Text.literal(player.getName().getString() + " 向你发起决斗挑战，输入/rkp duel accept " + player.getName().getString() + " 接受"));
        
        return 1;
    }

    private static int duelAccept(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        String challengerName = StringArgumentType.getString(context, "player");
        ServerPlayerEntity challenger = context.getSource().getServer().getPlayerManager().getPlayer(challengerName);
        
        if (challenger == null) {
            player.sendMessage(Text.literal("玩家不存在或不在线"));
            return 0;
        }
        
        DuelRequest request = duelRequests.get(player.getUuid());
        if (request == null || !request.challenger.equals(challenger.getUuid())) {
            player.sendMessage(Text.literal("没有来自该玩家的决斗请求"));
            return 0;
        }
        
        // 决斗逻辑
        int challengerMark = playerMarks.getOrDefault(challenger.getUuid(), DEFAULT_MARK);
        int targetMark = playerMarks.getOrDefault(player.getUuid(), DEFAULT_MARK);
        int totalMark = challengerMark + targetMark;
        
        // 随机决定胜负 (50%概率)
        boolean challengerWins = new Random().nextBoolean();
        
        if (challengerWins) {
            // 挑战者胜利
            int reward = (int)(totalMark * 0.2);
            int penalty = (int)(totalMark * 0.3);
            
            if (targetMark >= penalty) {
                playerMarks.put(player.getUuid(), targetMark - penalty);
            } else {
                playerMarks.remove(player.getUuid());
                player.kill();
            }
            
            playerMarks.put(challenger.getUuid(), challengerMark + reward);
            
            player.sendMessage(Text.literal("你输给了 " + challengerName + ", 损失了 " + penalty + " 积分"));
            challenger.sendMessage(Text.literal("你战胜了 " + player.getName().getString() + ", 获得了 " + reward + " 积分"));
        } else {
            // 被挑战者胜利
            int reward = (int)(totalMark * 0.2);
            int penalty = (int)(totalMark * 0.3);
            
            if (challengerMark >= penalty) {
                playerMarks.put(challenger.getUuid(), challengerMark - penalty);
            } else {
                playerMarks.remove(challenger.getUuid());
                challenger.kill();
            }
            
            playerMarks.put(player.getUuid(), targetMark + reward);
            
            player.sendMessage(Text.literal("你战胜了 " + challengerName + ", 获得了 " + reward + " 积分"));
            challenger.sendMessage(Text.literal("你输给了 " + player.getName().getString() + ", 损失了 " + penalty + " 积分"));
        }
        
        duelRequests.remove(player.getUuid());

        return 1;
    }

    private static int duelCancel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        
        // 检查是否有发出的挑战
        boolean hasChallenge = duelRequests.values().stream()
            .anyMatch(req -> req.challenger.equals(player.getUuid()));
            
        if (hasChallenge) {
            // 取消所有发出的挑战
            duelRequests.values().removeIf(req -> req.challenger.equals(player.getUuid()));
            player.sendMessage(Text.literal("已取消所有发出的决斗挑战"));
            return 1;
        }
        
        // 检查是否有收到的挑战
        if (duelRequests.containsKey(player.getUuid())) {
            duelRequests.remove(player.getUuid());
            player.sendMessage(Text.literal("已拒绝所有收到的决斗挑战"));
            return 1;
        }
        
        player.sendMessage(Text.literal("没有需要取消的决斗"));
        return 0;
    }

    // Team command implementations
    private static int teamCreate(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        
        // 检查玩家是否已经有队伍
        boolean alreadyInTeam = teams.values().stream()
            .anyMatch(team -> team.members.contains(player.getUuid()));
            
        if (alreadyInTeam) {
            player.sendMessage(Text.literal("你已经在其他队伍中"));
            return 0;
        }
        
        Team team = new Team();
        team.id = UUID.randomUUID();
        team.leader = player.getUuid();
        team.members.add(player.getUuid());
        
        teams.put(team.id, team);
        
        player.sendMessage(Text.literal("队伍创建成功! 队伍ID: " + team.id.toString()));
        return 1;
    }

    private static int teamDelete(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        String teamIdStr = StringArgumentType.getString(context, "teamId");
        
        try {
            UUID teamId = UUID.fromString(teamIdStr);
            Team team = teams.get(teamId);
            
            if (team == null) {
                player.sendMessage(Text.literal("队伍不存在"));
                return 0;
            }
            
            if (!team.leader.equals(player.getUuid())) {
                player.sendMessage(Text.literal("只有队长可以删除队伍"));
                return 0;
            }
            
            // 通知所有成员
            for (UUID memberId : team.members) {
                ServerPlayerEntity member = context.getSource().getServer().getPlayerManager().getPlayer(memberId);
                if (member != null) {
                    member.sendMessage(Text.literal("队伍已被解散"));
                }
            }
            
            teams.remove(teamId);
            player.sendMessage(Text.literal("队伍已删除"));
            return 1;
        } catch (IllegalArgumentException e) {
            player.sendMessage(Text.literal("无效的队伍ID"));
            return 0;
        }
    }

    private static int teamJoinList(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        
        List<Team> joinedTeams = teams.values().stream()
            .filter(team -> team.members.contains(player.getUuid()))
            .toList();
            
        if (joinedTeams.isEmpty()) {
            player.sendMessage(Text.literal("你没有加入任何队伍"));
            return 0;
        }
        
        StringBuilder sb = new StringBuilder("你加入的队伍:\n");
        for (Team team : joinedTeams) {
            ServerPlayerEntity leader = context.getSource().getServer().getPlayerManager().getPlayer(team.leader);
            String leaderName = leader != null ? leader.getName().getString() : "未知";
            sb.append("队伍ID: ").append(team.id).append(", 队长: ").append(leaderName)
              .append(", 成员数: ").append(team.members.size()).append("\n");
        }
        
        player.sendMessage(Text.literal(sb.toString()));
        return 1;
    }

    private static int teamMyTeamList(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        
        List<Team> myTeams = teams.values().stream()
            .filter(team -> team.leader.equals(player.getUuid()))
            .toList();
            
        if (myTeams.isEmpty()) {
            player.sendMessage(Text.literal("你没有创建任何队伍"));
            return 0;
        }
        
        StringBuilder sb = new StringBuilder("你创建的队伍:\n");
        for (Team team : myTeams) {
            sb.append("队伍ID: ").append(team.id).append(", 成员数: ").append(team.members.size()).append("\n");
        }
        
        player.sendMessage(Text.literal(sb.toString()));
        return 1;
    }

    private static int teamPlayerList(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        String teamIdStr = StringArgumentType.getString(context, "teamId");
        
        try {
            UUID teamId = UUID.fromString(teamIdStr);
            Team team = teams.get(teamId);
            
            if (team == null) {
                player.sendMessage(Text.literal("队伍不存在"));
                return 0;
            }
            
            if (!team.members.contains(player.getUuid())) {
                player.sendMessage(Text.literal("你还未参加该队伍"));
                return 0;
            }
            
            StringBuilder sb = new StringBuilder("队伍成员:\n");
            for (UUID memberId : team.members) {
                ServerPlayerEntity member = context.getSource().getServer().getPlayerManager().getPlayer(memberId);
                String memberName = member != null ? member.getName().getString() : "未知";
                String role = memberId.equals(team.leader) ? " (队长)" : "";
                sb.append(memberName).append(role).append("\n");
            }
            
            player.sendMessage(Text.literal(sb.toString()));
            return 1;
        } catch (IllegalArgumentException e) {
            player.sendMessage(Text.literal("无效的队伍ID"));
            return 0;
        }
    }

    private static int teamAccept(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity leader = context.getSource().getPlayer();
        String playerName = StringArgumentType.getString(context, "player");
        ServerPlayerEntity player = context.getSource().getServer().getPlayerManager().getPlayer(playerName);
        
        if (player == null) {
            leader.sendMessage(Text.literal("玩家不存在或不在线"));
            return 0;
        }
        
        // 检查是否有邀请
        Set<UUID> invites = teamInvites.get(leader.getUuid());
        if (invites == null || !invites.contains(player.getUuid())) {
            leader.sendMessage(Text.literal("没有来自该玩家的加入请求"));
            return 0;
        }
        
        // 找到队长的队伍
        Optional<Team> leaderTeamOpt = teams.values().stream()
            .filter(team -> team.leader.equals(leader.getUuid()))
            .findFirst();
            
        if (!leaderTeamOpt.isPresent()) {
            leader.sendMessage(Text.literal("你没有队伍"));
            return 0;
        }
        
        Team team = leaderTeamOpt.get();
        
        // 检查玩家是否已经在其他队伍中
        boolean inOtherTeam = teams.values().stream()
            .anyMatch(t -> t.members.contains(player.getUuid()) && !t.id.equals(team.id));
            
        if (inOtherTeam) {
            leader.sendMessage(Text.literal("该玩家已经在其他队伍中"));
            return 0;
        }
        
        // 添加玩家到队伍
        team.members.add(player.getUuid());
        invites.remove(player.getUuid());
        
        leader.sendMessage(Text.literal("已接受 " + playerName + " 加入队伍"));
        player.sendMessage(Text.literal("你已加入 " + leader.getName().getString() + " 的队伍"));
        
        return 1;
    }

    private static int teamCancel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        // Implementation for team cancel
        return 1;
    }

    private static int teamJoin(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        String teamIdStr = StringArgumentType.getString(context, "teamId");
        
        try {
            UUID teamId = UUID.fromString(teamIdStr);
            Team team = teams.get(teamId);
            
            if (team == null) {
                player.sendMessage(Text.literal("队伍不存在"));
                return 0;
            }
            
            // 检查是否已经在队伍中
            if (team.members.contains(player.getUuid())) {
                player.sendMessage(Text.literal("你已经在队伍中"));
                return 0;
            }
            
            // 检查是否在其他队伍中
            boolean inOtherTeam = teams.values().stream()
                .anyMatch(t -> t.members.contains(player.getUuid()) && !t.id.equals(teamId));
                
            if (inOtherTeam) {
                player.sendMessage(Text.literal("你已经在其他队伍中"));
                return 0;
            }
            
            // 发送加入请求
            teamInvites.computeIfAbsent(team.leader, k -> new HashSet<>()).add(player.getUuid());
            
            ServerPlayerEntity leader = context.getSource().getServer().getPlayerManager().getPlayer(team.leader);
            if (leader != null) {
                leader.sendMessage(Text.literal(player.getName().getString() + " 请求加入你的队伍，输入/rkp team accept " + player.getName().getString() + " 接受"));
            }
            
            player.sendMessage(Text.literal("已发送加入请求"));
            return 1;
        } catch (IllegalArgumentException e) {
            player.sendMessage(Text.literal("无效的队伍ID"));
            return 0;
        }
    }

    private static int teamStart(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        
        // 找到玩家所在的队伍
        Optional<Team> teamOpt = teams.values().stream()
            .filter(team -> team.members.contains(player.getUuid()))
            .findFirst();
            
        if (!teamOpt.isPresent()) {
            player.sendMessage(Text.literal("你不在任何队伍中"));
            return 0;
        }
        
        Team team = teamOpt.get();
        
        // 检查是否是队长
        if (!team.leader.equals(player.getUuid())) {
            player.sendMessage(Text.literal("只有队长可以开始游戏"));
            return 0;
        }
        
        // 检查队伍人数是否足够
        if (team.members.size() < 2) {
            player.sendMessage(Text.literal("队伍人数不足，至少需要2人"));
            return 0;
        }
        
        // 回合制比赛逻辑
        List<UUID> participants = new ArrayList<>(team.members);
        Collections.shuffle(participants);
        
        while (participants.size() > team.requiredWins) {
            // 随机选择失败者
            UUID loser = participants.remove(new Random().nextInt(participants.size()));
            ServerPlayerEntity loserPlayer = context.getSource().getServer().getPlayerManager().getPlayer(loser);
            
            if (loserPlayer != null) {
                loserPlayer.sendMessage(Text.literal("你被淘汰了!"));
                loserPlayer.kill();
            }
            
            // 通知所有成员
            for (UUID memberId : team.members) {
                ServerPlayerEntity member = context.getSource().getServer().getPlayerManager().getPlayer(memberId);
                if (member != null) {
                    member.sendMessage(Text.literal(loserPlayer != null ? loserPlayer.getName().getString() : "某玩家" + " 被淘汰了，剩余 " + participants.size() + " 人"));
                }
            }
        }
        
        // 分配积分给胜利者
        int rewardPerWinner = 100 * team.members.size(); // 基础奖励
        for (UUID winner : participants) {
            playerMarks.merge(winner, rewardPerWinner, Integer::sum);
            ServerPlayerEntity winnerPlayer = context.getSource().getServer().getPlayerManager().getPlayer(winner);
            if (winnerPlayer != null) {
                winnerPlayer.sendMessage(Text.literal("恭喜你获胜! 获得 " + rewardPerWinner + " 积分"));
            }
        }
        
        return 1;
    }

    private static int teamSetWins(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        int winners = IntegerArgumentType.getInteger(context, "winners");
        
        // 找到玩家作为队长的队伍
        Optional<Team> teamOpt = teams.values().stream()
            .filter(team -> team.leader.equals(player.getUuid()))
            .findFirst();
            
        if (!teamOpt.isPresent()) {
            player.sendMessage(Text.literal("你不是任何队伍的队长"));
            return 0;
        }
        
        Team team = teamOpt.get();
        
        // 检查胜利人数是否合法
        if (winners < 1 || winners > team.members.size() / 2) {
            player.sendMessage(Text.literal("胜利人数必须在1到队伍人数的一半之间"));
            return 0;
        }
        
        team.requiredWins = winners;
        player.sendMessage(Text.literal("已设置胜利人数为 " + winners));
        
        // 通知所有成员
        for (UUID memberId : team.members) {
            ServerPlayerEntity member = context.getSource().getServer().getPlayerManager().getPlayer(memberId);
            if (member != null && !member.getUuid().equals(player.getUuid())) {
                member.sendMessage(Text.literal("队长已将胜利人数设置为 " + winners));
            }
        }
        
        return 1;
    }

    // Mark command implementations
    private static int markList(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        // 获取积分前十的玩家
        List<Map.Entry<UUID, Integer>> topPlayers = playerMarks.entrySet().stream()
            .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
            .limit(10)
            .toList();
            
        if (topPlayers.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("暂无积分数据"), false);
            return 0;
        }
        
        StringBuilder sb = new StringBuilder("积分榜:\n");
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : topPlayers) {
            ServerPlayerEntity player = context.getSource().getServer().getPlayerManager().getPlayer(entry.getKey());
            String playerName = player != null ? player.getName().getString() : "未知";
            sb.append(rank++).append(". ").append(playerName).append(": ").append(entry.getValue()).append("\n");
        }
        
        context.getSource().sendFeedback(() -> Text.literal(sb.toString()), false);
        return 1;
    }

    private static int markMyMark(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        int mark = playerMarks.getOrDefault(player.getUuid(), 0);
        
        player.sendMessage(Text.literal("你的积分: " + mark));
        return 1;
    }

    // Helper classes
    private static class DuelRequest {
        UUID challenger;
        UUID target;
        long timestamp;
    }

    private static class Team {
        UUID id;
        UUID leader;
        Set<UUID> members = new HashSet<>();
        int requiredWins = 1;
    }
}