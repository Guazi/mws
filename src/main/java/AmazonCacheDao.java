import com.amazonservices.mws.products.model.Product;

public class AmazonCacheDao {
    String upc;
    Product amazon;

    public String getUpc() {
        return upc;
    }

    public void setUpc(String upc) {
        this.upc = upc;
    }

    public Product getAmazon() {
        return amazon;
    }

    public void setAmazon(Product amazon) {
        this.amazon = amazon;
    }

}
