package net.ec_cube.cmdline;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.GenericData;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * A sample application that demonstrates how the Google OAuth2 library can be
 * used to authenticate against EC-CUBE3.
 *
 * @author Kentaro Ohkouchi
 */
public class OAuth2Sample {

    /** Directory to store user credentials. */
    private static final java.io.File DATA_STORE_DIR = new java.io.File(
            System.getProperty("user.home"),
            ".store/eccube3-oauth2-client-for-java");

    /**
     * Global instance of the {@link DataStoreFactory}. The best practice is to
     * make it a single globally shared instance across your application.
     */
    private static FileDataStoreFactory DATA_STORE_FACTORY;

    /** OAuth 2 scope. */
    private static final String SCOPE = "order_read product_read product_write product_class_read product_class_write product_stock_read product_stock_write";

    /** Global instance of the HTTP transport. */
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    /** Global instance of the JSON factory. */
    static final JsonFactory JSON_FACTORY = new JacksonFactory();

    /** Authorizes the installed application to access user's protected data. */
    private static Credential authorize() throws Exception {
        Properties authProp = getAuthorizationProperties();
        // set up authorization code flow
        AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(
                BearerToken.authorizationHeaderAccessMethod(), HTTP_TRANSPORT,
                JSON_FACTORY, new GenericUrl(
                        authProp.getProperty("TOKEN_ENDPOINT")),
                new ClientParametersAuthentication(
                        authProp.getProperty("CLIENT_ID"),
                        authProp.getProperty("CLIENT_SECRET")),
                authProp.getProperty("CLIENT_ID"),
                authProp.getProperty("AUTHORIZATION_ENDPOINT"))
                .setScopes(Arrays.asList(SCOPE))
                .setDataStoreFactory(DATA_STORE_FACTORY).build();
        // authorize
        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setHost(authProp.getProperty("DOMAIN"))
                .setPort(Integer.parseInt(authProp.getProperty("PORT")))
                .build();
        AuthorizationCodeInstalledApp app = new AuthorizationCodeInstalledApp(flow, receiver) {
            @Override
            public Credential authorize(String userId) throws IOException {
                try {
                    Credential credential = getFlow().loadCredential(userId);
                    if (credential != null
                            && (credential.getRefreshToken() != null
                                || credential.getExpiresInSeconds() > 60)) {
                        return credential;
                    }
                    String state = RandomStringUtils.randomAlphanumeric(32);
                    // open in browser
                    String redirectUri = getReceiver().getRedirectUri();
                    AuthorizationCodeRequestUrl authorizationUrl = getFlow()
                            .newAuthorizationUrl()
                            .setRedirectUri(redirectUri)
                            .setState(state);
                    onAuthorization(authorizationUrl);
                    // receive authorization code and exchange it for an access
                    // token
                    String code = getReceiver().waitForCode();
                    TokenResponse response = getFlow().newTokenRequest(code)
                            .setRedirectUri(redirectUri).execute();
                    // store credential and return it
                    return getFlow().createAndStoreCredential(response, userId);
                } finally {
                    getReceiver().stop();
                }
            }
        };
        return app.authorize("user");
    }

    private static void listProducts(HttpRequestFactory requestFactory) throws Exception {
        Properties authProp = getAuthorizationProperties();

        HttpRequest request = requestFactory.buildGetRequest(new GenericUrl(
                authProp.getProperty("RESOURCE")));
        request.setParser(new JsonObjectParser(new JacksonFactory()));
        HttpResponse response = request.execute();

        JsonObjectParser p = (JsonObjectParser) request.getParser();
        ProductResults products = p.parseAndClose(response.getContent(), response.getContentCharset(), ProductResults.class);
        for (Product product : products.Product) {
            System.out.println(product.name);
        }
    }

    private static void printProductDetail(HttpRequestFactory requestFactory, int id) throws Exception {
        Properties authProp = getAuthorizationProperties();

        HttpRequest request = requestFactory.buildGetRequest(new GenericUrl(
                authProp.getProperty("BASEURL") + "/product/" + String.valueOf(id)));
        request.setParser(new JsonObjectParser(new JacksonFactory()));
        HttpResponse response = request.execute();

        JsonObjectParser p = (JsonObjectParser) request.getParser();
        Products productFeed = p.parseAndClose(response.getContent(),
                response.getContentCharset(), Products.class);

        Product product = productFeed.product;
        System.out.println("ID: " + product.id);
        System.out.println("name: " + product.name);
        System.out.println("DescriptionDetail: " + product.descriptionDetail);
    }

