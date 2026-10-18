package com.letmesee;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerNameResolverTest {

    private static final List<String> ONLINE = List.of("Steve", "Alex", "stewie");

    @Test
    void uniquePrefixMatchesIgnoringCase() {
        PlayerNameResolver.Resolution result =
            PlayerNameResolver.resolvePrefix(ONLINE, "stev");
        assertTrue(result.matched());
        assertEquals("Steve", result.name());
        assertEquals(List.of("Steve"), result.candidates());
    }

    @Test
    void fullNameMatches() {
        PlayerNameResolver.Resolution result =
            PlayerNameResolver.resolvePrefix(ONLINE, "Alex");
        assertTrue(result.matched());
        assertEquals("Alex", result.name());
    }

    @Test
    void inputIsTrimmedBeforeMatching() {
        PlayerNameResolver.Resolution result =
            PlayerNameResolver.resolvePrefix(ONLINE, "  Alex  ");
        assertTrue(result.matched());
        assertEquals("Alex", result.name());
    }

    @Test
    void ambiguousPrefixIsRejectedWithAllCandidates() {
        PlayerNameResolver.Resolution result =
            PlayerNameResolver.resolvePrefix(ONLINE, "st");
        assertEquals(PlayerNameResolver.Status.AMBIGUOUS, result.status());
        assertNull(result.name());
        assertEquals(List.of("Steve", "stewie"), result.candidates());
    }

    @Test
    void unknownNameHasNoMatch() {
        PlayerNameResolver.Resolution result =
            PlayerNameResolver.resolvePrefix(ONLINE, "Notch");
        assertEquals(PlayerNameResolver.Status.NO_MATCH, result.status());
        assertNull(result.name());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void emptyOrBlankInputNeverMatchesEveryone() {
        for (String input : new String[]{"", "   ", null}) {
            PlayerNameResolver.Resolution result =
                PlayerNameResolver.resolvePrefix(ONLINE, input);
            assertEquals(PlayerNameResolver.Status.NO_MATCH, result.status(), "输入: " + input);
            assertFalse(result.matched());
        }
    }

    @Test
    void emptyCandidateListHasNoMatch() {
        assertEquals(PlayerNameResolver.Status.NO_MATCH,
            PlayerNameResolver.resolvePrefix(List.of(), "Steve").status());
    }

    @Test
    void singleOnlinePlayerIsMatchedByAnyPrefix() {
        PlayerNameResolver.Resolution result =
            PlayerNameResolver.resolvePrefix(List.of("Steve"), "s");
        assertTrue(result.matched());
        assertEquals("Steve", result.name());
    }

    @Test
    void exactNameWinsOverPrefixAmbiguity() {
        // "ste" 前缀同时匹配 Steve 与 stewie，但完整输入 stewie 时调用方会先走精确匹配；
        // 解析器本身只负责前缀判定，因此这里验证前缀语义保持一致
        assertEquals(PlayerNameResolver.Status.AMBIGUOUS,
            PlayerNameResolver.resolvePrefix(ONLINE, "ste").status());
        assertTrue(PlayerNameResolver.resolvePrefix(ONLINE, "stewie").matched());
    }
}
