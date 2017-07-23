package util;

import java.util.*;

/**
 * Created by sujay on 7/22/17.
 */
public class TileHelper {

    /**
     * Creates a queue of randomly shuffled tiles that represent the tile bag for a game.
     * @return A shuffled queue of scrabble tiles
     */
    public static Queue<Character> getTileBagForGame()
    {
        List<Character> tileList =  Arrays.asList( 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'E',
                'E', 'E', 'A', 'A', 'A', 'A', 'A', 'A', 'A',
                'A', 'A', 'I', 'I', 'I', 'I', 'I', 'I', 'I',
                'I', 'I', 'O', 'O', 'O', 'O', 'O', 'O', 'O',
                'O', 'N', 'N', 'N', 'N', 'N', 'N', 'R', 'R',
                'R', 'R', 'R', 'R', 'T', 'T', 'T', 'T', 'T',
                'T', 'L', 'L', 'L', 'L', 'S', 'S', 'S', 'S',
                'U', 'U', 'U', 'U', 'D', 'D', 'D', 'D', 'G',
                'G', 'G', 'B', 'B', 'C', 'C', 'M', 'M', 'P',
                'P', 'F', 'F', 'H', 'H', 'V', 'V', 'W', 'W',
                'Y', 'Y', 'K', 'X', 'J', 'Q', 'Z');
        // Shuffle the tiles and arrange them into a queue.
        Collections.shuffle(tileList);
        return new ArrayDeque<>(tileList);
    }

    /**
     * Gets the scrabble value of character c.
     * @param c character to score
     * @return the score of c if played on a non-premium square on the traditional Scrabble board
     */
    public static int scoreCharacter(char c) {
        // A simple switch statement will suffice, since there are only 27 possible values to consider.
        switch (c) {
            case 'A':
                return 1;
            case 'B':
                return 3;
            case 'C':
                return 3;
            case 'D':
                return 2;
            case 'E':
                return 1;
            case 'F':
                return 4;
            case 'G':
                return 2;
            case 'H':
                return 4;
            case 'I':
                return 1;
            case 'J':
                return 8;
            case 'K':
                return 5;
            case 'L':
                return 1;
            case 'M':
                return 3;
            case 'N':
                return 1;
            case 'O':
                return 1;
            case 'P':
                return 3;
            case 'Q':
                return 10;
            case 'R':
                return 1;
            case 'S':
                return 1;
            case 'T':
                return 1;
            case 'U':
                return 1;
            case 'V':
                return 4;
            case 'W':
                return 4;
            case 'X':
                return 8;
            case 'Y':
                return 4;
            case 'Z':
                return 10;
        }
        return 0;
    }
}
