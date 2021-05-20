import com.amazonservices.mws.products.MarketplaceWebServiceProductsAsyncClient;
import com.amazonservices.mws.products.MarketplaceWebServiceProductsClient;
import com.amazonservices.mws.products.MarketplaceWebServiceProductsConfig;

/**
 * Configuration for MarketplaceWebServiceProducts samples.
 */
public class MwsConfig {

    /** Developer AWS access key. */
    private static final String accessKey = "X";

    /** Developer AWS secret key. */
    private static final String secretKey = "X";

    /** The client application name. */
    private static final String appName = "X";

    /** The client application version. */
    private static final String appVersion = "X";

    /**
     * The endpoint for region service and version.
     * ex: serviceURL = MWSEndpoint.NA_PROD.toString();
     */
    private static final String serviceURL = "https://mws.amazonservices.com/Products/2011-10-01";

    /** The client, lazy initialized. Async client is also a sync client. */
    private static MarketplaceWebServiceProductsAsyncClient client = null;

    /**
     * Get a client connection ready to use.
     *
     * @return A ready to use client connection.
     */
    public static MarketplaceWebServiceProductsClient getClient() {
        return getAsyncClient();
    }

    /**
     * Get an async client connection ready to use.
     *
     * @return A ready to use client connection.
     */
    public static synchronized MarketplaceWebServiceProductsAsyncClient getAsyncClient() {
        if (client==null) {
            MarketplaceWebServiceProductsConfig config = new MarketplaceWebServiceProductsConfig();
            config.setServiceURL(serviceURL);
            // Set other client connection configurations here.
            client = new MarketplaceWebServiceProductsAsyncClient(accessKey, secretKey,
                    appName, appVersion, config, null);
        }
        return client;
    }

}
