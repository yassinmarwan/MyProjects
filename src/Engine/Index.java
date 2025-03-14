package Engine;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Vector;

import bplustree.BPlusTree;
import bplustree.Key;

public class Index implements Serializable{
    private BPlusTree tree;
    private String indexName;

    public Index(String indexName, int order) {
        this.setIndexName(indexName);
        tree = new BPlusTree(order);
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public void delete(Object key, String pageName) throws DBAppException {
        tree.delete(new Key(key), pageName);
        // Write the updated index back to memory
        try {
            FileAccess.writeIndex(this);
        } catch (IOException e) {
            throw new DBAppException("Error writing index to memory: " + e.getMessage());
        }

    }
    
    public void delete(Object key) throws DBAppException {
        tree.delete(new Key(key));
        // Write the updated index back to memory
        try {
            FileAccess.writeIndex(this);
        } catch (IOException e) {
            throw new DBAppException("Error writing index to memory: " + e.getMessage());
        }
    }

    public void insert(Object key, String pageName) throws DBAppException {
        tree.insert(new Key(key), pageName);
        // Write the updated index back to memory
        try {
            FileAccess.writeIndex(this);
        } catch (IOException e) {
            throw new DBAppException("Error writing index to memory: " + e.getMessage());
        }

    }

    public void update(Object key, String oldPageName, String newPageName) throws DBAppException {
        tree.update(new Key(key), oldPageName, newPageName);
        // Write the updated index back to memory
        try {
            FileAccess.writeIndex(this);
        } catch (IOException e) {
            throw new DBAppException("Error writing index to memory: " + e.getMessage());
        }
    }

    public Vector<String> search(Object key) {
		Vector<String> searchResult = tree.search(new Key(key));    //store searched results in a vector 
		HashSet<String> uniqueSet = new HashSet<>(searchResult);    //removes duplicates in a hashset
		return new Vector<>(uniqueSet);             //return a new vector with unique set 
    }

    public Vector<String> searchWithBounds(Object bound, String operator) {
        ArrayList<Vector<String>> searchResults = tree.searchWithBounds(new Key(bound), operator);
        HashSet<String> uniquePages = new HashSet<>();  //removes duplicates in a hashset
        System.out.println(searchResults);
        // Flatten the search results and add them to the set
        for (Vector<String> result : searchResults) {
            uniquePages.addAll(result);
        }

        // Convert the set to a vector
        return new Vector<>(uniquePages);
    }


    @Override
    public String toString() {
        return tree.toString();
    }

    public static void main(String[] args) throws DBAppException {
        // Create an Index object
        Index index = new Index("SampleIndex", 3);

        // Perform insertions into the B+ tree
        index.insert(1.2, "page3");
        index.insert(1.2, "page1");
        index.insert(4.2, "page2");
        index.insert(5.2, "page2");
        index.insert(2.2, "page2");
        index.insert(3.2, "page2");
        System.out.println(index.searchWithBounds(4.3, "<="));
           

        // Display the B+ tree
        System.out.println("B+ Tree:");
        System.out.println(index.tree.toString());
    }
}
