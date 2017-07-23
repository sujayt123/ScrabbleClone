package scrabble;

import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.util.Pair;
import util.BoardHelper;
import util.TileHelper;
import util.Trie;
import util.TrieNode;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Controller implements Initializable {

    /**
     *  Access to the GUI representation of the board. Useful for defining drag-and-drop events.
     */
    private StackPane[][] board_cells = new StackPane[15][15];

    /**
     * Access to the GUI GridPane.
     */
    @FXML
    private GridPane gridPane;

    /**
     * Access to the HBox containing the player hand.
     */
    @FXML
    private HBox playerHandHBox;

    /**
     * Access to the text region containing the player score.
     */
    @FXML
    private Text playerScore;

    /**
     * Access to the text region containing the CPU score.
     */
    @FXML
    private Text cpuScore;

    /**
     * Access to the text region containing the status message.
     */
    @FXML
    private Text statusMessage;

    /**
     * The data currently represented on the screen. Propagates to main model at certain points in gameplay.
     * Bound to the text stored in each board_cell, if it exists. Any non-existing text states are created lazily.
     */
    private Text [][] viewModel = new Text[15][15];

    /**
     *  The most recently accepted state of the board. State may change upon successful user or computer move.
     *  Updated with a-z character values as board changes.
     */
    private char [][] mainModel = new char[15][15];

    /**
     * A queue that represents the bag of tiles remaining.
     */
    private Queue<Character> tilesRemaining;

    /**
     * A list for the player and cpu racks (henceforth referenced as "hands").
     */
    private List<Character> playerHand, cpuHand;

    /**
     * A list containing all the locations of board squares updated in the ViewModel during the player's turn.
     */
    private List<Pair<Integer, Integer>> changed_tile_coordinates;

    private HashSet<Character>[][] verticalCrossCheckSetsForModel, horizontalCrossCheckSetsForModelTranspose;

    /**
     * Work-around for an edge case with JavaFX drag-n-drop implementation
     */
    private static boolean wasDropSuccessful = false;

    /**
     * A prefix tree data structure to house the dictionary of scrabble words. See "util" for more information.
     */
    private static Trie trie;

    /**
     * Flag to invoke additional logic checks if it's the player's first turn.
     */
    private static boolean isFirstTurn = true;

    private Pair<Character[][], Integer> bestCPUPlay = new Pair<>(null, Integer.MAX_VALUE);

    /**
     * Initialization code that runs at application boot-time.
     * @param location (unused)
     * @param resources (unused)
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        /* Read dictionary into trie. */
        if (trie == null)
        {
            trie = new Trie();
        }

        /* Create (and initialize, if needed) initial data structures housing board information. */
        changed_tile_coordinates = new ArrayList<>();
        verticalCrossCheckSetsForModel = new HashSet[15][15];
        horizontalCrossCheckSetsForModelTranspose = new HashSet[15][15];
        IntStream.range(0, 15).forEach(i -> IntStream.range(0, 15).forEach(j -> {
            verticalCrossCheckSetsForModel[i][j] = new HashSet<>();
            horizontalCrossCheckSetsForModelTranspose[j][i] = new HashSet<>();
            verticalCrossCheckSetsForModel[i][j].addAll(IntStream.range((int)'A', (int)'Z').mapToObj(x-> (char)x).collect(Collectors.toList()));
            horizontalCrossCheckSetsForModelTranspose[j][i].addAll(IntStream.range((int)'A', (int)'Z').mapToObj(x-> (char)x).collect(Collectors.toList()));
            mainModel[i][j] = ' ';
        }));


        /*
         * Initialize the bindings to the view-model (view's understanding of board) data structure
         * and the containers (cells, more precisely, stack panes) housing it.
         * Mark these containers as valid targets for a drag n' drop motion.
         */
        gridPane.getChildren()
                .filtered(child -> child instanceof StackPane)
                .forEach(child -> {
                    final int row = gridPane.getRowIndex(child);
                    final int col = gridPane.getColumnIndex(child);
                    board_cells[row][col] = (StackPane) child;

                    ((StackPane) child).getChildren().
                            filtered((grandchild)-> grandchild instanceof Text)
                            .forEach((grandchild) ->
                                viewModel[row][col] = (Text) grandchild
                    );

                    child.setOnDragOver((event) -> {
                        /* accept it only if it is  not dragged from the same node
                         * and if it has a string data. also, ensure that
                          * the board cell can actually receive this tile */
                        if (event.getGestureSource() != child &&
                                event.getDragboard().hasString() &&
                                (viewModel[row][col] == null || viewModel[row][col].getText().length() == 2 ||
                                 viewModel[row][col].getText().charAt(0) == ' ')) {
                            event.acceptTransferModes(TransferMode.MOVE);
                        }

                        event.consume();
                    });

                    child.setOnDragEntered((event) -> {
                        /* the drag-and-drop gesture entered the target */
                        /* show to the user that it is an actual gesture target */
                        if (event.getGestureSource() != child &&
                                event.getDragboard().hasString() &&
                                (viewModel[row][col] == null || viewModel[row][col].getText().length() == 2 ||
                                        viewModel[row][col].getText().charAt(0) == ' ')) {
                            child.setStyle("-fx-border-color: darkblue; -fx-border-width: 3;");
                        }

                        event.consume();
                    });

                    child.setOnDragExited((event) -> {
                        /* mouse moved away, remove the graphical cues */
                        child.setStyle("-fx-border-width: 0;");
                        event.consume();
                    });

                    //TODO What should the board do when it receives a tile? Rigorously define the procedure
                    child.setOnDragDropped((event) -> {
                        /* if there is a string data on dragboard, read it and use it */
                        Dragboard db = event.getDragboard();
                        boolean success = false;
                        if (db.hasString()) {
                            // Creates the text element in the view model at that position if it doesn't exist.
                            if (viewModel[row][col] == null)
                            {
                                viewModel[row][col] = new Text(db.getString());
                                ((StackPane)child).getChildren().add(viewModel[row][col]);
                            }
                            else
                            {
                                /*
                                 * Change the text color of the pane to Black if needed.
                                 * This is to ensure that special squares are distinct from played tiles.
                                 */
                                viewModel[row][col].getStyleClass().add("black-text");
                            }
                            viewModel[row][col].setText(db.getString());
                            success = true;
                            // work-around for javafx edge case
                            wasDropSuccessful = true;
                        }
                        /* let the source know whether the string was successfully
                         * transferred and used */
                        event.setDropCompleted(success);
                        changed_tile_coordinates.add(new Pair<>(row, col));

                        event.consume();
                });
        });

        // Prepare to distribute tiles to players.
        tilesRemaining = TileHelper.getTileBagForGame();
        playerHand = new ArrayList<>();
        cpuHand = new ArrayList<>();

        // Distribute the starting racks (hereafter referenced as "hands") to the computer and the player.
        IntStream.range(0, 7).forEach( i->{
            playerHand.add(tilesRemaining.poll());
            cpuHand.add(tilesRemaining.poll());
            // Display the player's hand as stackpanes in the HBox in the bottom of the borderpane layout.
            addTileToUserHand(playerHand.get(i));
        });
    }

    /**
     * Recall all tiles placed on the board this turn to the player's hand.
     */
    public void recallTiles()
    {
        changed_tile_coordinates.forEach( pair ->
        {
            int i = pair.getKey();
            int j = pair.getValue();
            addTileToUserHand(viewModel[i][j].getText().charAt(0));

            // Reset the view model's text in accordance with whether it's a special tile.
            if (board_cells[i][j].getStyleClass().size() > 0)
            {
                String specialText = board_cells[i][j].getStyleClass().get(0).substring(0, 2);
                viewModel[i][j].setText(specialText);
                viewModel[i][j].getStyleClass().remove("black-text");
            }
            else
            {
                viewModel[i][j].setText(" ");
            }
        });
        changed_tile_coordinates.clear();
    }

    /**
     * Utility method to add a character to the user's hand in the GUI.
     * @param letter The character to add to the HBOX housing the user's hand
     */
    public void addTileToUserHand(char letter)
    {
        StackPane s = new StackPane();
        s.setStyle("-fx-background-color: lightyellow");
        s.setMinWidth(40);
        s.setMinHeight(40);
        s.setMaxWidth(40);
        s.setMaxHeight(40);
        s.getChildren().add(new Text(letter + ""));
        playerHandHBox.getChildren().add(s);

        // Mark the user's tiles as valid sources for a drag n' drop motion.
        s.setOnDragDetected((event) -> {
            /* drag was detected, start drag-and-drop gesture*/
            Dragboard db = s.startDragAndDrop(TransferMode.MOVE);

            /* put a string on dragboard */
            ClipboardContent content = new ClipboardContent();
            content.putString("" + letter);
            db.setContent(content);

            event.consume();
        });

        s.setOnDragDone((event) -> {
            /* if the data was successfully moved, clear it */
            if (event.getTransferMode() == TransferMode.MOVE && wasDropSuccessful) {
                playerHandHBox.getChildren().remove(s);
                wasDropSuccessful = false;
            }
            event.consume();
        });
    }

    /**
     * Attempts a player move. Triggered on click of "Move" button in GUI.
     */
    public void attemptPlayerMove()
    {
        if (isValidMove(BoardHelper.getViewModelAs2DArray(viewModel), changed_tile_coordinates))
        {
            statusMessage.setText("Your move has been registered.");
            statusMessage.getStyleClass().clear();
            statusMessage.getStyleClass().add("success-text");
            isFirstTurn = false;
            makePlayerMove();
        }
        else
        {
            statusMessage.setText("That didn't work out so well.");
            statusMessage.getStyleClass().clear();
            statusMessage.getStyleClass().add("error-text");
        }
    }

    /**
     * Checks if the viewModel is consistent with a valid move.
     * @return if the player move was valid
     */
    private boolean isValidMove(char[][] model, List<Pair<Integer, Integer>> changed_tile_coordinates)
    {
        boolean valid = changed_tile_coordinates.size() > 0;

        // Determine if the play is vertical or horizontal.
        boolean playWasHorizontal = changed_tile_coordinates.stream()
                .filter(x -> !x.getKey().equals(changed_tile_coordinates.get(0).getKey())).count() == 0;

        boolean playWasVertical = changed_tile_coordinates.stream()
                .filter(x -> !x.getValue().equals(changed_tile_coordinates.get(0).getValue())).count() == 0;

        valid = valid && (playWasVertical || playWasHorizontal);

        System.out.println("Checkpt 1: valid? " + valid);
        if (playWasVertical){
            int col = changed_tile_coordinates.get(0).getValue();
            valid = valid && validVerticalPlay(model, changed_tile_coordinates);

            // Ensure that the word is indeed connected vertically (and is not just two disjoint words in the same col)
            int min_row_ind = changed_tile_coordinates.stream().map(Pair::getKey).reduce((x, y) -> x < y ? x : y).get();
            int max_row_ind = changed_tile_coordinates.stream().map(Pair::getKey).reduce((x, y) -> x > y ? x : y).get();

            valid = valid && IntStream.rangeClosed(min_row_ind, max_row_ind)
                    .mapToObj(
                        i -> model[i][col] != ' ')
                    .reduce((x, y) -> x && y).get();


        }
        else
        {
            valid = valid && validHorizontalPlay(model, changed_tile_coordinates);
            int row = changed_tile_coordinates.get(0).getKey();

            // Ensure that the word is indeed connected horizontally (and is not just two disjoint words in the same row)
            int min_col_ind = changed_tile_coordinates.stream().map(Pair::getValue).reduce((x, y) -> x < y ? x : y).get();
            int max_col_ind = changed_tile_coordinates.stream().map(Pair::getValue).reduce((x, y) -> x > y ? x : y).get();
            valid = valid && IntStream.rangeClosed(min_col_ind, max_col_ind)
                    .mapToObj(
                        j -> model[row][j] != ' ')
                    .reduce((x, y) -> x && y).get();
        }

        System.out.println("Checkpt 2: valid? " + valid);

        if (isFirstTurn)
        {
            valid = valid
                    && changed_tile_coordinates.indexOf(new Pair<>(7, 7)) != -1
                    && changed_tile_coordinates.size() >= 2;
        }
        else
        {
            // All subsequent turns must consist of a play that is vertically or horizontally adjacent to at
            // least one other letter of a word that existed before this turn.
            valid = valid && changed_tile_coordinates.stream().filter(x -> {
                int r = x.getKey();
                int c = x.getValue();
                return (r > 0 && mainModel[r-1][c] != ' ')
                        || (r < 14 && mainModel[r+1][c] != ' ')
                        || (c > 0 && mainModel[r][c-1] != ' ')
                        || (c < 14 && mainModel[r][c+1] != ' ');
            }).count() > 0;
        }
        System.out.println("Checkpt 3: valid? " + valid);

        return valid;

    }

    /**
     * Builds the vertical word in which the letter at the provided coordinate in the provided model
     * If the provided coordinate is empty, returns the prefix to the word that would exist if a tile were placed there.
     *
     * @param model the model to use for construction of the word
     * @param pair coordinate
     * @return the word itself, as well as the starting index of the word
     */
    private static Pair<String, Integer> buildVerticalWordForCoordinate(char[][] model, Pair<Integer, Integer> pair)
    {
        StringBuilder sb = new StringBuilder();
        int row = pair.getKey();
        int col = pair.getValue();

        OptionalInt top_exclusive = IntStream.iterate(row - 1, i -> i - 1)
                .limit(row)
                .filter(r -> model[r][col] == ' ')
                .findFirst();
        OptionalInt bot_exclusive = IntStream.range(row, 15)
                .filter(r -> model[r][col] == ' ')
                .findFirst();
        int top_exc = top_exclusive.isPresent() ? top_exclusive.getAsInt(): -1;
        int bot_exc = bot_exclusive.isPresent() ? bot_exclusive.getAsInt(): 15;
        IntStream.range(top_exc + 1, bot_exc)
                .forEach(r ->
                        sb.append(model[r][col])
                );

        return new Pair<>(sb.length() > 0 ? sb.toString() : "", top_exc + 1);
    }

    /**
     * Builds the vertical word in which the letter at the provided coordinate in the provided model
     * If the provided coordinate is empty, returns the prefix to the word that would exist if a tile were placed there.
     *
     * @param model the model to use for construction of the word
     * @param pair coordinate
     * @return the word itself, as well as the starting index of the word
     */
    private static Pair<String, Integer> buildHorizontalWordForCoordinate(char[][] model, Pair<Integer, Integer> pair)
    {
        System.out.println(pair.toString());
        StringBuilder sb = new StringBuilder();
        int row = pair.getKey();
        int col = pair.getValue();

        OptionalInt top_exclusive = IntStream.iterate(col - 1, c -> c - 1)
                .limit(col)
                .filter(c -> model[row][c] == ' ')
                .findFirst();
        OptionalInt bot_exclusive = IntStream.range(col, 15)
                .filter(c -> model[row][c] == ' ')
                .findFirst();
        int left_exc = top_exclusive.isPresent() ? top_exclusive.getAsInt(): -1;
        int right_exc = bot_exclusive.isPresent() ? bot_exclusive.getAsInt(): 15;
        IntStream.range(left_exc + 1, right_exc)
                .forEach(c ->
                        sb.append(model[row][c])
                );

        return new Pair<>(sb.length() > 0 ? sb.toString() : "", left_exc + 1);
    }

    /**
     * Is the letter that is at location "pair" in the GUI part of a valid word in the vertical direction?
     * @param p an (x,y) coordinate in the GUI denoting a location of an inserted tile
     */
    private boolean validVerticalPlay(char[][] board, List<Pair<Integer, Integer>> changed_tile_coordinates) {
        /*
         * First, check if the vertical part constitutes a word.
         */
        String verticalWord = buildVerticalWordForCoordinate(board, changed_tile_coordinates.get(0)).getKey();
        // Now that we have the vertical word we've formed, let's see whether it is valid.

        TrieNode tn = trie.getNodeForPrefix(verticalWord);

//        System.out.println("valid vertical word formed for " + verticalWord + " :" + (verticalWord.length() == 1 || (tn != null && tn.isWord())));
        changed_tile_coordinates.stream().forEach((pair)->{
            System.out.println(pair.toString() + " has horizontal cross check sets of " + horizontalCrossCheckSetsForModelTranspose[pair.getValue()][pair.getKey()]);
        });

        /*
         * Second, check if the horizontal words formed in a parallel play follow the cross sets.
         */
        return (verticalWord.length() == 1 || (tn != null && tn.isWord()))
                && (changed_tile_coordinates.stream().
                filter((pair) ->
                        horizontalCrossCheckSetsForModelTranspose[pair.getValue()][pair.getKey()]
                                .contains(board[pair.getKey()][pair.getValue()]))
                .count() == changed_tile_coordinates.size() || isFirstTurn);
    }

    /**
     * Is the letter that is at location "pair" in the GUI part of a valid word in the vertical direction?
     * @param p an (x,y) coordinate in the GUI denoting a location of an inserted tile
     */
    private boolean validHorizontalPlay(char[][] board, List<Pair<Integer, Integer>> changed_tile_coordinates) {
        /*
         * First, check if the vertical part constitutes a word.
         */
        String horizontalWord = buildHorizontalWordForCoordinate(board, changed_tile_coordinates.get(0)).getKey();
        // Now that we have the vertical word we've formed, let's see whether it is valid.

        TrieNode tn = trie.getNodeForPrefix(horizontalWord);

        /*
         * Second, check if the vertical words formed in a parallel play follow the cross sets.
         */
//        System.out.println("valid horizontal word formed for " + horizontalWord + " :" + (horizontalWord.length() == 1 || (tn != null && tn.isWord())));
        changed_tile_coordinates.stream().forEach((pair)->{
            System.out.println(pair.toString() + " has vertical cross check sets of " + verticalCrossCheckSetsForModel[pair.getKey()][pair.getValue()]);
        });

        return (horizontalWord.length() == 1 || (tn != null && tn.isWord()))
                && (changed_tile_coordinates.stream().
                filter((pair) ->
                        verticalCrossCheckSetsForModel[pair.getKey()][pair.getValue()]
                                .contains(board[pair.getKey()][pair.getValue()]))
                .count() == changed_tile_coordinates.size() || isFirstTurn);
    }

    /**
     * Finalizes the move for the player, assuming it was valid. Propagates changes
     * from viewmodel to model.
     */
    private void makePlayerMove()
    {
        System.out.println("Valid move");

        // Step 1: increment player score
        // Determine if the play is vertical or horizontal.
        boolean playWasHorizontal = changed_tile_coordinates.stream()
                .filter(x -> !x.getKey().equals(changed_tile_coordinates.get(0).getKey())).count() == 0;

        int score = Integer.parseInt(playerScore.getText().split(":")[1]);
        System.out.println("Player score is currently: " + score);

        if (!playWasHorizontal)
        {
            score += scoreVertical(BoardHelper.getViewModelAs2DArray(viewModel), changed_tile_coordinates.get(0));
            score += changed_tile_coordinates.stream().map(x-> scoreHorizontal(BoardHelper.getViewModelAs2DArray(viewModel), x)).reduce((x,y)->x+y).get();
        }
        else
        {
            score += scoreHorizontal(BoardHelper.getViewModelAs2DArray(viewModel), changed_tile_coordinates.get(0));
            score += changed_tile_coordinates.stream().map(x -> scoreVertical(BoardHelper.getViewModelAs2DArray(viewModel), x)).reduce((x,y)->x+y).get();
        }
        if (changed_tile_coordinates.size() == 7)
        {
            score += 50;
        }

        playerScore.setText("Player Score:" + score);

        // Step 2: propagate viewModel to model
        changed_tile_coordinates.forEach(p -> {
            int row = p.getKey();
            int col = p.getValue();
            mainModel[row][col] = viewModel[row][col].getText().charAt(0);
        });


        // Step 3: take as many tiles from the bag as you can (up to the number removed) and give them to the player
        changed_tile_coordinates.forEach((x) -> {
            playerHand.remove((Character)viewModel[x.getKey()][x.getValue()].getText().charAt(0));
            Character c = tilesRemaining.poll();
            if (c != null)
            {
                playerHand.add(c);
                addTileToUserHand(c);
            }
        });

        // Step 5: Recompute cross sets
//        computeCrossCheckSets(verticalCrossCheckSetsForModel, mainModel, BoardHelper.generateListOfAdjacentVerticalCoordinates(changed_tile_coordinates));
//        computeCrossCheckSets(horizontalCrossCheckSetsForModelTranspose, BoardHelper.getTransposeOfModel(mainModel), BoardHelper.generateListOfAdjacentHorizontalCoordinates(changed_tile_coordinates)
//                .stream().map(x-> new Pair<>(x.getValue(), x.getKey())).collect(Collectors.toList()));
        computeCrossCheckSets(verticalCrossCheckSetsForModel, mainModel, BoardHelper.getCoordinatesListForBoard());
        computeCrossCheckSets(horizontalCrossCheckSetsForModelTranspose, BoardHelper.getTransposeOfModel(mainModel), BoardHelper.getCoordinatesListForBoard());
        // Step 6: clear changed_tiles list
        changed_tile_coordinates.clear();

        BoardHelper.getCoordinatesListForBoard().forEach(x->System.out.println("Vertical set for " + x.toString() + " : " + verticalCrossCheckSetsForModel[x.getKey()][x.getValue()]));
        BoardHelper.getCoordinatesListForBoard().forEach(x->System.out.println("Horizontal set for " + x.toString() + " : " + horizontalCrossCheckSetsForModelTranspose[x.getValue()][x.getKey()]));

        System.out.println("The CPU has in his hand : " + cpuHand.toString());
        // TODO Step 7: let CPU make its move
        makeCPUMove();

        return;
    }


    /**
     * Computes the VERTICAL cross check sets for a given model.
     *
     * To compute the horizontal cross check sets for that model,
     * pass in horizontalCrossCheckSetsForTranspose, model transpose, and coordinates to update
     * in accordance with transposed matrix.
     *
     * @param crossCheckSets
     * @param model
     * @param coordinates
     */
    private void computeCrossCheckSets(HashSet<Character>[][] crossCheckSets,
                                       char[][] model,
                                       List<Pair<Integer, Integer>> coordinates){
        coordinates.stream().filter(pair -> model[pair.getKey()][pair.getValue()] == ' ').forEach((pair) -> {
                int i = pair.getKey();
                int j = pair.getValue();
                crossCheckSets[i][j].clear();
                Pair<String, Integer> verticalPrefixToThisSquare = buildVerticalWordForCoordinate(model, pair);
                TrieNode prefixNode = trie.getNodeForPrefix(verticalPrefixToThisSquare.getKey());
                if (prefixNode != null)
                {
                    StringBuilder verticalSuffixToThisSquare = new StringBuilder();
                    OptionalInt bot_exclusive = IntStream.range(i + 1, 15)
                            .filter(r-> model[r][j] == ' ')
                            .findFirst();
                    IntStream.range(i + 1, bot_exclusive.isPresent() ? bot_exclusive.getAsInt() : 15)
                            .forEach(x -> {
                               verticalSuffixToThisSquare.append(model[x][j]);
                            });

                    prefixNode.getOutgoingEdges().keySet().forEach(c -> {
                        if (prefixNode.getNodeForPrefix(c + verticalSuffixToThisSquare.toString(), 0) != null
                                && prefixNode.getNodeForPrefix(c + verticalSuffixToThisSquare.toString(), 0).isWord()) {
                            crossCheckSets[i][j].add(c);
                        }
                    });

                    if (prefixNode == trie.root && verticalSuffixToThisSquare.toString().equals(""))
                    {
                        crossCheckSets[i][j].addAll(IntStream.range((int)'A', (int)'Z').mapToObj(x-> (char)x).collect(Collectors.toList()));

                    }
                }
            });
    }

    private void makeCPUMove()
    {
        Set<Pair<Integer, Integer>> anchorSquares = BoardHelper.getCoordinatesListForBoard()
                .stream()
                .filter(x -> mainModel[x.getKey()][x.getValue()] == ' ')
                .filter(x -> Stream.concat(BoardHelper.generateListOfAdjacentHorizontalCoordinates(Collections.singletonList(x)).stream(),
                        BoardHelper.generateListOfAdjacentVerticalCoordinates(Collections.singletonList(x)).stream())
                        .filter(p -> !p.equals(x) && mainModel[p.getKey()][p.getValue()] != ' ').count() > 0).collect(Collectors.toSet());

        System.out.println(anchorSquares.toString());
        // TODO: I think all the logic in CPU move up until this point is valid.
        bestCPUPlay = new Pair<>(null, Integer.MIN_VALUE);
        anchorSquares.forEach(square -> computeBestHorizontalPlayAtAnchor(BoardHelper.getCopyOfModel(mainModel), anchorSquares, square, verticalCrossCheckSetsForModel));
//        anchorSquares.stream().map(x -> new Pair<>(x.getValue(), x.getKey()))
//                .forEach(square -> computeBestHorizontalPlayAtAnchor(BoardHelper.getTransposeOfModel(mainModel), square, horizontalCrossCheckSetsForModelTranspose));
        ArrayList<Pair<Integer, Integer>> newSquaresPlacedLocations = new ArrayList<>();
        for (int i = 0; i < 15; i++)
        {
            for (int j = 0; j < 15; j++)
            {
                if (mainModel[i][j] != ' ')
                {
                    viewModel[i][j].getStyleClass().removeAll("bold-text");
                    viewModel[i][j].getStyleClass().add("black-text");
                }
                if (bestCPUPlay.getKey()[i][j] != mainModel[i][j])
                {
                    if (viewModel[i][j] == null)
                    {
                        viewModel[i][j] = new Text();
                        board_cells[i][j].getChildren().add(viewModel[i][j]);
                    }
                    newSquaresPlacedLocations.add(new Pair(i, j));
                    viewModel[i][j].setText(bestCPUPlay.getKey()[i][j] + "");
                    viewModel[i][j].getStyleClass().add("bold-text");
                    mainModel[i][j] = bestCPUPlay.getKey()[i][j];
                    cpuHand.remove((Character)mainModel[i][j]);
                }
            }
        }
        computeCrossCheckSets(verticalCrossCheckSetsForModel, mainModel, BoardHelper.generateListOfAdjacentVerticalCoordinates(newSquaresPlacedLocations));
        computeCrossCheckSets(horizontalCrossCheckSetsForModelTranspose, BoardHelper.getTransposeOfModel(mainModel), BoardHelper.generateListOfAdjacentHorizontalCoordinates(newSquaresPlacedLocations)
                .stream().map(x-> new Pair<>(x.getValue(), x.getKey())).collect(Collectors.toList()));
        int score = Integer.parseInt(cpuScore.getText().split(":")[1]);
        System.out.println("Current cpu score " + score);
        score += bestCPUPlay.getValue();
        cpuScore.setText("CPU Score:" + score);

        for (int k = cpuHand.size(); k < 7; k++)
        {
            Character c = tilesRemaining.poll();
            if (c != null)
            {
                cpuHand.add(c);
            }
        }
    }


    /**
     *
     * @param model the model to use for recursive backtracking
     * @param square anchor square
     * @param verticalCrossCheckSets vertical cross checks
     * @return (horizontal string, leftmost position, score)
     */
    private void computeBestHorizontalPlayAtAnchor(char[][] model, Set<Pair<Integer, Integer>> anchors, Pair<Integer, Integer> square, HashSet<Character>[][] verticalCrossCheckSets) {
        int col = square.getValue();
        OptionalInt left_exclusive = IntStream.iterate(col - 1, i -> i - 1)
                .limit(col)
                .filter(c -> model[square.getKey()][c] != ' ' || anchors.contains(new Pair<>(square.getKey(), c)))
                .findFirst();
        int k = left_exclusive.isPresent() ? col - left_exclusive.getAsInt() - 1: col;
        if (k == 0)
        {
            if (col == 0)
            {
                ExtendRight(model, square, "", cpuHand, trie.root, verticalCrossCheckSets);
            }
            else if (model[square.getKey()][col - 1] != ' ')
            {
                String prefix = buildHorizontalWordForCoordinate(model, new Pair<>(square.getKey(), col - 1)).getKey();
                ExtendRight(model, square, prefix, cpuHand, trie.getNodeForPrefix(prefix), verticalCrossCheckSets);
            }
        }

        LeftPart(model, square,"", cpuHand, trie.root, verticalCrossCheckSets, k, k);
    }

    private void LeftPart(char[][] board, Pair<Integer,Integer> square, String partialWord, List<Character> tilesRemainingInRack, TrieNode N, HashSet<Character>[][] crossCheckSets, int limit, int maxLimit)
    {
        for (int i = 0 ; i < 15 ; i++) {
            for (int j = 0; j< 15; j++)
            {
                System.out.print(board[i][j]);
            }
            System.out.println();
        }
        ExtendRight(board, square, partialWord, tilesRemainingInRack, N, crossCheckSets);
        if (limit > 0)
        {
            N.getOutgoingEdges().keySet().forEach(c -> {
                if (tilesRemainingInRack.contains((Character)c))
                {
                    for (int i = square.getValue() - maxLimit; i < square.getValue(); i++)
                    {
                        board[square.getKey()][i] = board[square.getKey()][i + 1];
                    }
                    board[square.getKey()][square.getValue() - 1] = c;
                    tilesRemainingInRack.remove((Character)c);
                    LeftPart(board, square, partialWord + c, tilesRemainingInRack, N.getOutgoingEdges().get(c), crossCheckSets, limit - 1, maxLimit);
                    tilesRemainingInRack.add(c);
                    for (int i = square.getValue() - 1; i > square.getValue() - maxLimit; i--)
                    {
                        board[square.getKey()][i] = board[square.getKey()][i - 1];
                    }
                    board[square.getKey()][square.getValue() - maxLimit] = ' ';
                }
            });
        }
    }

    private void ExtendRight(char[][] board, Pair<Integer, Integer> square, String partialWord, List<Character> tilesRemainingInRack, TrieNode N, HashSet<Character>[][] crossCheckSets)
    {
        for (int i = 0 ; i < 15 ; i++) {
            for (int j = 0; j< 15; j++)
            {
                System.out.print(board[i][j]);
            }
            System.out.println();
        }
        if (square.getValue() >= 15)
            return;
        if (board[square.getKey()][square.getValue()] == ' ')
        {
            if (N.isWord())
            {
                LegalMove(board, partialWord);
            }
            N.getOutgoingEdges().keySet().forEach(c -> {
                if (tilesRemainingInRack.contains(c) && crossCheckSets[square.getKey()][square.getValue()].contains(c))
                {
                    tilesRemainingInRack.remove((Character)c);
                    board[square.getKey()][square.getValue()] = c;
                    ExtendRight(board, new Pair<>(square.getKey(), square.getValue() + 1), partialWord + c, tilesRemainingInRack, N.getOutgoingEdges().get(c), crossCheckSets);
                    board[square.getKey()][square.getValue()] = ' ';
                    tilesRemainingInRack.add(c);
                }
            });
        }
        else
        {
            if (N.getOutgoingEdges().containsKey(board[square.getKey()][square.getValue()]))
            {
                ExtendRight(board, new Pair<>(square.getKey(), square.getValue() + 1), partialWord + board[square.getKey()][square.getValue()], tilesRemainingInRack, N.getOutgoingEdges().get(board[square.getKey()][square.getValue()]), crossCheckSets);
            }
        }
    }

    private void LegalMove(char[][] board, String partialWord) {
        // Get all pairs in which board differs from mainModel.
        List<Pair<Integer, Integer>> changed_coords_by_cpu = BoardHelper.getCoordinatesListForBoard().stream().filter(x -> {
            int r = x.getKey();
            int c = x.getValue();
            return board[r][c] != mainModel[r][c];
        }).collect(Collectors.toList());
        if (changed_coords_by_cpu.size() == 0 || !isValidMove(board, changed_coords_by_cpu))
            return;
        // Since we know the move was horizontal, let's score it.
        int score = changed_coords_by_cpu.stream().reduce(scoreHorizontal(board, changed_coords_by_cpu.get(0)),
                (acc, x) -> {
                    return acc + scoreVertical(board, x);
                }, (acc1, acc2) -> acc1 + acc2);
        if (changed_coords_by_cpu.size() == 7)
        {
            score += 50;
        }
        if (score > bestCPUPlay.getValue())
        {
            Character[][] boardToSave = new Character[15][15];
            BoardHelper.getCoordinatesListForBoard().forEach(x ->
                boardToSave[x.getKey()][x.getValue()] = board[x.getKey()][x.getValue()]
            );
            System.out.println("The word " + partialWord + " garnered " + score + " points for the CPU");
            bestCPUPlay = new Pair<>(boardToSave, score);
        }


    }

    /**
     * You must invoke this function before propagating board --> mainModel. Likewise for scoreHorizontal.
     * @param board
     * @param pair
     * @return
     */
    private int scoreVertical(char[][] board, Pair<Integer, Integer> pair) {
        int row = pair.getKey();
        int col = pair.getValue();

        Pair<String, Integer> p = buildVerticalWordForCoordinate(board, pair);
        int start_index = p.getValue();
        int length = p.getKey().length();

        // If there is no word (of 2 or more letters) formed, return 0 immediately.
        if (length == 1)
        {
            return 0;
        }

        Pair<Integer, Pair<Integer, Integer>> wordScoreTuple =
                IntStream.range(start_index, start_index + length)
                        .mapToObj(r -> r)
                        .reduce(new Pair<>(0, new Pair<>(0, 0)),
                                (acc, r) -> {
                                    int partialScore = acc.getKey();
                                    int dw_count = acc.getValue().getKey();
                                    int tw_count = acc.getValue().getValue();
                                    int letterScore = TileHelper.scoreCharacter(board[r][col]);
                                    if (board_cells[r][col].getStyleClass().size() > 0 && mainModel[r][col] == ' ') {
                                        switch (board_cells[r][col].getStyleClass().get(0).substring(0, 2)) {
                                            case "DW":
                                                dw_count++;
                                                break;
                                            case "TW":
                                                tw_count++;
                                                break;
                                            case "DL":
                                                letterScore *= 2;
                                                break;
                                            case "TL":
                                                letterScore *= 3;
                                                break;
                                        }
                                    }
                                    return new Pair<>(partialScore + letterScore, new Pair<>(dw_count, tw_count));
                                }
                                , (pairA, pairB) ->
                                        new Pair<>(pairA.getKey() + pairB.getKey(),
                                                new Pair<>(pairA.getValue().getKey() + pairB.getValue().getKey(),
                                                        pairA.getValue().getValue() + pairB.getValue().getValue()))
                        );

        int baseScore = wordScoreTuple.getKey();
        int dw_count = wordScoreTuple.getValue().getKey();
        int tw_count = wordScoreTuple.getValue().getValue();

        return (int) (baseScore * Math.pow(2, dw_count) * Math.pow(3, tw_count));
    }

    private int scoreHorizontal(char[][] board, Pair<Integer, Integer> pair) {

        int row = pair.getKey();
        int col = pair.getValue();

        Pair<String, Integer> p = buildHorizontalWordForCoordinate(board, pair);
        int start_index = p.getValue();
        int length = p.getKey().length();

        // If there is no word (of 2 or more letters) formed, return 0 immediately.
        if (length == 1)
        {
            return 0;
        }

        Pair<Integer, Pair<Integer, Integer>> wordScoreTuple =
                IntStream.range(start_index, start_index + length)
                        .mapToObj(c -> c)
                        .reduce(new Pair<>(0, new Pair<>(0, 0)),
                                (acc, c) -> {
                                    int partialScore = acc.getKey();
                                    int dw_count = acc.getValue().getKey();
                                    int tw_count = acc.getValue().getValue();
                                    int letterScore = TileHelper.scoreCharacter(board[row][c]);
                                    if (board_cells[row][c].getStyleClass().size() > 0 && mainModel[row][c] == ' ') {
                                        switch (board_cells[row][c].getStyleClass().get(0).substring(0, 2)) {
                                            case "DW":
                                                dw_count++;
                                                break;
                                            case "TW":
                                                tw_count++;
                                                break;
                                            case "DL":
                                                letterScore *= 2;
                                                break;
                                            case "TL":
                                                letterScore *= 3;
                                                break;
                                        }
                                    }
                                    return new Pair<>(partialScore + letterScore, new Pair<>(dw_count, tw_count));
                                }
                                , (pairA, pairB) ->
                                        new Pair<>(pairA.getKey() + pairB.getKey(),
                                                new Pair<>(pairA.getValue().getKey() + pairB.getValue().getKey(),
                                                        pairA.getValue().getValue() + pairB.getValue().getValue()))
                        );

        int baseScore = wordScoreTuple.getKey();
        int dw_count = wordScoreTuple.getValue().getKey();
        int tw_count = wordScoreTuple.getValue().getValue();

        return (int) (baseScore * Math.pow(2, dw_count) * Math.pow(3, tw_count));
    }
}
