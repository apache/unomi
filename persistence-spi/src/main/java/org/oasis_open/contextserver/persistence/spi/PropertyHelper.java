package org.oasis_open.contextserver.persistence.spi;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.beanutils.expression.DefaultResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Created by toto on 20/05/15.
 */
public class PropertyHelper {

    private static final Logger logger = LoggerFactory.getLogger(PropertyHelper.class.getName());
    private static DefaultResolver resolver = new DefaultResolver();

    public static boolean setProperty(Object target, String propertyName, Object propertyValue, String setPropertyStrategy) {
        try {
            while (resolver.hasNested(propertyName)) {
                Object v = PropertyUtils.getProperty(target, resolver.next(propertyName));
                if (v == null) {
                    v = new LinkedHashMap<>();
                    PropertyUtils.setProperty(target, resolver.next(propertyName), v);
                }
                propertyName = resolver.remove(propertyName);
                target = v;
            }

            if (setPropertyStrategy != null && setPropertyStrategy.equals("addValue")) {
                Object previousValue = PropertyUtils.getProperty(target, propertyName);
                List<Object> values = new ArrayList<>();
                if (previousValue != null && previousValue instanceof List) {
                    values.addAll((List) previousValue);
                } else if (previousValue != null) {
                    values.add(previousValue);
                }
                if (!values.contains(propertyValue)) {
                    values.add(propertyValue);
                    BeanUtils.setProperty(target, propertyName, values);
                    return true;
                }
            } else if (propertyValue != null && !propertyValue.equals(BeanUtils.getProperty(target, propertyName))) {
                if (setPropertyStrategy == null ||
                        setPropertyStrategy.equals("alwaysSet") ||
                        (setPropertyStrategy.equals("setIfMissing") && BeanUtils.getProperty(target, propertyName) == null)) {
                    BeanUtils.setProperty(target, propertyName, propertyValue);
                    return true;
                }
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            logger.error("Cannot set property", e);
        }
        return false;
    }

}
