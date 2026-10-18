package com.letmesee;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * 在线玩家名的前缀解析：把「候选名字列表 + 输入」收敛成纯函数，便于单测。
 *
 * <p>玩家库存查看最容易出错的地方是「看错人」——输入的前缀同时匹配多个在线玩家时，
 * 必须拒绝打开而不是随便挑一个。这段判定以前写在 {@code LMSCommand} 里，
 * 依赖 Bukkit 在线玩家列表，无法单测；抽出来后可以覆盖歧义、空输入等边界。</p>
 *
 * <p>只依赖 JDK，任意线程可调用。</p>
 */
public final class PlayerNameResolver {

    /** 解析结果状态。 */
    public enum Status {
        /** 唯一命中。 */
        MATCHED,
        /** 没有命中（含空输入）。 */
        NO_MATCH,
        /** 多个候选，必须让调用者补全全名。 */
        AMBIGUOUS
    }

    /**
     * 一次解析结果。
     *
     * @param status 结果状态
     * @param name 仅 {@link Status#MATCHED} 时非空，命中的玩家名
     * @param candidates 命中的全部玩家名（歧义时用于提示，按传入顺序）
     */
    public record Resolution(Status status, String name, List<String> candidates) {

        /** 是否唯一命中。 */
        public boolean matched() {
            return status == Status.MATCHED;
        }
    }

    private PlayerNameResolver() {
    }

    /**
     * 忽略大小写的前缀匹配。
     *
     * <p>输入会先去掉首尾空白；空输入直接视为无匹配，避免「空前缀匹配所有人」
     * 退化成随便打开某个玩家的库存。忽略大小写是因为玩家更可能手打小写名字。</p>
     *
     * @param onlineNames 候选玩家名（通常是在线玩家）
     * @param input 玩家输入的名字或前缀
     */
    public static Resolution resolvePrefix(Collection<String> onlineNames, String input) {
        String needle = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return new Resolution(Status.NO_MATCH, null, List.of());
        }

        List<String> matches = new ArrayList<>();
        for (String name : onlineNames) {
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(needle)) {
                matches.add(name);
            }
        }

        if (matches.isEmpty()) {
            return new Resolution(Status.NO_MATCH, null, List.of());
        }
        if (matches.size() > 1) {
            return new Resolution(Status.AMBIGUOUS, null, List.copyOf(matches));
        }
        return new Resolution(Status.MATCHED, matches.get(0), List.copyOf(matches));
    }
}
