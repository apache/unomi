package org.oasis_open.wemi.context.server.persistence.spi;

import org.oasis_open.wemi.context.server.api.Item;

import java.util.List;

/**
 * Created by loom on 02.05.14.
 */
public interface PersistenceService {

    public boolean save(Item item);

    public Item load(String itemId);

    public List<Item> query(String query);
}
