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

    private final PokebuilderLite plugin;

    public PBCommand(PokebuilderLite plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("§a/pr setmove <partySlot 1-6> <moveSlot 1-4> <move name...>");
            if (sender.hasPermission("pokebuilder.admin")) {
                sender.sendMessage("§a/pr setmove <player> <partySlot> <moveSlot> <move name...>");
            }
            sender.sendMessage("§7지정 칸(1~4)에 정확히 교체합니다. (Pixelmon 9.1.13)");
            return true;
        }

        if (!args[0].equalsIgnoreCase("setmove")) return false;

        try {
            // self
            if (sender instanceof Player && args.length >= 4) {
                Player p = (Player) sender;
                int partySlot = parseInt(args[1], -1);
                int moveSlot  = parseInt(args[2], -1);
                String moveName = join(args, 3);
                String err = setExactMove(p.getUniqueId(), partySlot, moveSlot, moveName);
                sender.sendMessage(err == null ? ok(p.getName(), partySlot, moveSlot, moveName) : ("§c" + err));
                return true;
            }
            // admin -> other
            if (sender.hasPermission("pokebuilder.admin") && args.length >= 5) {
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage("§c플레이어 오프라인/미접속"); return true; }
                int partySlot = parseInt(args[2], -1);
                int moveSlot  = parseInt(args[3], -1);
                String moveName = join(args, 4);
                String err = setExactMove(target.getUniqueId(), partySlot, moveSlot, moveName);
                sender.sendMessage(err == null ? ok(target.getName(), partySlot, moveSlot, moveName) : ("§c" + err));
                return true;
            }

            sender.sendMessage("§c사용법: /pr setmove <party 1-6> <slot 1-4> <move>");
            return true;

        } catch (Throwable t) {
            sender.sendMessage("§c오류: " + t.getClass().getSimpleName() + " - " + t.getMessage());
            return true;
        }
    }

    private String ok(String who, int pSlot, int mSlot, String move) {
        return "§a" + who + " §f의 파티§e" + pSlot + " §f번 포켓몬, 스킬칸 §e" + mSlot + " §f→ §b" + move + " §7(정확 교체 완료)";
    }

    private int parseInt(String s, int def) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }
    private String join(String[] a, int from) { return String.join(" ", Arrays.copyOfRange(a, from, a.length)); }

    /**
     * Pixelmon API를 리플렉션으로 호출해서 정확히 N칸 교체
     * partySlot: 1~6, moveSlot: 1~4
     */
    private String setExactMove(UUID uuid, int partySlot, int moveSlot, String moveName) throws Exception {
        if (partySlot < 1 || partySlot > 6) return "partySlot은 1~6";
        if (moveSlot  < 1 || moveSlot  > 4) return "moveSlot은 1~4";
        moveName = moveName.trim();

        // 1) StorageProxy.getParty(UUID) → PlayerPartyStorage
        Class<?> storageProxy = Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
        Method getPartyByUUID = storageProxy.getMethod("getParty", UUID.class);
        Object party = getPartyByUUID.invoke(null, uuid);
        if (party == null) return "파티를 찾지 못했어요.";

        // 2) PlayerPartyStorage.get(int) → Pokemon
        Method partyGet = party.getClass().getMethod("get", int.class);
        Object pokemon = partyGet.invoke(party, partySlot - 1);
        if (pokemon == null) return "해당 파티칸에 포켓몬이 없음.";

        // 3) pokemon.getMoveset() → Moveset
        Method getMoveset = pokemon.getClass().getMethod("getMoveset");
        Object moveset = getMoveset.invoke(pokemon);
        if (moveset == null) return "무브셋을 불러오지 못함.";

        // 4) 공격 기술 유효성 검사 (Pixelmon 9.x) - Moves/ImmutableAttack 기반으로 대체
Object immAtk = null;
try {
    // com.pixelmonmod.pixelmon.battles.attacks.moves.Moves -> enum 상수 생성
    Class<?> movesEnum = Class.forName("com.pixelmonmod.pixelmon.battles.attacks.moves.Moves");
    // 이름 정규화: 공백/하이픈 -> 언더스코어, 대문자
    String key = moveName.trim().toUpperCase().replace(' ', '_').replace('-', '_');
    Object enumConst = java.lang.Enum.valueOf((Class) movesEnum, key);
    Class<?> immClass = Class.forName("com.pixelmonmod.pixelmon.battles.attacks.ImmutableAttack");
    immAtk = immClass.getMethod("fromMove", movesEnum).invoke(null, enumConst);
} catch (Throwable __ignore) { }
if (immAtk == null) {
    try {
        // TR 이름도 지원 (일부 기술은 TR로만 매칭 가능)
        Class<?> trEnum = Class.forName("com.pixelmonmod.pixelmon.enums.technicalmoves.Gen8TechnicalRecords");
        String key = moveName.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        Object trConst = java.lang.Enum.valueOf((Class) trEnum, key);
        Class<?> immClass2 = Class.forName("com.pixelmonmod.pixelmon.battles.attacks.ImmutableAttack");
        immAtk = immClass2.getMethod("from", trEnum).invoke(null, trConst);
    } catch (Throwable __ignore) { }
}
boolean exists = (immAtk != null);
if (!exists) return "해당 기술이 존재하지 않음: " + moveName;

        Object newAttack = immAtk; // ImmutableAttack instance already prepared
// 5) 기존 칸에 set(index, Attack)
        Method set = moveset.getClass().getMethod("set", int.class, immClass != null ? immClass : Class.forName("com.pixelmonmod.pixelmon.battles.attacks.ImmutableAttack"));
        set.invoke(moveset, moveSlot - 1, newAttack);

        // 6) tryNotifyPokemon()로 갱신 통지 + PP 회복(선택)
        Method tryNotify = moveset.getClass().getMethod("tryNotifyPokemon");
        tryNotify.invoke(moveset);
        try {
            Method heal = moveset.getClass().getMethod("healAllPP");
            heal.invoke(moveset);
        } catch (NoSuchMethodException ignored) {}

        return null; // OK
    }

    // 탭완성: 파티칸/슬롯 숫자 보조
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 2) return StringUtil.copyPartialMatches(args[1], Arrays.asList("1","2","3","4","5","6"), out);
        if (args.length == 3) return StringUtil.copyPartialMatches(args[2], Arrays.asList("1","2","3","4"), out);
        return out;
    }
}
