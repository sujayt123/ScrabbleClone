package util;

import java.io.*;

/**
 * Created by sujay on 7/21/17.
 */
public class Trie {

    public TrieNode root;

    public Trie()
    {
        root = new TrieNode(false);

        File file = new File("./src/util/dictionary.txt");
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                // process the line.
                root.insertWord(line, 0);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Read file into trie, " + TrieNode.numberInsertions + " insertions");
    }

    public TrieNode getNodeForPrefix(String s)
    {
        return root.getNodeForPrefix(s, 0);
    }

}
