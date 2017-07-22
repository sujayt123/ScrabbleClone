package scrabble;

import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.util.Pair;
import util.Score;
import util.Trie;
import util.TrieNode;

import java.net.URL;
import java.util.*;
import java.util.stream.IntStream;

public class Controller implements Initializable {

    // TODO Refactor redundant code into reusable segments and avoid having a jumbled mix of loops and streams everywhere.

    /* Access to the GUI representation of the board. Useful for defining drag-and-drop events. */
    private StackPane[][] board_cells = new StackPane[15][15];

    /* Access to the GUI GridPane.*/
    @FXML
    private GridPane gridPane;

    @FXML
    private HBox playerHandHBox;

    @FXML
    private Text playerScore;

    @FXML
    private Text cpuScore;

    @FXML
    private Text statusMessage;

    /* The data currently represented on the screen. Propagates to main model at certain points in gameplay.
     * Bound to the text stored in each board_cell, if it exists. Any non-existing text states are created lazily.
     */
    private Text [][] viewModel = new Text[15][15];

    /* The most recently accepted state of the board. State may change upon successful user or computer move. */
    private char [][] mainModel = new char[15][15];

    private Queue<Character> tilesRemaining;

    private List<Character> playerHand, cpuHand;

    private List<Pair<Integer, Integer>> changed_tile_coordinates;

    private HashSet[][] crossCheckSets;

    // Work-around for an edge case with JavaFX drag-n-drop implementation
    private static boolean wasDropSuccessful = false;

    private Trie trie;

    // Flag to indicate if it's the first turn of the game or not.
    private boolean isFirstTurn = true;

    /**
     * Runs initialization routines right after the view loads.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        trie = new Trie();
        changed_tile_coordinates = new ArrayList<>();
        crossCheckSets = new HashSet[15][15];
        IntStream.range(0, 15).forEach(i -> IntStream.range(0, 15).forEach(j -> {
            crossCheckSets[i][j] = new HashSet<>();
        }));

        computeCrossCheckSets(0, 15, 0, 15);

        /*
         * Initialize the bindings to the viewmodel (view's understanding of board) and the board cells housing them.
         * Also mark the GridPane cells as valid targets for a drag n' drop motion.
         */
        gridPane.getChildren().forEach((child)->{
            if (child instanceof StackPane)
            {
                final int row = gridPane.getRowIndex(child);
                final int col = gridPane.getColumnIndex(child);
                board_cells[row][col] = (StackPane) child;
                ((StackPane) child).getChildren().forEach((item_in_stackpane) -> {
                    if (item_in_stackpane instanceof Text) {
                        viewModel[row][col] = (Text) item_in_stackpane;
                    }
                });
                child.setOnDragOver(new EventHandler <DragEvent>() {
                    public void handle(DragEvent event) {
                        /* data is dragged over the target */
                        System.out.println("onDragOver");

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
                    }
                });
                child.setOnDragEntered(new EventHandler<DragEvent>() {
                    public void handle(DragEvent event) {
                        /* the drag-and-drop gesture entered the target */
                        /* show to the user that it is an actual gesture target */
                        if (event.getGestureSource() != child &&
                                event.getDragboard().hasString() &&
                                (viewModel[row][col] == null ||
                                        viewModel[row][col].getText().length() != 1)) {
                            child.setStyle("-fx-border-color: darkblue; -fx-border-width: 3;");
                        }

                        event.consume();
                    }
                });
                child.setOnDragExited(new EventHandler <DragEvent>() {
                    public void handle(DragEvent event) {
                /* mouse moved away, remove the graphical cues */
                        child.setStyle("-fx-border-width: 0;");
                        event.consume();
                    }
                });

                //TODO What should the board do when it receives a tile? Rigorously define the procedure
                child.setOnDragDropped(new EventHandler <DragEvent>() {
                    public void handle(DragEvent event) {
                        /* data dropped */
                        System.out.println("onDragDropped");
                        /* if there is a string data on dragboard, read it and use it */
                        Dragboard db = event.getDragboard();
                        boolean success = false;
                        if (db.hasString()) {
                            // Change the text color of the pane to Black if needed.

                            // Creates the text element in the view model at that position if it doesn't exist.
                            if (viewModel[row][col] == null)
                            {
                                viewModel[row][col] = new Text(db.getString());
                                ((StackPane)child).getChildren().add(viewModel[row][col]);
                            }
                            else
                            {
                                viewModel[row][col].getStyleClass().add("black-text");
                            }
                            viewModel[row][col].setText(db.getString());


                            success = true;
                        }
                        /* let the source know whether the string was successfully
                         * transferred and used */
                        event.setDropCompleted(success);
                        wasDropSuccessful = true;
                        changed_tile_coordinates.add(new Pair(row, col));

                        event.consume();
                    }
                });
            }
        });

