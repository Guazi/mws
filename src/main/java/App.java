
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.Semaphore;

import com.amazonservices.mws.client.AbstractMwsObject;
import com.amazonservices.mws.products.*;
import com.amazonservices.mws.products.model.*;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import org.apache.commons.collections4.ListUtils;
import org.bson.conversions.Bson;
import me.tongfei.progressbar.*;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;

public class App {

    private static Semaphore maximumRequestQuotaSemaphore;

    private static final String PRICE_HISTORY_COLLECTION_NAME = "price_history";
    private static final String ITEMS_COLLECTION_NAME = "items";
    private static final String RETAILER_DB_NAME = "retailers";

    /**
     * Call the service, log response and exceptions.
     *
     * @param client
     * @param request
     *
     * @return The response.
     */
    public static GetCompetitivePricingForASINResponse invokeGetCompetitivePricingForASIN(
            MarketplaceWebServiceProducts client,
            GetCompetitivePricingForASINRequest request) throws InterruptedException {
        try {
            maximumRequestQuotaSemaphore.acquire();
            GetCompetitivePricingForASINResponse response = client.getCompetitivePricingForASIN(request);
            return response;
        } catch (MarketplaceWebServiceProductsException ex) {
            // Exception properties are important for diagnostics.
            System.out.println("Service Exception:");
            ResponseHeaderMetadata rhmd = ex.getResponseHeaderMetadata();
            if(rhmd != null) {
                System.out.println("RequestId: "+rhmd.getRequestId());
                System.out.println("Timestamp: "+rhmd.getTimestamp());
            }
            System.out.println("Message: "+ex.getMessage());
            System.out.println("StatusCode: "+ex.getStatusCode());
            System.out.println("ErrorCode: "+ex.getErrorCode());
            System.out.println("ErrorType: "+ex.getErrorType());
            throw ex;
        }
    }

    private static Bson getQueryForAsinsForUpdate() {
        Bson lastUpdatedDoesntExist = exists("retailers.amazon.last_updated", false);
        Bson lastUpdatedLongerThanDayBefore = lte("retailers.amazon.last_updated", Utilities.getYesterday());
        Bson orQuery = or(lastUpdatedDoesntExist, lastUpdatedLongerThanDayBefore);
        return orQuery;
    }

    public static ArrayList<String> getAsinsForUpdate() {
        MongoClient mongoClient = MongoSingleton.getMongoClient();
        ArrayList list = new ArrayList<>();
        DistinctIterable asins = mongoClient.getDatabase(RETAILER_DB_NAME)
                .getCollection(ITEMS_COLLECTION_NAME)
                .distinct("amazon.Identifiers.MarketplaceASIN.ASIN", App.getQueryForAsinsForUpdate(),String.class);
        asins.into(list);
        return list;
    }

