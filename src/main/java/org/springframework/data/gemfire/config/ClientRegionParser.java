/*
 * Copyright 2010-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.gemfire.config;

import java.util.List;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.data.gemfire.RegionAttributesFactoryBean;
import org.springframework.data.gemfire.client.ClientRegionFactoryBean;
import org.springframework.data.gemfire.client.Interest;
import org.springframework.data.gemfire.client.RegexInterest;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

/**
 * Parser for &lt;client-region;gt; bean definitions.
 *
 * To avoid eager evaluations, the region interests are declared as nested definition.
 *
 * @author Costin Leau
 * @author David Turanski
 * @author John Blum
 */
class ClientRegionParser extends AbstractRegionParser {

	@Override
	protected Class<?> getRegionFactoryClass() {
		return ClientRegionFactoryBean.class;
	}

	@Override
	protected void doParseRegion(Element element, ParserContext parserContext, BeanDefinitionBuilder regionBuilder,
			boolean subRegion) {

		validateDataPolicyShortcutAttributesMutualExclusion(element, parserContext);

		String resolvedCacheRef = ParsingUtils.resolveCacheReference(element.getAttribute("cache-ref"));

		if (!subRegion) {
			regionBuilder.addPropertyReference("cache", resolvedCacheRef);
			ParsingUtils.setPropertyValue(element, regionBuilder, "close");
			ParsingUtils.setPropertyValue(element, regionBuilder, "destroy");
		}

		ParsingUtils.setPropertyValue(element, regionBuilder, "name");
		ParsingUtils.setPropertyValue(element, regionBuilder, "data-policy", "dataPolicyName");
		ParsingUtils.setPropertyValue(element, regionBuilder, "ignore-if-exists", "lookupEnabled");
		ParsingUtils.setPropertyValue(element, regionBuilder, "persistent");
		ParsingUtils.setPropertyValue(element, regionBuilder, "pool-name");
		ParsingUtils.setPropertyValue(element, regionBuilder, "shortcut");

		parseDiskStoreAttribute(element, regionBuilder);

		// Client RegionAttributes for overflow/eviction, expiration and statistics
		BeanDefinitionBuilder regionAttributesBuilder = BeanDefinitionBuilder.genericBeanDefinition(
			RegionAttributesFactoryBean.class);

		mergeTemplateRegionAttributes(element, parserContext, regionBuilder, regionAttributesBuilder);

		ParsingUtils.parseOptionalRegionAttributes(parserContext, element, regionAttributesBuilder);
		ParsingUtils.parseStatistics(element, regionAttributesBuilder);
		ParsingUtils.parseExpiration(parserContext, element, regionAttributesBuilder);
		ParsingUtils.parseEviction(parserContext, element, regionAttributesBuilder);
		ParsingUtils.parseCompressor(parserContext, element, regionAttributesBuilder);

		regionBuilder.addPropertyValue("attributes", regionAttributesBuilder.getBeanDefinition());

		List<Element> subElements = DomUtils.getChildElements(element);

		ManagedList<Object> interests = new ManagedList<Object>();

		for (Element subElement : subElements) {
			String subElementLocalName = subElement.getLocalName();

			if ("cache-listener".equals(subElementLocalName)) {
				regionBuilder.addPropertyValue("cacheListeners", ParsingUtils.parseRefOrNestedBeanDeclaration(
					parserContext, subElement, regionBuilder));
			}
			else if ("cache-loader".equals(subElementLocalName)) {
				regionBuilder.addPropertyValue("cacheLoader", ParsingUtils.parseRefOrNestedBeanDeclaration(
					parserContext, subElement, regionBuilder));
			}
			else if ("cache-writer".equals(subElementLocalName)) {
				regionBuilder.addPropertyValue("cacheWriter", ParsingUtils.parseRefOrNestedBeanDeclaration(
					parserContext, subElement, regionBuilder));
			}
			else if ("key-interest".equals(subElementLocalName)) {
				interests.add(parseKeyInterest(subElement, parserContext));
			}
			else if ("regex-interest".equals(subElementLocalName)) {
				interests.add(parseRegexInterest(subElement));
			}
		}

		if (!interests.isEmpty()) {
			regionBuilder.addPropertyValue("interests", interests);
		}

		if (!subRegion) {
			parseSubRegions(element, parserContext, resolvedCacheRef);
		}
	}

	private void parseDiskStoreAttribute(final Element element, final BeanDefinitionBuilder builder) {
		String diskStoreRefAttribute = element.getAttribute("disk-store-ref");

		if (StringUtils.hasText(diskStoreRefAttribute)) {
			builder.addPropertyValue("diskStoreName", diskStoreRefAttribute);
			builder.addDependsOn(diskStoreRefAttribute);
		}
	}

	private void parseCommonInterestAttributes(Element element, BeanDefinitionBuilder builder) {
		ParsingUtils.setPropertyValue(element, builder, "durable", "durable");
		ParsingUtils.setPropertyValue(element, builder, "result-policy", "policy");
		ParsingUtils.setPropertyValue(element, builder, "receive-values", "receiveValues");
	}

	private Object parseKeyInterest(Element keyInterestElement, ParserContext parserContext) {
		BeanDefinitionBuilder keyInterestBuilder = BeanDefinitionBuilder.genericBeanDefinition(Interest.class);

		parseCommonInterestAttributes(keyInterestElement, keyInterestBuilder);
		keyInterestBuilder.addConstructorArgValue(ParsingUtils.parseRefOrNestedBeanDeclaration(parserContext,
			keyInterestElement, keyInterestBuilder, "key-ref"));

		return keyInterestBuilder.getBeanDefinition();
	}

	private Object parseRegexInterest(Element regexInterestElement) {
		BeanDefinitionBuilder regexInterestBuilder = BeanDefinitionBuilder.genericBeanDefinition(RegexInterest.class);

		parseCommonInterestAttributes(regexInterestElement, regexInterestBuilder);
		ParsingUtils.setPropertyValue(regexInterestElement, regexInterestBuilder, "pattern", "key");

		return regexInterestBuilder.getBeanDefinition();
	}

}