        List<Character> tileList =
                Arrays.asList( 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'E', 'E',
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
        tilesRemaining = new ArrayDeque<>(tileList);

        // Prepare each player to receive tiles.
        playerHand = new ArrayList<>();
        cpuHand = new ArrayList<>();

        // Distribute the starting racks (hereafter referenced as "hands") to the computer and the player.
        for (int i = 0; i < 7; i++)
        {
            playerHand.add(tilesRemaining.remove());
            cpuHand.add(tilesRemaining.remove());
        }

        // Display the player's hand as stackpanes in the HBox in the bottom of the borderpane layout.
        for (int i = 0; i < 7 ; i++)
        {
            addTileToUserHand(playerHand.get(i));
        }
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
        s.setOnDragDetected(new EventHandler<MouseEvent>() {
            public void handle(MouseEvent event) {
                            /* drag was detected, start drag-and-drop gesture*/
                System.out.println("onDragDetected");

                            /* allow any transfer mode */
                Dragboard db = s.startDragAndDrop(TransferMode.MOVE);

                            /* put a string on dragboard */
                ClipboardContent content = new ClipboardContent();
                content.putString("" + letter);
                db.setContent(content);

                event.consume();
            }
        });

        s.setOnDragDone(new EventHandler <DragEvent>() {
            public void handle(DragEvent event) {
                /* the drag-and-drop gesture ended */
                System.out.println("onDragDone");
                /* if the data was successfully moved, clear it */
                if (event.getTransferMode() == TransferMode.MOVE && wasDropSuccessful) {
                    playerHandHBox.getChildren().remove(s);
                    wasDropSuccessful = false;
                }
                event.consume();
            }
        });
    }

    /**
     * Optional method to ensure the hand is consistent with what's displayed on the screen.
     * JavaFX appears to have some edge cases with drag-n-drop that may require this method
     * or other solutions (like flags) to address.
     */
    public void ensureHandConsistentWithGUI()
    {
        Map<Character, Integer> lettersNotInHand = new HashMap<>();
        for (char c: playerHand)
        {
            if (!lettersNotInHand.containsKey(c) && c >= 'A' && c <= 'Z')
            {
                lettersNotInHand.put(c, 0);
            }
            lettersNotInHand.put(c, lettersNotInHand.get(c) + 1);
        }

        playerHandHBox.getChildren().forEach((stackpane) -> {
            ((StackPane)stackpane).getChildren().forEach((text) -> {
                char c = ((Text)text).getText().charAt(0);
                lettersNotInHand.put(c, lettersNotInHand.get(c) - 1);
            });
        });

        System.out.println(lettersNotInHand.toString());

        lettersNotInHand.forEach((k, v) -> {
            for (int i = 0; i < v; i++) {
                addTileToUserHand(k);
            }
        });
    }

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

    //TODO Fix in the morning. Take a look at the streams and see whether the filtering and checks are done appropriately.
    boolean isValidMove()
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
            int min_row_ind = changed_tile_coordinates.stream().map((x)->x.getKey()).reduce((x, y) -> x < y ? x : y).get();
            int max_row_ind = changed_tile_coordinates.stream().map((x)->x.getKey()).reduce((x, y) -> x > y ? x : y).get();

            valid = valid && IntStream.rangeClosed(min_row_ind, max_row_ind).mapToObj(
                    i -> viewModel[i][changed_tile_coordinates.get(0).getValue()] != null
                            && viewModel[i][changed_tile_coordinates.get(0).getValue()].getText().length() == 1)
                    .reduce((x, y) -> x && y).get();

