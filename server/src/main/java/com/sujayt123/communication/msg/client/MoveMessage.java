package com.sujayt123.communication.msg.client;

import com.sujayt123.communication.msg.Message;

/**
 * Created by sujay on 8/14/17.
 */
public class MoveMessage extends Message {
    private String[][] boardAfterAttemptedMove;

    public MoveMessage(String[][] boardAfterAttemptedMove) {
        this.boardAfterAttemptedMove = boardAfterAttemptedMove;
    }

    public String[][] getBoardAfterAttemptedMove() {
        return boardAfterAttemptedMove;
    }

    public void setBoardAfterAttemptedMove(String[][] boardAfterAttemptedMove) {
        this.boardAfterAttemptedMove = boardAfterAttemptedMove;
    }
}