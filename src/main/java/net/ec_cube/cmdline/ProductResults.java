package net.ec_cube.cmdline;

import java.util.List;

import com.google.api.client.util.Key;

public class ProductResults {

    @Key("product")
    public List<Product> Product;

    @Key
    public Metadata metadata;

    @Key
    public int limit;

    @Key("has_more")
    public boolean hasMore;
}
