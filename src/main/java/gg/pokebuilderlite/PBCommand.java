
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

    private String setExactMove(UUID uuid, int partySlot, int moveSlot, String moveName) {
        try {
            if (partySlot < 1 || partySlot > 6) return "파티칸은 1~6";
            if (moveSlot  < 1 || moveSlot  > 4) return "기술칸은 1~4";

            // 1) resolve Moves / ImmutableAttack for the given move name
            Class<?> movesEnum = Class.forName("com.pixelmonmod.pixelmon.battles.attacks.moves.Moves");
            Class<?> immClass  = Class.forName("com.pixelmonmod.pixelmon.battles.attacks.ImmutableAttack");

            Object immAtk = null;
            String key = moveName.trim().toUpperCase().replace(' ', '_').replace('-', '_');
            try {
                @SuppressWarnings("unchecked")
                Object enumConst = Enum.valueOf((Class) movesEnum, key);
                immAtk = immClass.getMethod("fromMove", movesEnum).invoke(null, enumConst);
            } catch (Throwable ignore) {
                try {
                    Class<?> trEnum = Class.forName("com.pixelmonmod.pixelmon.enums.technicalmoves.Gen8TechnicalRecords");
                    @SuppressWarnings("unchecked")
                    Object trConst = Enum.valueOf((Class) trEnum, key);
                    immAtk = immClass.getMethod("from", trEnum).invoke(null, trConst);
                } catch (Throwable ignore2) { /* still null */ }
            }
            if (immAtk == null) return "기술을 찾을 수 없음: " + moveName;

            // 2) get PlayerPartyStorage by UUID
            Class<?> storageProxy = Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
            Class<?> playerPartyStorage = Class.forName("com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage");
            Method getPartyByUUID = null;
            try {
                getPartyByUUID = storageProxy.getMethod("getParty", UUID.class);
            } catch (NoSuchMethodException nse) {
                // fallback older signature might be (ServerPlayerEntity) - not used here
            }
            if (getPartyByUUID == null) return "StorageProxy.getParty(UUID) 없음";
            Object party = getPartyByUUID.invoke(null, uuid);
            if (party == null) return "파티 정보를 가져올 수 없음";

            // 3) Get Pokemon in partySlot
            Method get = playerPartyStorage.getMethod("get", int.class);
            Object poke = get.invoke(party, partySlot - 1);
            if (poke == null) return "해당 칸 포켓몬이 없음";

            // 4) Get moveset and set specific slot (index moveSlot-1) to immAtk
            Class<?> pokemonClass = Class.forName("com.pixelmonmod.pixelmon.api.pokemon.Pokemon");
            Method getMoveset = pokemonClass.getMethod("getMoveset");
            Object moveset = getMoveset.invoke(poke);

            Method setMethod = null;
            try {
                setMethod = moveset.getClass().getMethod("set", int.class, immClass);
            } catch (NoSuchMethodException e) {
                // some versions accept Object
                setMethod = moveset.getClass().getMethod("set", int.class, Object.class);
            }
            setMethod.invoke(moveset, moveSlot - 1, immAtk);

            // 5) ensure client sync (call party.markDirty / sendParty to client if available)
            try {
                Method markDirty = party.getClass().getMethod("markDirty");
                markDirty.invoke(party);
            } catch (NoSuchMethodException ignored) {}
            try {
                Method send = party.getClass().getMethod("sendEntireParty", UUID.class);
                send.invoke(party, uuid);
            } catch (NoSuchMethodException ignored) {}

            return null; // success
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