    private static void doOperationProduct(HttpRequestFactory requestFactory) throws Exception {
        int productId = createProduct(requestFactory, "Java で作った商品", 999999999);
        System.out.println("Created by productId: " + productId);

        GenericData data = new GenericData();
        data.put("name", "商品名を変更しました");
        if (executeUpdate(requestFactory, "product", productId, data)) {
            System.out.println("Updated by");
            printProductDetail(requestFactory, productId);
        }

        if (executeDelete(requestFactory, "product", productId)) {
            System.out.println("商品ID: " + productId + " を削除しました");
        }
    }

    private static int createProduct(HttpRequestFactory requestFactory, String productName, int price02) throws Exception {
        Properties authProp = getAuthorizationProperties();
        GenericData product = new GenericData();
        product.put("name", productName);
        product.put("Creator", createGenericObject(2));
        product.put("Status", createGenericObject(1));
        product.put("del_flg", 0);
        String location = executePost(requestFactory, "product", product);
        int product_id = Integer.parseInt(location.replaceAll(authProp.getProperty("RESOURCE") + "/", ""));

        GenericData productClass = new GenericData();
        productClass.put("Product", createGenericObject(product_id));
        productClass.put("price02", price02);
        productClass.put("stock_unlimited", 1);
        productClass.put("del_flg", 0);
        productClass.put("product_id", product_id);
        productClass.put("ProductType", createGenericObject(1));
        productClass.put("Creator", createGenericObject(2));

        String location2 = executePost(requestFactory, "product_class", productClass);
        return product_id;
    }

    private static String executePost(HttpRequestFactory requestFactory, String table, GenericData data)
            throws Exception {
        Properties authProp = getAuthorizationProperties();
        HttpContent content = new JsonHttpContent(new JacksonFactory(), data);
        HttpRequest request = requestFactory
                .buildPostRequest(new GenericUrl(authProp.getProperty("BASEURL") + "/" + table), content);
        HttpResponse response = request.execute();
        HttpHeaders headers = response.getHeaders();
        if (response.getStatusCode() != 201) {
            throw new Exception("HTTP Status: " + response.getStatusCode());
        }
        return headers.getLocation();
    }

    private static boolean executeUpdate(HttpRequestFactory requestFactory, String table, int id, GenericData data) throws Exception {
        Properties authProp = getAuthorizationProperties();
        HttpContent content = new JsonHttpContent(new JacksonFactory(), data);
        HttpRequest request = requestFactory
                .buildPutRequest(new GenericUrl(authProp.getProperty("BASEURL") + "/" + table + "/" + String.valueOf(id)), content);
        HttpResponse response = request.execute();
        if (response.getStatusCode() != 204) {
            throw new Exception("HTTP Status: " + response.getStatusCode());
        }
        return true;
    }

    private static boolean executeDelete(HttpRequestFactory requestFactory, String table, int id) throws Exception {
        Properties authProp = getAuthorizationProperties();
        HttpRequest request = requestFactory.buildDeleteRequest(new GenericUrl(authProp.getProperty("BASEURL") + "/" + table + "/" + String.valueOf(id)));
        HttpResponse response = request.execute();
        if (response.getStatusCode() != 204) {
            throw new Exception("HTTP Status: " + response.getStatusCode());
        }
        return true;
    }
    private static GenericData createGenericObject(int id) {
        GenericData data = new GenericData();
        data.put("id", id);
        return data;
    }

    protected static Properties getAuthorizationProperties() throws Exception {
        Properties authProp = new Properties();
        String propFile = "authorization.properties";
        InputStream input = null;
        try {
            input = new FileInputStream(propFile);
            authProp.load(input);
        } catch (FileNotFoundException e) {
            new Exception(e);
        } catch (IOException e) {
            new Exception(e);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    // quiet.
                }
            }
        }
        return authProp;
    }

    public static void main(String[] args) {
        try {
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
            final Credential credential = authorize();
            HttpRequestFactory requestFactory = HTTP_TRANSPORT
                    .createRequestFactory(new HttpRequestInitializer() {

                        public void initialize(HttpRequest request)
                                throws IOException {
                            credential.initialize(request);
                            request.setParser(new JsonObjectParser(JSON_FACTORY));
                        }
                    });
            listProducts(requestFactory);
            printProductDetail(requestFactory, 1);
            doOperationProduct(requestFactory);
            // Success!
            return;
        } catch (IOException e) {
            System.err.println(e.getMessage());
        } catch (Throwable t) {
            t.printStackTrace();
        }
        System.exit(1);
    }
}
