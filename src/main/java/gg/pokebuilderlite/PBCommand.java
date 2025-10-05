
package gg.pokebuilderlite;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

public class PBCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("§6/pr setmove <파티칸1~6> <기술칸1~4> <기술명> §7- 해당 칸을 정확히 교체");
            return true;
        }
        if (!args[0].equalsIgnoreCase("setmove")) return false;

        try {
            if (sender instanceof Player && args.length >= 4) {
                Player p = (Player) sender;
                int partySlot = parseInt(args[1], -1);
                int moveSlot  = parseInt(args[2], -1);
                String moveName = join(args, 3);
                String err = setExactMove(p.getUniqueId(), partySlot, moveSlot, moveName);
                if (err == null) {
                    sender.sendMessage("§a완료: §f" + moveSlot + "번 칸에 §e" + moveName + "§f 설정됨.");
                } else {
                    sender.sendMessage("§c실패: " + err);
                }
                return true;
            }

            if (args.length >= 5) { // console or admin: /pr setmove <닉> <파티칸> <기술칸> <기술명>
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage("§c플레이어 오프라인: " + args[1]); return true; }
                int partySlot = parseInt(args[2], -1);
                int moveSlot  = parseInt(args[3], -1);
                String moveName = join(args, 4);
                String err = setExactMove(target.getUniqueId(), partySlot, moveSlot, moveName);
                sender.sendMessage(err == null ? "§a완료" : "§c실패: " + err);
                return true;
            }
        } catch (Throwable t) {
            sender.sendMessage("§c오류: " + t.getClass().getSimpleName() + " - " + t.getMessage());
            t.printStackTrace();
            return true;
        }
        return false;
    }

    private String setExactMove(java.util.UUID uuid, int partySlot, int moveSlot, String moveName) {
    try {
        if (partySlot < 1 || partySlot > 6) return "파티칸은 1~6";
        if (moveSlot  < 1 || moveSlot  > 4) return "기술칸은 1~4";
        if (moveName == null || moveName.trim().isEmpty()) return "기술명이 비었음";

        // 1) Attack 클래스 획득
        Class<?> attackCls = Class.forName("com.pixelmonmod.pixelmon.battles.attacks.Attack");

        // (선택) 유효성 확인
        try {
            java.lang.reflect.Method hasAttack = attackCls.getMethod("hasAttack", String.class);
            boolean ok = (boolean) hasAttack.invoke(null, moveName);
            if (!ok) return "알 수 없는 기술: " + moveName;
        } catch (NoSuchMethodException ignore) {
            // 일부 버전에선 없음 → 넘어감
        }

        // 2) 새 Attack 생성 (Attack(String) 생성자 우선)
        Object newAttack = null;
        try {
            java.lang.reflect.Constructor<?> c = attackCls.getConstructor(String.class);
            newAttack = c.newInstance(moveName);
        } catch (NoSuchMethodException e) {
            // 다른 팩토리 존재 시 시도
            try {
                java.lang.reflect.Method factory = attackCls.getMethod("getAttack", String.class);
                newAttack = factory.invoke(null, moveName);
            } catch (NoSuchMethodException e2) {
                return "기술 객체 생성 불가: " + moveName;
            }
        }

        // 3) 파티 가져오기 (StorageProxy.getParty(UUID))
        Class<?> storageProxy = Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
        java.lang.reflect.Method getPartyByUUID = storageProxy.getMethod("getParty", java.util.UUID.class);
        Object party = getPartyByUUID.invoke(null, uuid);
        if (party == null) return "파티 정보를 가져올 수 없음";

        // 4) 대상 포켓몬
        java.lang.reflect.Method get = party.getClass().getMethod("get", int.class);
        Object poke = get.invoke(party, partySlot - 1);
        if (poke == null) return "해당 칸 포켓몬이 없음";

        // 5) Moveset 꺼내서 정확 슬롯 교체
        java.lang.reflect.Method getMoveset = poke.getClass().getMethod("getMoveset");
        Object moveset = getMoveset.invoke(poke);

        java.lang.reflect.Method setMethod = null;
        try {
            setMethod = moveset.getClass().getMethod("set", int.class, attackCls);
        } catch (NoSuchMethodException e) {
            // 타입 소거 대응
            setMethod = moveset.getClass().getMethod("set", int.class, Object.class);
        }
        setMethod.invoke(moveset, moveSlot - 1, newAttack);

        // 6) 동기화 (가능한 경우)
        try { party.getClass().getMethod("markDirty").invoke(party); } catch (NoSuchMethodException ignored) {}
        try { party.getClass().getMethod("sendEntireParty", java.util.UUID.class).invoke(party, uuid); } catch (NoSuchMethodException ignored) {}
        try { moveset.getClass().getMethod("tryNotifyPokemon").invoke(moveset); } catch (NoSuchMethodException ignored) {}

        return null;
    } catch (Throwable t) {
        t.printStackTrace();
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}

    private static int parseInt(String s, int d) {
        try { return Integer.parseInt(s); } catch (Exception e) { return d; }
    }

    private static String join(String[] arr, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < arr.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    // Tab complete
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 2) return StringUtil.copyPartialMatches(args[1], Arrays.asList("1","2","3","4","5","6"), out);
        if (args.length == 3) return StringUtil.copyPartialMatches(args[2], Arrays.asList("1","2","3","4"), out);
        return out;
    }
}
