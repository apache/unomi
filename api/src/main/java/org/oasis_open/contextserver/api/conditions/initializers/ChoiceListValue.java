package org.oasis_open.contextserver.api.conditions.initializers;

/**
 * List option object for various choice lists. 
 */
public class ChoiceListValue implements Cloneable {
    
    private String id;
    
    private String name;

    public ChoiceListValue(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * Returns a cloned instance of this choice list value object with the name, set to the provided localized name.
     * 
     * @param localizedName
     *            the localized name for this choice list value object
     * @return a cloned instance of this choice list value object with the name, set to the provided localized name
     */
    public ChoiceListValue localizedCopy(String localizedName) {
        try {
            ChoiceListValue clone = (ChoiceListValue) clone();
            clone.name = localizedName;
            
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new IllegalArgumentException(e);
        }
        
    }
}