    public static void processAsins(List<String> asins) {
        MarketplaceWebServiceProductsClient client = MwsConfig.getClient();
        GetCompetitivePricingForASINRequest request = getRequest(asins);
        try {
            GetCompetitivePricingForASINResponse response = App.invokeGetCompetitivePricingForASIN(client, request);
            processListOfMwsResponse(response);
        } catch (MarketplaceWebServiceProductsException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void addAmazonProductForDate(Product product) {
        MongoClient mongoClient = MongoSingleton.getMongoClient();
        MongoCollection collection = mongoClient.getDatabase(RETAILER_DB_NAME).getCollection("ASINHistory", AsinHistoryDao.class);
        AsinHistoryDao object = new AsinHistoryDao();
        object.setAsin(product.getIdentifiers().getMarketplaceASIN().getASIN());
        object.setProduct(product);
        object.setDate(new Date());
        collection.insertOne(object);
    }

    private static int getPrimarySalesRank(SalesRankList salesRankList) {
        int salesRank = 0;
        int salesRankListSize = salesRankList.getSalesRank().size();
        if (salesRankListSize > 0) {
            salesRank = salesRankList.getSalesRank().get(salesRankListSize -1).getRank();
        }
        return salesRank;
    }

    private static BigDecimal getNewPrice(Product product) {
        List<CompetitivePriceType> competitivePrices = product.getCompetitivePricing()
                .getCompetitivePrices().getCompetitivePrice();
        BigDecimal newConditionPrice = new BigDecimal(0);
        Optional<CompetitivePriceType> newItem = competitivePrices.stream()
                .filter(competitivePrice -> competitivePrice.getCondition().contains("New")).findFirst();
        if (newItem.isPresent()) {
            newConditionPrice = newItem.get().getPrice().getLandedPrice().getAmount()
                    .add(newItem.get().getPrice().getShipping().getAmount());
        }
        return newConditionPrice;
    }

    private static Bson getMongoDbSetterForItemsDb(Product product) {
        Bson price = set("retailers.amazon.price", App.getNewPrice(product));
        Bson lastUpdated = set("retailers.amazon.last_updated", new Date());
        Bson totalSalesRank = set("amazon.SalesRankings", product.getSalesRankings());
        List<Bson> setters = new ArrayList<>(Arrays.asList(price, lastUpdated, totalSalesRank));
        int salesRank = App.getPrimarySalesRank(product.getSalesRankings());
        if (salesRank > 0) {
            setters.add(set("salesrank", App.getPrimarySalesRank(product.getSalesRankings())));
        }
        Bson setter = combine(setters);
        return setter;
    }

    private static Bson getMongoDbQueryForPriceHistory(String upc) {
        Bson query = combine(eq("upc", upc), eq("retailer", "amazon"), eq("date", LocalDate.now(ZoneOffset.UTC)));
        return query;
    }

    private static Bson getMongoDbSetterForPriceHistoryDb(Product product) {
        Bson setter = set("price", App.getNewPrice(product));
        return setter;

    }

    private static Item updateItemsCollection(Product product) {
        MongoClient mongoClient = MongoSingleton.getMongoClient();
        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
        options.returnDocument(ReturnDocument.AFTER);
        MongoCollection collection = mongoClient.getDatabase(RETAILER_DB_NAME).getCollection(ITEMS_COLLECTION_NAME, Item.class);
        Item document = (Item) collection.findOneAndUpdate(eq("amazon.Identifiers.MarketplaceASIN.ASIN",
                product.getIdentifiers().getMarketplaceASIN().getASIN()),
                getMongoDbSetterForItemsDb(product), options);
        return document;
    }

    private static void updatePriceHistoryCollection(Product product, String upc) {
        UpdateOptions options = new UpdateOptions();
        options.upsert(true);
        MongoClient mongoClient = MongoSingleton.getMongoClient();
        MongoCollection collection = mongoClient.getDatabase(RETAILER_DB_NAME).getCollection(PRICE_HISTORY_COLLECTION_NAME);
        collection.updateOne(getMongoDbQueryForPriceHistory(upc), getMongoDbSetterForPriceHistoryDb(product), options);
    }

    private static MinimumPricedRetailer getMinimumPricedRetailer(Item item) {
        HashMap<String, Retailer> retailers = item.getRetailers();
        Map.Entry<String, Retailer> minimumPricedRetailer = Collections.min(retailers.entrySet(),
                Comparator.comparing(retailer ->
                        retailer.getValue().getPrice().floatValue() > 0 ? retailer.getValue().getPrice().floatValue() : Float.MAX_VALUE));
        MinimumPricedRetailer miniPriceRetailer = new MinimumPricedRetailer(minimumPricedRetailer.getValue());
        miniPriceRetailer.setRetailer_name(minimumPricedRetailer.getKey());
        return miniPriceRetailer;
    }

    private static void updateMinimumPricedItem(Item updatedItem) {
        MongoClient mongoClient = MongoSingleton.getMongoClient();
        MongoCollection collection = mongoClient.getDatabase(RETAILER_DB_NAME).getCollection(ITEMS_COLLECTION_NAME);
        MinimumPricedRetailer minimumPricedRetailer = App.getMinimumPricedRetailer(updatedItem);
        collection.updateOne(eq("upc", updatedItem.getUpc()), set("minimum", minimumPricedRetailer));
    }

    private static void processMwsItemResult(GetCompetitivePricingForASINResult result) {
        if (result.getError() == null) {
            Product product = result.getProduct();
            App.addAmazonProductForDate(product);
            Item updatedItem = App.updateItemsCollection(product);
            App.updatePriceHistoryCollection(product, updatedItem.getUpc());
            App.updateMinimumPricedItem(updatedItem);
        } else {
            System.out.print(result);
        }
        pb.step();
    }

    private static void processListOfMwsResponse(GetCompetitivePricingForASINResponse response) {
        response.getGetCompetitivePricingForASINResult().stream().forEach(App::processMwsItemResult);
    }

    private static GetCompetitivePricingForASINRequest getRequest(List<String> asins) {
        GetCompetitivePricingForASINRequest request = new GetCompetitivePricingForASINRequest();
        // Create a request.
        String sellerId = "X";
        request.setSellerId(sellerId);
        String mwsAuthToken = "X";
        request.setMWSAuthToken(mwsAuthToken);
        String marketplaceId = "X";
        request.setMarketplaceId(marketplaceId);
        ASINListType asinList = new ASINListType();
        asinList.setASIN(asins);
        request.setASINList(asinList);
        return request;
    }

    private static ProgressBar pb = new ProgressBar("test", 0);

    /**
     *  Command line entry point.
     */
    public static void main(String[] args) {
        EveryXSecondRefiller refiller = new EveryXSecondRefiller(20, 2000);
        maximumRequestQuotaSemaphore = refiller.getSemaphore();
        refiller.start();
        List<String> asins = getAsinsForUpdate();
        pb.maxHint(asins.size());
        List<List<String>> chunks = ListUtils.partition(asins, 20);
        chunks.forEach(App::processAsins);
    }

}
