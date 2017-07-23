package util;

import javafx.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by sujay on 7/22/17.
 */
public class BoardHelper {

    private static List<Pair<Integer, Integer>> matrixOfCoords = IntStream.range(0, 15).mapToObj(r-> IntStream.range(0, 15).mapToObj(x->x)
            .reduce(new ArrayList<Pair<Integer, Integer>>(), (acc, c) -> {acc.add(new Pair<>(r, c));return acc;},
                    (acc1, acc2)-> {
                        acc1.addAll(acc2);
                        return acc1;
                    })).flatMap(List::stream).collect(Collectors.toList());

    public static List<Pair<Integer,Integer>> getCoordinatesListForBoard()
    {
        return matrixOfCoords;
    }

    public static List<Pair<Integer, Integer>> generateListOfAdjacentVerticalCoordinates(List<Pair<Integer, Integer>> changed_coords)
    {
        return changed_coords.stream().map((pair) -> {
            int r = pair.getKey();
            int c = pair.getValue();
            List<Pair<Integer, Integer>> output = new ArrayList<>();
            if (r > 0)
                    output.add(new Pair<>(r - 1, c));
            if (r < 14)
                output.add(new Pair<>(r + 1, c));
            return output;
        }).flatMap(List::stream).collect(Collectors.toList());
    }

    public static List<Pair<Integer, Integer>> generateListOfAdjacentHorizontalCoordinates(List<Pair<Integer, Integer>> changed_coords)
    {
        return changed_coords.stream().map((pair) -> {
            int r = pair.getKey();
            int c = pair.getValue();
            List<Pair<Integer, Integer>> output = new ArrayList<>();
            if (c > 0)
                output.add(new Pair<>(r, c - 1));
            if (c < 14)
                output.add(new Pair<>(r, c + 1));
            return output;
        }).flatMap(List::stream).collect(Collectors.toList());
    }

    public static char[][] getTransposeOfModel(char[][] model)
    {
        char[][] newArray = new char[15][15];
        matrixOfCoords.forEach((pair)->{
           newArray[pair.getKey()][pair.getValue()] = model[pair.getValue()][pair.getKey()];
        });
        return newArray;
    }

    public static char[][] getCopyOfModel(char[][] model)
    {
        char[][] newArray = new char[15][15];
        matrixOfCoords.forEach((pair)->{
            newArray[pair.getKey()][pair.getValue()] = model[pair.getKey()][pair.getValue()];
        });
        return newArray;
    }
}
