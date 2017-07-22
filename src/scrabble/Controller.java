package scrabble;

import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.util.Pair;
import util.TileHelper;
import util.Trie;
import util.TrieNode;

import java.net.URL;
import java.util.*;
import java.util.stream.IntStream;

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
     *  Initialized to '\0' by default and updated with a-z character values as board changes.
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

    private HashSet[][] crossCheckSets;

    /**
     * Work-around for an edge case with JavaFX drag-n-drop implementation
     */
    private static boolean wasDropSuccessful = false;

    /**
     * A prefix tree data structure to house the dictionary of scrabble words. See "util" for more information.
     */
    private Trie trie;

    /**
     * Flag to invoke additional logic checks if it's the player's first turn.
     */
    private boolean isFirstTurn = true;

    /**
     * Initialization code that runs at application boot-time.
     * @param location (unused)
     * @param resources (unused)
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        /* Read dictionary into trie. */
        trie = new Trie();

        /* Create (and initialize, if needed) initial data structures housing board information. */
        changed_tile_coordinates = new ArrayList<>();
        crossCheckSets = new HashSet[15][15];
        IntStream.range(0, 15).forEach(i -> IntStream.range(0, 15).forEach(j -> {
            crossCheckSets[i][j] = new HashSet<>();
        }));

        computeCrossCheckSets(0, 15, 0, 15);

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
                                (viewModel[row][col] == null ||
                                 viewModel[row][col].getText().length() != 1)) {
                            event.acceptTransferModes(TransferMode.MOVE);
                        }

                        event.consume();
                    });

                    child.setOnDragEntered((event) -> {
                        /* the drag-and-drop gesture entered the target */
                        /* show to the user that it is an actual gesture target */
                        if (event.getGestureSource() != child &&
                                event.getDragboard().hasString() &&
                                (viewModel[row][col] == null ||
                                        viewModel[row][col].getText().length() != 1)) {
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
                viewModel[i][j].setText("");
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
        if (isValidMove())
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
    private boolean isValidMove()
    {
        boolean valid = true;

        // Determine if the play is vertical or horizontal.
        boolean playWasHorizontal = changed_tile_coordinates.stream()
                .filter(x -> !x.getKey().equals(changed_tile_coordinates.get(0).getKey())).count() == 0;

        boolean playWasVertical = changed_tile_coordinates.stream()
                .filter(x -> !x.getValue().equals(changed_tile_coordinates.get(0).getValue())).count() == 0;

        valid = valid && (playWasVertical || playWasHorizontal);

        System.out.println("Checkpt 1: valid? " + valid);
        if (playWasVertical){
            valid = valid && partOfValidVerticalWord(changed_tile_coordinates.get(0));

            // Ensure that the word is indeed connected vertically (and is not just two disjoint words in the same col)
            int min_row_ind = changed_tile_coordinates.stream().map(Pair::getKey).reduce((x, y) -> x < y ? x : y).get();
            int max_row_ind = changed_tile_coordinates.stream().map(Pair::getKey).reduce((x, y) -> x > y ? x : y).get();

            valid = valid && IntStream.rangeClosed(min_row_ind, max_row_ind)
                    .mapToObj(
                        i -> viewModel[i][changed_tile_coordinates.get(0).getValue()] != null
                            && viewModel[i][changed_tile_coordinates.get(0).getValue()].getText().length() == 1)
                    .reduce((x, y) -> x && y).get();

            // Ensure that each letter contributes to a horizontal word correctly.
            valid = valid && changed_tile_coordinates.stream().
                    map(this::partOfValidHorizontalWord).reduce((x, y) -> x && y).get();

        }
        else
        {
            valid = valid && partOfValidHorizontalWord(changed_tile_coordinates.get(0));

            // Ensure that the word is indeed connected horizontally (and is not just two disjoint words in the same row)
            int min_col_ind = changed_tile_coordinates.stream().map(Pair::getValue).reduce((x, y) -> x < y ? x : y).get();
            int max_col_ind = changed_tile_coordinates.stream().map(Pair::getValue).reduce((x, y) -> x > y ? x : y).get();
            valid = valid && IntStream.rangeClosed(min_col_ind, max_col_ind)
                    .mapToObj(
                        j -> viewModel[changed_tile_coordinates.get(0).getKey()][j] != null
                            && viewModel[changed_tile_coordinates.get(0).getKey()][j].getText().length() == 1)
                    .reduce((x, y) -> x && y).get();

            // Ensure that each letter contributes to a vertical word correctly.
            valid = valid && changed_tile_coordinates.stream().
                    map(this::partOfValidVerticalWord).reduce((x, y) -> x && y).get();
        }

        System.out.println("Checkpt 2: valid? " + valid);

        if (isFirstTurn)
        {
            valid = valid
                    && changed_tile_coordinates.indexOf(new Pair(7, 7)) != -1
                    && changed_tile_coordinates.size() >= 2;
        }
        else
        {
            // All subsequent turns must consist of a play that is vertically or horizontally adjacent to at
            // least one other letter of a word that existed before this turn.
            valid = valid && changed_tile_coordinates.stream().filter(x -> {
                int r = x.getKey();
                int c = x.getValue();
                return (r > 0 && mainModel[r-1][c] != '\0')
                        || (r < 14 && mainModel[r+1][c] != '\0')
                        || (c > 0 && mainModel[r][c-1] != '\0')
                        || (c < 14 && mainModel[r][c+1] != '\0');
            }).count() > 0;
        }
        System.out.println("Checkpt 3: valid? " + valid);

        return valid;

    }

    /**
     * Builds the vertical word in which the letter at the provided coordinate in the ViewModel participates.
     * @param pair
     * @return the word itself, as well as the starting index of the word
     */
    private Pair<String, Integer> buildVerticalWordForCoordinate(Pair<Integer, Integer> pair)
    {
        StringBuilder sb = new StringBuilder();
        int row = pair.getKey();
        int col = pair.getValue();

        OptionalInt top_exclusive = IntStream.iterate(row, i -> i - 1)
                .limit(row + 1)
                .filter(r -> !(viewModel[r][col] != null && viewModel[r][col].getText().length() == 1))
                .findFirst();
        OptionalInt bot_exclusive = IntStream.range(row + 1, 15)
                .filter(r -> !(viewModel[r][col] != null && viewModel[r][col].getText().length() == 1))
                .findFirst();
        int top_exc = top_exclusive.isPresent() ? top_exclusive.getAsInt(): -1;
        int bot_exc = bot_exclusive.isPresent() ? bot_exclusive.getAsInt(): 15;
        Boolean endOfWordSeen = false;
        IntStream.range(top_exc + 1, bot_exc)
                .forEach(r ->
                        sb.append(viewModel[r][col].getText().charAt(0))
                );

        return new Pair<>(sb.toString(), top_exc + 1);
    }

    /**
     * Builds the horizontal word in which the letter at the provided coordinate in the ViewModel participates.
     * @param pair
     * @return the word itself, as well as the starting index of the word
     */
    private Pair<String, Integer> buildHorizontalWordForCoordinate(Pair<Integer, Integer> pair)
    {
        StringBuilder sb = new StringBuilder();
        int row = pair.getKey();
        int col = pair.getValue();

        OptionalInt left_exclusive = IntStream.iterate(col, i -> i - 1)
                .limit(col + 1)
                .filter(c -> !(viewModel[row][c] != null && viewModel[row][c].getText().length() == 1))
                .findFirst();
        OptionalInt right_exclusive = IntStream.range(col + 1, 15)
                .filter(c -> !(viewModel[row][c] != null && viewModel[row][c].getText().length() == 1))
                .findFirst();
        int left_exc = left_exclusive.isPresent() ? left_exclusive.getAsInt(): -1;
        int right_exc = right_exclusive.isPresent() ? right_exclusive.getAsInt(): 15;
        Boolean endOfWordSeen = false;
        IntStream.range(left_exc + 1, right_exc)
                .forEach(c ->
                        sb.append(viewModel[row][c].getText().charAt(0))
                );

        return new Pair<>(sb.toString(), left_exc + 1);
    }

    /**
     * Is the letter that is at location "pair" in the GUI part of a valid word in the vertical direction?
     * @param pair an (x,y) coordinate in the GUI denoting a location of an inserted tile
     */
    private boolean partOfValidVerticalWord(Pair<Integer, Integer> pair) {
        String verticalWord = buildVerticalWordForCoordinate(pair).getKey();
        // Now that we have the vertical word we've formed, let's see whether it is valid.
        if (verticalWord.length() == 1)
            return true;
        TrieNode tn = trie.getNodeForPrefix(verticalWord.toLowerCase());
        return (tn != null && tn.isWord());
    }

    /**
     * Is the letter that is at location "pair" in the GUI part of a valid word in the vertical direction?
     * @param pair an (x,y) coordinate in the GUI denoting a location of an inserted tile
     */
    private boolean partOfValidHorizontalWord(Pair<Integer, Integer> pair) {
        String horizontalWord = buildHorizontalWordForCoordinate(pair).getKey();

        // Now that we have the vertical word we've formed, let's see whether it is valid.
        if (horizontalWord.length() == 1)
            return true;
        TrieNode tn = trie.getNodeForPrefix(horizontalWord.toLowerCase());
        return (tn != null && tn.isWord());
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
            score += scoreVertical(changed_tile_coordinates.get(0));
            score += changed_tile_coordinates.stream().map(this::scoreHorizontal).reduce((x,y)->x+y).get();
        }
        else
        {
            score += scoreHorizontal(changed_tile_coordinates.get(0));
            score += changed_tile_coordinates.stream().map(this::scoreVertical).reduce((x,y)->x+y).get();
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
        // Step 5: clear changed_tiles list
        changed_tile_coordinates.clear();

        // TODO Step 6: let CPU make its move

    }

    /**
     *
     * @param pair
     * @return score
     */
    private int scoreHorizontal(Pair<Integer, Integer> pair) {

        int row = pair.getKey();
        int col = pair.getValue();

        Pair<String, Integer> p = buildHorizontalWordForCoordinate(pair);
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
                        int letterScore = TileHelper.scoreCharacter(viewModel[row][c].getText().charAt(0));
                        if (board_cells[row][c].getStyleClass().size() > 0 && mainModel[row][c] == '\0') {
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

    private int scoreVertical(Pair<Integer, Integer> pair) {
        int row = pair.getKey();
        int col = pair.getValue();

        Pair<String, Integer> p = buildVerticalWordForCoordinate(pair);
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
                                    int letterScore = TileHelper.scoreCharacter(viewModel[r][col].getText().charAt(0));
                                    if (board_cells[r][col].getStyleClass().size() > 0 && mainModel[r][col] == '\0') {
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

    private void computeCrossCheckSets(int startRowInclusive, int endRowExclusive, int startColInclusive, int endColExclusive) {
        IntStream.range(startRowInclusive, endRowExclusive).forEach(i -> {
            IntStream.range(startColInclusive, endColExclusive).forEach(j -> {
                crossCheckSets[i][j].clear();
                StringBuilder verticalPrefixToThisSquare = new StringBuilder();
                for (int h = 0; h < i; h++)
                {
                    if ((int) mainModel[h][j] != 0)
                        verticalPrefixToThisSquare.append(mainModel[h][j]);
                }
                TrieNode prefixNode = trie.getNodeForPrefix(verticalPrefixToThisSquare.toString());
                verticalPrefixToThisSquare.chars().forEach(x -> System.out.println(x));
                if (prefixNode != null)
                {
                    StringBuilder verticalSuffixToThisSquare = new StringBuilder();
                    for (int h = i + 1; h < 15; h++)
                    {
                        if((int) mainModel[h][j] != 0)
                            verticalSuffixToThisSquare.append(mainModel[h][j]);
                    }
                    for (char c: prefixNode.getOutgoingEdges().keySet())
                    {
                        if (prefixNode.getNodeForPrefix(c + verticalSuffixToThisSquare.toString(), 0) != null) {
                            crossCheckSets[i][j].add(c);
                        }
                    }
                }
            });
        });
    }
}
