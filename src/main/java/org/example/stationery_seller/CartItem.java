package org.example.stationery_seller;

public class CartItem {

    private Item item;
    private int quantity;

    public CartItem(Item item, int quantity){
        this.item = item;
        this.quantity = quantity;
    }

    public Item getItem() {
        return item;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setItem(Item item){
        this.item = item;
    }
    public void setQuantity(int quantity){
        this.quantity = quantity;
    }
}
