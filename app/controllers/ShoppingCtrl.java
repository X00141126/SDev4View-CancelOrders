package controllers;

import play.mvc.*;
import play.data.*;
import javax.inject.Inject;
import java.util.*;

import views.html.*;
import play.db.ebean.Transactional;
import play.api.Environment;

// Import models
import models.users.*;
import models.products.*;
import models.shopping.*;
import java.util.Calendar;


// Import security controllers
import controllers.security.*;

// Authenticate user
@Security.Authenticated(Secured.class)
// Authorise user (check if user is a customer)
@With(CheckIfCustomer.class)

public class ShoppingCtrl extends Controller {


    /** Dependency Injection **/

    /** http://stackoverflow.com/questions/15600186/play-framework-dependency-injection **/
    private FormFactory formFactory;

    /** http://stackoverflow.com/a/37024198 **/
    private Environment env;

    /** http://stackoverflow.com/a/10159220/6322856 **/
    @Inject
    public ShoppingCtrl(Environment e, FormFactory f) {
        this.env = e;
        this.formFactory = f;
    }


    
    // Get a user - if logged in email will be set in the session
	private Customer getCurrentUser() {
		return (Customer)User.getLoggedIn(session().get("email"));
	}

    @Transactional
    public Result showBasket() {
        return ok(basket.render(getCurrentUser()));
    }
    
    // Add item to customer basket
    @Transactional
    public Result addToBasket(Long id) {
        
        // Find the product
        Product p = Product.find.byId(id);
        
        // Get basket for logged in customer
        Customer customer = (Customer)User.getLoggedIn(session().get("email"));
        if(p.decrementStock()){
        // Check if item in basket
        if (customer.getBasket() == null) {
            // If no basket, create one
            customer.setBasket(new Basket());
            customer.getBasket().setCustomer(customer);
            customer.update();
        }
        p.update();
        // Add product to the basket and save
        customer.getBasket().addProduct(p);
        customer.update();
    }
        // Show the basket contents     
        return ok(basket.render(customer));
    }
    
    // Add an item to the basket
    @Transactional
    public Result addOne(Long itemId, Long pid) {
        
        // Get the order item
        OrderItem item = OrderItem.find.byId(itemId);
        // Find the product
        Product p = Product.find.byId(pid);
        
        if(p.decrementStock()){
                    // Increment quantity
        item.increaseQty();
        // Save
        item.update();
        p.update();
        } else{
            flash("success", "Sorry, no more of these products left");
        }


        // Show updated basket
        return redirect(routes.ShoppingCtrl.showBasket());
    }

    @Transactional
    public Result removeOne(Long itemId) {
        
        // Get the order item
        OrderItem item = OrderItem.find.byId(itemId);
        // Get user
        Customer c = getCurrentUser();
        // Call basket remove item method
        c.getBasket().removeItem(item);
        c.getBasket().update();
        // back to basket
        return ok(basket.render(c));
    }

    // Empty Basket
    @Transactional
    public Result emptyBasket() {
        
        Customer c = getCurrentUser();
        c.getBasket().removeAllItems();
        c.getBasket().update();
        
        return ok(basket.render(c));
    }

    @Transactional
    public Result placeOrder() {
        Customer c = getCurrentUser();
        
        // Create an order instance
        ShopOrder order = new ShopOrder();
        
        // Associate order with customer
        order.setCustomer(c);
        
        // Copy basket to order
        order.setItems(c.getBasket().getBasketItems());
        
        // Save the order now to generate a new id for this order
        order.save();
       
       // Move items from basket to order
        for (OrderItem i: order.getItems()) {
            // Associate with order
            i.setOrder(order);
            // Remove from basket
            i.setBasket(null);
            // update item
            i.update();
        }
        
        // Update the order
        order.update();
        
        // Clear and update the shopping basket
        c.getBasket().setBasketItems(null);
        c.getBasket().update();
        
        // Show order confirmed view
        return ok(orderConfirmed.render(c, order));
    }
    
    // View an individual order
    @Transactional
    public Result viewOrder(long id) {
        ShopOrder order = ShopOrder.find.byId(id);
        return ok(orderConfirmed.render(getCurrentUser(), order));
    }

    @Transactional
    public Result viewOrders() {
        List<ShopOrder> orders = getCurrentUser().getOrders();
        return ok(viewOrders.render(getCurrentUser(), orders));
    }

    @Transactional
    public Result cancelOrder(Long id) {
        List<ShopOrder> orders = getCurrentUser().getOrders();        
        ShopOrder order = ShopOrder.find.byId(id);
        Calendar now = Calendar.getInstance();

        long miliSecondForDate1 = order.getOrderDate().getTimeInMillis();
        long miliSecondForDate2 = now.getTimeInMillis();
        long diffInMilis = miliSecondForDate2 - miliSecondForDate1;
        long diffInSecond = diffInMilis / 1000;

        if (diffInSecond > 3600) {
            flash("error", "Sorry, it's to late to cancel this order");
            return ok(viewOrders.render(getCurrentUser(), orders));
        }
        else {
            order.delete();            
            flash("success", "Your order has been cancelled");
            return ok(viewOrders.render(getCurrentUser(), orders));            
        }

    }

}