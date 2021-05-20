import com.amazonservices.mws.products.model.Product;

import java.util.Date;

public class AsinHistoryDao {
    public String getAsin() {
        return asin;
    }

    public void setAsin(String asin) {
        this.asin = asin;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String asin;
    public Date date;
    public Product product;
}