            // Ensure that each letter contributes to a horizontal word correctly.
            for (Pair pair : changed_tile_coordinates)
            {
                valid = valid && partOfValidHorizontalWord(pair);
            }
        }


        else
        {
            valid = valid && partOfValidHorizontalWord(changed_tile_coordinates.get(0));

            // Ensure that the word is indeed connected horizontally (and is not just two disjoint words in the same row)
            int min_col_ind = changed_tile_coordinates.stream().map((x)->x.getValue()).reduce((x, y) -> x < y ? x : y).get();
            int max_col_ind = changed_tile_coordinates.stream().map((x)->x.getValue()).reduce((x, y) -> x > y ? x : y).get();
            valid = valid && IntStream.rangeClosed(min_col_ind, max_col_ind).mapToObj(
                    j -> viewModel[changed_tile_coordinates.get(0).getKey()][j] != null
                            && viewModel[changed_tile_coordinates.get(0).getKey()][j].getText().length() == 1)
                    .reduce((x, y) -> x && y).get();

            // Ensure that each letter contributes to a vertical word correctly.

            for (Pair pair : changed_tile_coordinates)
            {
                valid = valid && partOfValidVerticalWord(pair);
            }
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
                if (r > 0 && mainModel[r-1][c] != '\0')
                    return true;
                if (r < 14 && mainModel[r+1][c] != '\0')
                    return true;
                if (c > 0 && mainModel[r][c-1] != '\0')
                    return true;
                if (c < 14 && mainModel[r][c+1] != '\0')
                    return true;
                return false;
            }).count() > 0;
        }
        System.out.println("Checkpt 3: valid? " + valid);

        return valid;

    }

    /**
     * Is the letter that is at location "pair" in the GUI part of a valid word in the vertical direction?
     * @param pair an (x,y) coordinate in the GUI denoting a location of an inserted tile
     */
    private boolean partOfValidVerticalWord(Pair<Integer, Integer> pair) {
        StringBuilder sb = new StringBuilder();
        int row = pair.getKey();
        int col = pair.getValue();
        sb.append(viewModel[row][col].getText().charAt(0));
        for (int r = row - 1; r >= 0; r--)
        {
            if (viewModel[r][col] != null && viewModel[r][col].getText().length() == 1)
            {
                sb.insert(0, viewModel[r][col].getText().charAt(0));
            }
            else break;
        }
        for (int r = row + 1; r < 15; r++)
        {
            if (viewModel[r][col] != null && viewModel[r][col].getText().length() == 1)
            {
                sb.append(viewModel[r][col].getText().charAt(0));
            }
            else break;
        }
        // Now that we have the vertical word we've formed, let's see whether it is valid.
        if (sb.length() == 1)
            return true;
        TrieNode tn = trie.getNodeForPrefix(sb.toString().toLowerCase());
        return (tn != null && tn.isWord());
    }

    /**
     * Is the letter that is at location "pair" in the GUI part of a valid word in the vertical direction?
     * @param pair an (x,y) coordinate in the GUI denoting a location of an inserted tile
     */
    private boolean partOfValidHorizontalWord(Pair<Integer, Integer> pair) {
        StringBuilder sb = new StringBuilder();
        int row = pair.getKey();
        int col = pair.getValue();
        sb.append(viewModel[row][col].getText().charAt(0));
        for (int c = col - 1; c >= 0; c--)
        {
            if (viewModel[row][c] != null && viewModel[row][c].getText().length() == 1)
            {
                sb.insert(0, viewModel[row][c].getText().charAt(0));
            }
            else break;
        }
        for (int c = col + 1; c < 15; c++)
        {
            if (viewModel[row][c] != null && viewModel[row][c].getText().length() == 1)
            {
                sb.append(viewModel[row][c].getText().charAt(0));
            }
            else break;
        }

        // Now that we have the vertical word we've formed, let's see whether it is valid.
        if (sb.length() == 1)
            return true;
        TrieNode tn = trie.getNodeForPrefix(sb.toString().toLowerCase());

        return (tn != null && tn.isWord());
    }

    public void makePlayerMove()
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
            score += changed_tile_coordinates.stream().map(x -> scoreHorizontal(x)).reduce((x,y)->x+y).get();
        }
        else
        {
            score += scoreHorizontal(changed_tile_coordinates.get(0));
            score += changed_tile_coordinates.stream().map(x -> scoreVertical(x)).reduce((x,y)->x+y).get();
        }

        playerScore.setText("Player Score:" + score);

        // Step 2: propagate viewModel to model
        changed_tile_coordinates.forEach(pair -> {
            int row = pair.getKey();
            int col = pair.getValue();
            mainModel[row][col] = viewModel[row][col].getText().charAt(0);
        });

        // Step 3: remove X tiles from the rack
        changed_tile_coordinates.forEach(p -> {
            int row = p.getKey();
            int col = p.getValue();
        });

        // Step 4: take as many of X tiles from the bag as you can and give them to the player
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

        // If there is no word (of 2 or more letters) formed, return 0 immediately.
        int count = 0;

        int score = 0;
        int letterScore;
        int dw_count = 0;
        int tw_count = 0;

        int row = pair.getKey();
        int col = pair.getValue();
        for (int c = col; c >= 0; c--)
        {
            if (viewModel[row][c] != null && viewModel[row][c].getText().length() == 1)
            {
                letterScore = Score.scoreCharacter(viewModel[row][c].getText().charAt(0));
                // if the bonus wasn't used in a previous turn, it can be used now.
                if (board_cells[row][c].getStyleClass().size() > 0 && mainModel[row][c] == '\0')
                {
                    switch(board_cells[row][c].getStyleClass().get(0).substring(0, 2))
                    {
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
                score += letterScore;
            }
            else break;
            count++;
        }
        for (int c = col + 1; c < 15; c++)
        {
            if (viewModel[row][c] != null && viewModel[row][c].getText().length() == 1)
            {
                letterScore = Score.scoreCharacter(viewModel[row][c].getText().charAt(0));
                // if the bonus wasn't used in a previous turn, it can be used now.
                if (board_cells[row][c].getStyleClass().size() > 0 && mainModel[row][c] == '\0')
                {
                    switch(board_cells[row][c].getStyleClass().get(0).substring(0, 2))
                    {
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
                score += letterScore;
            }
            else break;
            count++;
        }
        if (count <= 1)
        {
            return 0;
        }

        for (int i = 0; i < dw_count; i++)
            score *= 2;
        for (int i = 0; i < tw_count; i++)
            score *= 3;
        System.out.println("score is " + score);
        return score;
    }

    private int scoreVertical(Pair<Integer, Integer> pair) {
        // If there is no word (of 2 or more letters) formed, return 0 immediately.
        int count = 0;

        int score = 0;
        int letterScore;
        int dw_count = 0;
        int tw_count = 0;

        int row = pair.getKey();
        int col = pair.getValue();
        for (int r = row; r >= 0; r--)
        {
            if (viewModel[r][col] != null && viewModel[r][col].getText().length() == 1)
            {
                letterScore = Score.scoreCharacter(viewModel[r][col].getText().charAt(0));
                // if the bonus wasn't used in a previous turn, it can be used now.
                if (board_cells[r][col].getStyleClass().size() > 0 && mainModel[r][col] == '\0')
                {
                    switch(board_cells[r][col].getStyleClass().get(0).substring(0, 2))
                    {
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
                score += letterScore;
            }
            else break;
            count++;
        }
        for (int r = row + 1; r < 15; r++)
        {
            if (viewModel[r][col] != null && viewModel[r][col].getText().length() == 1)
            {
                letterScore = Score.scoreCharacter(viewModel[r][col].getText().charAt(0));
                // if the bonus wasn't used in a previous turn, it can be used now.
                if (board_cells[r][col].getStyleClass().size() > 0 && mainModel[r][col] == '\0')
                {
                    switch(board_cells[r][col].getStyleClass().get(0).substring(0, 2))
                    {
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
                score += letterScore;
            }
            else break;
            count++;
        }
        if (count <= 1)
        {
            return 0;
        }

        for (int i = 0; i < dw_count; i++)
            score *= 2;
        for (int i = 0; i < tw_count; i++)
            score *= 3;
        System.out.println("score is " + score);
        return score;
    }

    public void computeCrossCheckSets(int startRowInclusive, int endRowExclusive, int startColInclusive, int endColExclusive) {
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
