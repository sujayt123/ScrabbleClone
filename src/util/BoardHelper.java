package util;

import javafx.util.Pair;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by sujay on 7/22/17.
 */
public class BoardHelper {

    private static List<Pair<Integer, Integer>> matrixOfCoords;

    /**
     * Returns a singleton of the list containing all (x, y) coordinate pairs for the Scrabble board.
     *
     * @return List of elements with x ranging from 0 to 14 and y ranging from 0 to 14
     */
    public static List<Pair<Integer,Integer>> getCoordinatesListForBoard()
    {
        if (matrixOfCoords == null)
        {
            matrixOfCoords = forEachBoardSquareAsList(Pair::new);
        }
        return matrixOfCoords;
    }

    /**
     * Applies the function biFunction to each square in the board and returns its results on each coordinate as a list.
     * @param biFunction A lambda that takes in two integers as input and spits something out as output
     * @param <T> The output type
     * @return a List of T
     */
    public static <T> List<T> forEachBoardSquareAsList(BiFunction<Integer, Integer, T> biFunction)
    {
        return IntStream.range(0, 15)
                .mapToObj(x->x)
                .flatMap(i ->
                        IntStream.range(0, 15)
                                .mapToObj(j -> biFunction.apply(i, j))).collect(Collectors.toList());
    }

    /**
     * Applies the function biFunction to each square in the board and returns its results on each coordinate as a list.
     * @param biFunction A lambda that takes in two integers as input and spits something out as output
     * @param <T> The output type
     * @return a List of T
     */
    public static <T> List<List<T>> forEachBoardSquareAsNestedList(BiFunction<Integer, Integer, T> biFunction)
    {
        return IntStream.range(0, 15)
                .mapToObj(x->x)
                .map(i ->
                        IntStream.range(0, 15)
                                .mapToObj(j -> biFunction.apply(i, j)).collect(Collectors.toList()))
                .collect(Collectors.toList());
    }

    /**
     * Applies the function biFunction to each provided square and returns its results on each coordinate as a list.
     * @param biFunction A lambda that takes in two integers as input and spits something out as output
     * @param input the input list of coords
     * @param <T> The output type
     * @return a List of T
     * @return
     */
    public static <T> List<T> forEachProvidedSquareAsList(BiFunction<Integer, Integer, T> biFunction, List<Pair<Integer, Integer>> input)
    {
        return input.stream().map((pair) -> {
          int r = pair.getKey();
          int c = pair.getValue();
          return biFunction.apply(r, c);
        }).collect(Collectors.toList());
    }

    /**
     * Applies the Function function to each letter in the alphabet and returns the output as a list of results.
     * @param function A lambda that takes in two integers as input and spits something out as output
     * @param <T> The output type
     * @return a List of T
     */
    public static <T, U> List<U> forEachAtoZ(Function<Character, U> function)
    {
        return IntStream.rangeClosed((int)'A', (int)'Z').
                mapToObj(x-> (char)x).map(function).collect(Collectors.toList());
    }

}
