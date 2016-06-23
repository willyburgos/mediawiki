/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.mediawiki.syntax.internal.parser.model;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.rendering.listener.reference.AttachmentResourceReference;
import org.xwiki.rendering.listener.reference.DocumentResourceReference;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.parser.ResourceReferenceParser;

import info.bliki.htmlcleaner.ContentToken;
import info.bliki.htmlcleaner.TagToken;
import info.bliki.wiki.filter.WikipediaPreTagParser;
import info.bliki.wiki.model.Configuration;
import info.bliki.wiki.model.ImageFormat;
import info.bliki.wiki.model.WikiModel;
import info.bliki.wiki.namespaces.INamespace.NamespaceCode;
import info.bliki.wiki.tags.PTag;
import info.bliki.wiki.tags.SourceTag;

/**
 * Custom WikiModel.
 * 
 * @version $Id$
 */
@Component(roles = EventWikiModel.class)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class EventWikiModel extends WikiModel
{
    private boolean nopipe;

    class Tokens implements Map<String, TagToken>
    {
        @Override
        public int size()
        {
            return EventWikiModel.super.getTokenMap().size();
        }

        @Override
        public boolean isEmpty()
        {
            return EventWikiModel.super.getTokenMap().isEmpty();
        }

        @Override
        public boolean containsKey(Object key)
        {
            return EventWikiModel.super.getTokenMap().containsKey(key);
        }

        @Override
        public boolean containsValue(Object value)
        {
            return EventWikiModel.super.getTokenMap().containsValue(value);
        }

        @Override
        public TagToken get(Object key)
        {
            TagToken token = EventWikiModel.super.getTokenMap().get(key);

            if (token == null) {
                String keyString = (String) key;
                if (keyString.startsWith(XMacroTag.TAGPREFIX)) {
                    String macroId = ((String) key).substring(XMacroTag.TAGPREFIX.length());
                    token = isInline() ? new XInlineMacroTag(macroId) : new XStandaloneMacroTag(macroId);
                }
            }

            return token;
        }

        @Override
        public TagToken put(String key, TagToken value)
        {
            return EventWikiModel.super.getTokenMap().put(key, value);
        }

        @Override
        public TagToken remove(Object key)
        {
            return EventWikiModel.super.getTokenMap().remove(key);
        }

        @Override
        public void putAll(Map<? extends String, ? extends TagToken> m)
        {
            EventWikiModel.super.getTokenMap().putAll(m);
        }

        @Override
        public void clear()
        {
            EventWikiModel.super.getTokenMap().clear();
        }

        @Override
        public Set<String> keySet()
        {
            return EventWikiModel.super.getTokenMap().keySet();
        }

        @Override
        public Collection<TagToken> values()
        {
            return EventWikiModel.super.getTokenMap().values();
        }

        @Override
        public Set<java.util.Map.Entry<String, TagToken>> entrySet()
        {
            return EventWikiModel.super.getTokenMap().entrySet();
        }

    }

    @Inject
    @Named("default/link")
    private ResourceReferenceParser linkReferenceParser;

    /**
     * @see #getImageReferenceParser()
     */
    @Inject
    @Named("default/image")
    private ResourceReferenceParser imageReferenceParser;

    private Tokens tokens = new Tokens();

    /**
     * Default constructor.
     */
    public EventWikiModel()
    {
        super("${image}", "${title}");

        addStandaloneMacroTag("gallery");
        addStandaloneMacroTag("blockquote");

        addTokenTag(Configuration.HTML_CODE_OPEN.getName(), new SourceTag());
    }

    private void addStandaloneMacroTag(String name)
    {
        addTokenTag(name, new XStandaloneMacroTag(name));

    }

    @Override
    public boolean appendRawNamespaceLinks(String rawNamespaceTopic, String viewableLinkDescription,
        boolean containsNoPipe)
    {
        this.nopipe = containsNoPipe;

        return super.appendRawNamespaceLinks(rawNamespaceTopic, viewableLinkDescription, containsNoPipe);
    }

    @Override
    public void appendInternalLink(String topic, String hashSection, String topicDescription, String cssClass,
        boolean parseRecursive)
    {
        // Create reference
        ResourceReference reference = null;

        if (StringUtils.isNotEmpty(hashSection)) {
            reference = new ResourceReference('#' + hashSection, ResourceType.PATH);
        } else {
            int index = topic.indexOf(':', 1);
            if (index > 0) {
                String namespace = topic.substring(0, index);
                if (this.fNamespace.isNamespace(namespace, NamespaceCode.FILE_NAMESPACE_KEY)
                    || namespace.equalsIgnoreCase("media")) {
                    reference = new AttachmentResourceReference(topic.substring(namespace.length() + 1));
                }
            }
        }

        // Fallback on standard link reference parser
        if (reference == null) {
            reference = this.linkReferenceParser.parse(topic);
        }

        // Set anchor
        if (StringUtils.isNotEmpty(hashSection)) {
            if (reference instanceof DocumentResourceReference) {
                ((DocumentResourceReference) reference).setAnchor(hashSection);
            }
        }

        // Create tag
        LinkTag linkTag = new LinkTag(reference, false);

        // Append tag
        pushNode(linkTag);
        if (!this.nopipe) {
            if (parseRecursive) {
                WikipediaPreTagParser.parseRecursive(topicDescription, this, false, true);
            } else {
                linkTag.addChild(new ContentToken(topicDescription));
            }
        }
        popNode();
    }

    @Override
    public void appendInternalImageLink(String hrefImageLink, String srcImageLink, ImageFormat imageFormat)
    {
        ResourceReference reference = this.imageReferenceParser.parse(srcImageLink);

        ImageTag imageTag = new ImageTag(reference, false, imageFormat);

        append(imageTag);
    }

    @Override
    public void appendMailtoLink(String link, String linkName, boolean withoutSquareBrackets)
    {
        ResourceReference reference = new ResourceReference(link.substring("mailto:".length()), ResourceType.MAILTO);

        LinkTag linkTag = new LinkTag(reference, withoutSquareBrackets);

        if (!withoutSquareBrackets && !StringUtils.equals(link, linkName)) {
            linkTag.addChild(new ContentToken(linkName));
        }

        append(linkTag);
    }

    @Override
    public void appendExternalLink(String uriSchemeName, String link, String linkName, boolean withoutSquareBrackets)
    {
        // TODO: support uri with empty scheme (fallback on current request scheme)

        ResourceReference reference = new ResourceReference(link, ResourceType.URL);

        LinkTag linkTag = new LinkTag(reference, withoutSquareBrackets);

        if (!withoutSquareBrackets && !StringUtils.equals(link, linkName)) {
            linkTag.addChild(new ContentToken(linkName));
        }

        append(linkTag);
    }

    private boolean isInline()
    {
        TagToken token = peekNode();

        if (token != null) {
            if (!Configuration.SPECIAL_BLOCK_TAGS.contains('|' + token.getName() + '|')) {
                if (token instanceof PTag) {
                    // Bliki always produce a opening P tag no matter what and only close it later of the following tag
                    // is a standalone tag
                    return ((PTag) token).getChildren().size() > 0;
                } else {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public Map<String, TagToken> getTokenMap()
    {
        return this.tokens;
    }
}
