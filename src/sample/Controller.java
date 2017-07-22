package sample;

import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import util.Trie;

import java.net.URL;
import java.util.*;
import java.util.stream.IntStream;

public class Controller implements Initializable {

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

    private char[] playerHand, cpuHand;

    private HashSet[][] crossCheckSets;

    // Work-around for an edge case with JavaFX drag-n-drop implementation
    private static boolean wasDropSuccessful = false;

    private Trie trie;

    /**
     * Runs initialization routines right after the view loads.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        trie = new Trie();
        crossCheckSets = new HashSet[15][15];
        IntStream.range(0, 15).forEach(i -> IntStream.range(0, 15).forEach(j -> {
            crossCheckSets[i][j] = new HashSet<>();
            for(char alphabet = 'a'; alphabet <= 'z';alphabet++) {
                crossCheckSets[i][j].add(alphabet);
            }
        }));
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
        playerHand = new char[7];
        cpuHand = new char[7];

        // Distribute the starting racks (hereafter referenced as "hands") to the computer and the player.
        for (int i = 0; i < 7; i++)
        {
            playerHand[i] = tilesRemaining.remove();
            cpuHand[i] = tilesRemaining.remove();
        }

        // Display the player's hand as stackpanes in the HBox in the bottom of the borderpane layout.
        for (int i = 0; i < 7 ; i++)
        {
            addTileToUserHand(playerHand[i]);
        }
    }

    /**
     * Recall all tiles placed on the board this turn to the player's hand.
     */
    public void recallTiles()
    {
        for (int i = 0 ; i < 15; i++)
        {
            for (int j = 0; j < 15; j++)
            {
                if (viewModel[i][j] != null && viewModel[i][j].getText().length() == 1
                        && viewModel[i][j].getText().charAt(0) != mainModel[i][j])
                {
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
                }
            }
        }
    }

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
            if (!lettersNotInHand.containsKey(c))
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
            makePlayerMove();
        }
        else
        {
            statusMessage.setText("That didn't work out so well.");
            statusMessage.getStyleClass().add("error-text");
        }
    }

    public boolean isValidMove()
    {
        return false;
    }

    public void makePlayerMove()
    {
        // propagate viewModel to model
        // increment player score
        // remove X tiles from the rack
        // take as many of X tiles from the bag as you can and give them to the player
        // let CPU make its move
    }

    public void computeCrossCheckSets() {

    }



}
