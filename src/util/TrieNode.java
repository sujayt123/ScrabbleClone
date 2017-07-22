package util;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by sujay on 7/21/17.
 */
public class TrieNode {

    private Map<Character, TrieNode> outgoingEdges;
    public static int numberInsertions = 0;
    private boolean isWord;

    public TrieNode(boolean isWord)
    {
        this.isWord = isWord;
        outgoingEdges = new HashMap<>();
    }

    public Map<Character, TrieNode> getOutgoingEdges() {
        return outgoingEdges;
    }

    public boolean isWord() {
        return isWord;
    }

    void insertWord(String s, int index) {
        numberInsertions++;
        if (index < s.length())
        {
                TrieNode child = outgoingEdges.containsKey(s.charAt(index)) ?
                        outgoingEdges.get(s.charAt(index)) :
                        new TrieNode(false);
                outgoingEdges.put(s.charAt(index), child);
                child.insertWord(s, index + 1);
        }
        else {
            isWord = true;
        }
    }
}
