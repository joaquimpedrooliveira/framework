package org.demoiselle.jee.configuration.extractor.impl;

import static org.demoiselle.jee.core.annotation.Priority.L2_PRIORITY;

import java.lang.reflect.Field;

import javax.enterprise.context.Dependent;

import org.apache.commons.configuration2.Configuration;
import org.demoiselle.jee.configuration.ConfigType;
import org.demoiselle.jee.configuration.extractor.ConfigurationValueExtractor;
import org.demoiselle.jee.core.annotation.Priority;

/**
 * Adds the data extraction capability of a source ({@link ConfigType}) for the type of {@link Enum}.
 * 
 * <p>
 * Sample:
 * </p>
 * 
 * <p>
 * For the extraction of an Enum type of a properties file the statement made in the properties will have the following format:
 * </p>
 * 
 * <pre>
 * demoiselle.loadedConfigurationType=SYSTEM
 * </pre>
 * 
 * And the configuration class will be declared as follows:
 * 
 * <pre>
 *  
 * &#64;Configuration
 * public class BookmarkConfig {
 *
 *  private {@link ConfigType} loadedConfigurationType;
 *
 *  public ConfigType getLoadedConfigurationType(){
 *    return loadedConfigurationType;
 *  }
 *
 * }
 * 
 * </pre>
 * 
 */
@Dependent
@Priority(L2_PRIORITY)
public class ConfigurationEnumValueExtractor implements ConfigurationValueExtractor {

	@Override
	public Object getValue(String prefix, String key, Field field, Configuration configuration) throws Exception {
		String value = configuration.getString(prefix + key);

		if (value != null && !value.trim().equals("")) {
			Object enums[] = field.getType().getEnumConstants();

			for (int i = 0; i < enums.length; i++) {
				if (((Enum<?>) enums[i]).name().equals(value)) {
					return enums[i];
				}
			}
		}
		
		return null;
		
	}

	@Override
	public boolean isSupported(Field field) {
		return field.getType().isEnum();
	}

}
