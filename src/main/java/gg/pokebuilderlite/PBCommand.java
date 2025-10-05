package gg.pokebuilderlite;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.stream.Collectors;

// Pixelmon API (1.16.5-9.1.x)
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.PartyStorage;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.stats.Moveset;
import com.pixelmonmod.pixelmon.api.pokemon.stats.Attack;
import com.pixelmonmod.pixelmon.api.pokemon.species.PokemonForm;
import com.pixelmonmod.pixelmon.api.pokemon.species.moves.Moves;
import com.pixelmonmod.pixelmon.battles.attacks.ImmutableAttack;

public class PBCommand implements CommandExecutor, TabCompleter {
    private final PokebuilderLite plugin;

    public PBCommand(PokebuilderLite plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("§a/pb setmove <partySlot 1-6> <moveSlot 1-4> <move name...>");
            if (sender.hasPermission("pokebuilder.admin")) {
                sender.sendMessage("§a/pb setmove <player> <partySlot> <moveSlot> <move name...>");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("setmove")) {
            if (args.length >= 4 && sender instanceof Player) {
                // self
                Player target = (Player) sender;
                int partySlot = parseInt(args[1], -1);
                int moveSlot = parseInt(args[2], -1);
                String moveName = join(args, 3);

                return doSetMove(sender, target, partySlot, moveSlot, moveName);
            } else if (args.length >= 5 && sender.hasPermission("pokebuilder.admin")) {
                // other player
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[1]);
                    return true;
                }
                int partySlot = parseInt(args[2], -1);
                int moveSlot = parseInt(args[3], -1);
                String moveName = join(args, 4);

                return doSetMove(sender, target, partySlot, moveSlot, moveName);
            } else {
                sender.sendMessage("§c사용법: /pb setmove <partySlot 1-6> <moveSlot 1-4> <move name...>");
                if (sender.hasPermission("pokebuilder.admin")) {
                    sender.sendMessage("§c또는: /pb setmove <player> <partySlot> <moveSlot> <move name...>");
                }
                return true;
            }
        }

        sender.sendMessage("§c알 수 없는 하위 명령어입니다. /pb help");
        return true;
    }

    private boolean doSetMove(CommandSender sender, Player target, int partySlot, int moveSlot, String moveName) {
        if (partySlot < 1 || partySlot > 6) {
            sender.sendMessage("§cpartySlot은 1~6");
            return true;
        }
        if (moveSlot < 1 || moveSlot > 4) {
            sender.sendMessage("§cmoveSlot은 1~4");
            return true;
        }
        if (moveName == null || moveName.trim().isEmpty()) {
            sender.sendMessage("§c기술 이름을 입력하세요.");
            return true;
        }

        try {
            PlayerPartyStorage party = StorageProxy.getParty(target.getUniqueId());
            if (party == null) {
                sender.sendMessage("§c해당 플레이어의 파티를 불러올 수 없습니다.");
                return true;
            }
            Pokemon pokemon = party.get(partySlot - 1);
            if (pokemon == null) {
                sender.sendMessage("§c해당 파티 슬롯에 포켓몬이 없습니다.");
                return true;
            }

            // Build allowed moves list from Pokemon's learnset
            PokemonForm form = pokemon.getForm();
            Moves moves = form.getMoves();
            Set<ImmutableAttack> allowed = new HashSet<>();
            allowed.addAll(moves.getAllLevelUpMoves());
            allowed.addAll(moves.getTMMoves());
            allowed.addAll(moves.getTutorMoves());
            allowed.addAll(moves.getTransferMoves());
            // Some versions expose HM/TR by dedicated getters; guard with try/catch
            try { allowed.addAll(moves.getHMMoves()); } catch (Throwable ignored) {}
            try { allowed.addAll(moves.getTRMoves()); } catch (Throwable ignored) {}

            ImmutableAttack chosen = matchMove(allowed, moveName);
            if (chosen == null) {
                sender.sendMessage("§c해당 포켓몬이 배울 수 있는 기술 목록에서 찾을 수 없습니다: §e" + moveName);
                sender.sendMessage("§7(정확한 영어 기술명, 예: §fFlamethrower§7)");
                return true;
            }

            // Create mutable attack and set into the specified slot
            Attack newAttack = chosen.ofMutable();
            Moveset ms = pokemon.getMoveset();
            ms.set(moveSlot - 1, newAttack);
            ms.healAllPP(); // reset PP to full to avoid 0PP upon replace

            // force storage update
            party.set(partySlot - 1, pokemon);

            sender.sendMessage("§a[" + target.getName() + "] " + partySlot + "번 포켓몬의 " + moveSlot + "번 슬롯을 §e" + chosen.getAttackName() + "§a(으)로 설정했습니다.");
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            sender.sendMessage("§c실패: 콘솔 로그를 확인하세요. (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")");
            return true;
        }
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private static String join(String[] arr, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < arr.length; i++) {
            if (i > from) sb.append(" ");
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ENGLISH).replace(" ", "").replace("-", "").replace("_", "");
    }

    private static ImmutableAttack matchMove(Set<ImmutableAttack> pool, String userInput) {
        String want = normalize(userInput);
        // exact normalized match on attack name
        for (ImmutableAttack a : pool) {
            String n = normalize(a.getAttackName());
            if (n.equals(want)) return a;
        }
        // startsWith fallback
        for (ImmutableAttack a : pool) {
            String n = normalize(a.getAttackName());
            if (n.startsWith(want)) return a;
        }
        return null;
    }

    // Tab completion
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], Arrays.asList("setmove", "help"), new ArrayList<>());
        }
        if (args.length == 2) {
            // either party slot or player name (admin)
            if (sender.hasPermission("pokebuilder.admin")) {
                List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                names.add("1"); names.add("2"); names.add("3"); names.add("4"); names.add("5"); names.add("6");
                return StringUtil.copyPartialMatches(args[1], names, new ArrayList<>());
            } else {
                return StringUtil.copyPartialMatches(args[1], Arrays.asList("1","2","3","4","5","6"), new ArrayList<>());
            }
        }
        if (args.length == 3) {
            if (sender.hasPermission("pokebuilder.admin") && Bukkit.getPlayerExact(args[1]) != null) {
                return StringUtil.copyPartialMatches(args[2], Arrays.asList("1","2","3","4","5","6"), new ArrayList<>());
            }
            return StringUtil.copyPartialMatches(args[2], Arrays.asList("1","2","3","4"), new ArrayList<>());
        }
        if (args.length == 4) {
            if (sender.hasPermission("pokebuilder.admin") && Bukkit.getPlayerExact(args[1]) != null) {
                return StringUtil.copyPartialMatches(args[3], Arrays.asList("1","2","3","4"), new ArrayList<>());
            }
        }
        if (args.length >= 4) {
            // Suggest moves from player's current pokemon learnset if possible
            Player contextPlayer = null;
            int partySlotIdx = -1;
            if (sender.hasPermission("pokebuilder.admin") && Bukkit.getPlayerExact(args[1]) != null) {
                contextPlayer = Bukkit.getPlayerExact(args[1]);
                partySlotIdx = parseInt(args[2], -1) - 1;
            } else if (sender instanceof Player) {
                contextPlayer = (Player) sender;
                partySlotIdx = parseInt(args[1], -1) - 1;
            }
            if (contextPlayer != null && partySlotIdx >= 0 && partySlotIdx < 6) {
                try {
                    PlayerPartyStorage party = StorageProxy.getParty(contextPlayer.getUniqueId());
                    if (party != null) {
                        Pokemon pokemon = party.get(partySlotIdx);
                        if (pokemon != null) {
                            PokemonForm form = pokemon.getForm();
                            Moves moves = form.getMoves();
                            Set<String> names = new HashSet<>();
                            for (ImmutableAttack atk : moves.getAllLevelUpMoves()) names.add(atk.getAttackName());
                            try { for (ImmutableAttack atk : moves.getTMMoves()) names.add(atk.getAttackName()); } catch (Throwable ignored) {}
                            try { for (ImmutableAttack atk : moves.getTutorMoves()) names.add(atk.getAttackName()); } catch (Throwable ignored) {}
                            try { for (ImmutableAttack atk : moves.getTransferMoves()) names.add(atk.getAttackName()); } catch (Throwable ignored) {}
                            try { for (ImmutableAttack atk : moves.getHMMoves()) names.add(atk.getAttackName()); } catch (Throwable ignored) {}
                            try { for (ImmutableAttack atk : moves.getTRMoves()) names.add(atk.getAttackName()); } catch (Throwable ignored) {}
                            List<String> sorted = names.stream().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
                            String prefix;
                            if (sender.hasPermission("pokebuilder.admin") && Bukkit.getPlayerExact(args[1]) != null) {
                                prefix = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                            } else {
                                prefix = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                            }
                            return StringUtil.copyPartialMatches(prefix, sorted, new ArrayList<>());
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return result;
    }
}
