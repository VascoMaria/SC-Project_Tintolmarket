/**
 * @author Grupo 51
 * @author Henrique Catarino - 56278
 * @author Miguel Nunes - 56338
 * @author Vasco Maria - 56374
 */

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class Wine {
	
    private String name;
    private File image;
    private double rating;
    private int numberRaters;
    private HashMap<String, ArrayList<Integer>> sellers;

    public Wine(String name , File image) {
    	this.name = name;
    	this.image = image;
    	this.rating = 0;
    	this.numberRaters = 0;
    	this.sellers = new HashMap<>();
    }
    
    public Wine(String name , File image, double rating, int numberRaters) {
    	this.name = name;
    	this.image = image;
    	this.rating = rating;
    	this.numberRaters = numberRaters;
    	this.sellers = new HashMap<>();
    }
    
    public String getName() {
    	return this.name;
    }
    
    public File getImage() {
    	return image;
    }
   
    public double getRating() {
    	return rating ;
    }
    
    public int getNumberRaters() {
    	return numberRaters;
    }
    
    public HashMap<String, ArrayList<Integer>> getSales() {
    	return sellers;
    }
    
    public ArrayList<Integer> getSale(String seller) {
    	return sellers.get(seller);
    }
    
    public void sellWine(String seller, int quantity, int value) {
    	ArrayList<Integer> list = new ArrayList<>();
    	list.add(quantity);
    	list.add(value);
    	sellers.put(seller, list);
    }
    
    public void buyWine(String seller, int quantity) {
    	ArrayList<Integer> sale = sellers.get(seller);
    	if (quantity == sale.get(0)) {
    		sellers.remove(seller);
    	}
    	else {
    		ArrayList<Integer> newSale = new ArrayList<>();
    		newSale.add(sale.get(0) - quantity);
    		newSale.add(sale.get(1));
    		sellers.put(seller, newSale);
    	}
    }
    
    public double rate(int rating) {
    	this.rating = (this.rating * numberRaters + rating) / (numberRaters + 1);
    	numberRaters += 1;
    	return this.rating;
    }
    
    
    
    
}