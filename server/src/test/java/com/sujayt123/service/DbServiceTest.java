package com.sujayt123.service;

import com.sujayt123.communication.msg.server.GameListItem;
import com.sujayt123.communication.msg.server.GameStateItem;
import javafx.util.Pair;
import org.junit.Test;
import scrabble.AI;
import scrabble.Trie;
import util.Quadruple;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Created by sujay on 8/15/17.
 */
public class DbServiceTest {
    @Test
    public void tests() throws Exception {
        DbService db = new DbService();

        db.deleteExistingAccount("hello");
        db.deleteExistingAccount("hello2");

        /* Set up dummy accounts */
        Optional<Integer> helloCreationSuccess, hello2CreationSuccess, helloLoginSuccess, hello2LoginSuccess;

        assertTrue((helloCreationSuccess = db.createNewAccount("hello", "world")).isPresent());
        assertTrue((hello2CreationSuccess = db.createNewAccount("hello2", "world2")).isPresent());
        assertFalse(db.createNewAccount("CPU", "password").isPresent());
        assertFalse(db.createNewAccount("hello", "password").isPresent());
        assertFalse(db.createNewAccount("hello2", "password").isPresent());


        /* Log in to dummy accounts */
        assertTrue((helloLoginSuccess = db.login("hello", "world")).isPresent());
        assertTrue((hello2LoginSuccess = db.login("hello2", "world2")).isPresent());
        assertFalse(db.login("hello", "world2").isPresent());
        assertFalse(db.login("hello2", "world").isPresent());

        /* Logging in should return the same user ids */
        assertEquals(helloCreationSuccess.get(), helloLoginSuccess.get());
        assertEquals(hello2CreationSuccess.get(), hello2LoginSuccess.get());

        /* Create games involving dummy players */
        assertFalse(db.createNewGame(helloLoginSuccess.get(), "notaRealPlayer"));
        assertTrue(db.createNewGame(helloLoginSuccess.get(), "CPU"));
        assertTrue(db.createNewGame(hello2LoginSuccess.get(), "CPU"));
        assertTrue(db.createNewGame(hello2LoginSuccess.get(), "hello"));

        Optional<GameListItem[]> gamesForHello = db.getGamesForPlayer(helloLoginSuccess.get());
        Optional<GameListItem[]> gamesForHello2 = db.getGamesForPlayer(hello2LoginSuccess.get());
        assertTrue(gamesForHello.isPresent());
        assertTrue(gamesForHello2.isPresent());

        /* Assert that every game for the player "Hello" is against one of hello2 and cpu */
        Arrays.asList(gamesForHello.get()).forEach(x -> assertTrue(Arrays.asList("hello2", "CPU").contains(x.getOpponentName())));
        assertEquals(gamesForHello.get().length, 2);

        /* Assert that every game for the player "Hello2" is against one of hello and cpu */
        Arrays.asList(gamesForHello2.get()).forEach(x -> assertTrue(Arrays.asList("hello", "CPU").contains(x.getOpponentName())));
        assertEquals(gamesForHello2.get().length, 2);

        /* Test that hello's games were created and retrieved correctly */
        for (int k = 0; k < 2; k++)
        {
            Optional<GameStateItem> gameStateItemHello = db.getGameById(helloLoginSuccess.get(), gamesForHello.get()[k].getGame_id());
            assertTrue(gameStateItemHello.isPresent());
            for (int i = 0; i < 15; i++)
            {
                for (int j = 0; j < 15; j++)
                {
                    assertEquals(gameStateItemHello.get().getOldBoard()[i][j], ' ');
                    assertEquals(gameStateItemHello.get().getBoard()[i][j], ' ');
                }
            }

        /* The logic of the createGame function ensures that the creating player goes first. So
         * if we're checking the game against the CPU, we go first. If we're checking the game against
          * hello2, hello2 goes first because he created the game. */

            if (gameStateItemHello.get().getOpponentName().equals("CPU"))
            {
                assertTrue(gameStateItemHello.get().isClientTurn());
            }
            else
            {
                assertFalse(gameStateItemHello.get().isClientTurn());
            }
        }

        /* Now attempt making an update to one of the game's state */

        // No change in the board state should result in an "invalid" update
        List<List<Character>> board1, board2;
        board1 = Arrays.asList(Arrays.asList(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '),
                Arrays.asList(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '),
                Arrays.asList(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '),
                Arrays.asList(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '),
                Arrays.asList(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '),
                Arrays.asList(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '),
                Arrays.asList(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '),
                Arrays.asList(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '),
                Arrays.asList(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '),
                Arrays.asList(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '),
                Arrays.asList(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '),
                Arrays.asList(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '),
                Arrays.asList(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '),
                Arrays.asList(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '),
                Arrays.asList(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '));

        /* Fake (irrelevant) queue generated for the sake of running AI.CPUMove procedure. */

        String playerHand = db.getGameById(helloLoginSuccess.get(), gamesForHello.get()[0].getGame_id()).get().getClientHand();
        List<Character> playerHandAsList = playerHand.chars().mapToObj(x -> (char)x).collect(Collectors.toList());
        Queue<Character> irrelevantQueue = new ConcurrentLinkedDeque<>(Arrays.asList('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'));
        // Updating with a valid word should be accepted. The AI will choose a valid word for this test.
        Quadruple<List<List<Character>>, List<Character>, Queue<Character>, Pair<String, Integer>> move =
            AI.CPUMove(new Quadruple<List<List<Character>>, List<Character>, Queue<Character>, Trie>
                    (board1,
                    playerHandAsList,
                    irrelevantQueue,
                    new Trie()));

        board2 = move.getA();

        Optional<Map<Integer, GameStateItem>> updateGameRetVal = db.updateGameState(helloLoginSuccess.get(), gamesForHello.get()[0].getGame_id(), board1, board2);
        assertTrue(updateGameRetVal.isPresent());

        /* Check whether the game was updated in the database and whether the result of db.updateGameState is consistent with the db */
        Optional<GameStateItem> updatedGame = db.getGameById(helloLoginSuccess.get(), gamesForHello.get()[0].getGame_id());

        assertTrue(updatedGame.isPresent());
        assertEquals(updateGameRetVal.get().get(helloLoginSuccess.get()), updatedGame.get());

        assertFalse(updatedGame.get().isClientTurn());
        assertNotEquals(gamesForHello.get()[0].getClientScore(), updatedGame.get().getClientScore());
        /* The value of the old board should be ' ', the value of the new board should be 'A' */
        assertNotEquals(updatedGame.get().getOldBoard()[7][7], updatedGame.get().getBoard()[7][7]);

        /* Remove dummy accounts */
        db.deleteExistingAccount(helloLoginSuccess.get());
        db.deleteExistingAccount(hello2LoginSuccess.get());

    }
}