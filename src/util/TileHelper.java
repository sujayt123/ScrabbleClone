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
        switch (Character.toLowerCase(c)) {
            case 'a':
                return 1;
            case 'b':
                return 3;
            case 'c':
                return 3;
            case 'd':
                return 2;
            case 'e':
                return 1;
            case 'f':
                return 4;
            case 'g':
                return 2;
            case 'h':
                return 4;
            case 'i':
                return 1;
            case 'j':
                return 8;
            case 'k':
                return 5;
            case 'l':
                return 1;
            case 'm':
                return 3;
            case 'n':
                return 1;
            case 'o':
                return 1;
            case 'p':
                return 3;
            case 'q':
                return 10;
            case 'r':
                return 1;
            case 's':
                return 1;
            case 't':
                return 1;
            case 'u':
                return 1;
            case 'v':
                return 4;
            case 'w':
                return 4;
            case 'x':
                return 8;
            case 'y':
                return 4;
            case 'z':
                return 10;
        }
        return 0;
    }
}
