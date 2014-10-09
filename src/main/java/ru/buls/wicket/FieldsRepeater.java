package ru.buls.wicket;

import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.markup.*;
import org.apache.wicket.markup.html.WebMarkupContainerWithAssociatedMarkup;
import org.apache.wicket.markup.html.form.*;
import org.apache.wicket.markup.parser.XmlTag;
import org.apache.wicket.markup.parser.filter.WicketTagIdentifier;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.resource.ResourceStreamNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;

import static org.apache.wicket.markup.MarkupResourceData.NO_MARKUP_RESOURCE_DATA;


/**
 * Created by alexander on 05.10.14.
 */
public class FieldsRepeater extends MarkupContainer {

    private Logger logger = LoggerFactory.getLogger(FieldsRepeater.class);

    static {
        WicketTagIdentifier.registerWellKnownTagName("field");
        WicketTagIdentifier.registerWellKnownTagName("label");
    }

    protected Decorator<String> labelDecorator = new LabelDecorator();
    protected ChildTagBuilder childTagBuilder = new ChildTagBuilder();
    protected boolean simplifyMarkupId = true;

    public FieldsRepeater(String id) {
        super(id);
    }

    @Override
    protected void onRender(final MarkupStream markupStream) {
        int startIndex = markupStream.getCurrentIndex();

        for (int i = 0; i < size(); ++i) {
            markupStream.setCurrentIndex(startIndex);
            Component component = get(i);
            component.render(markupStream);
        }
    }


    private void copy(Markup from, Markup to) {
        for (int i = 0; i < from.size(); ++i) {
            to.addMarkupElement(from.get(i));
        }
    }

    private String getEnclosureId(Component child) {
        return "enclosureFor" + child.getMarkupId();
    }

    private Markup createMarkupFor(MarkupElement tag, Component child) {
        Markup childMarkup;
        if (tag instanceof WicketTag) {
            WicketTag wtag = (WicketTag) tag;
            String name = wtag.getName();
            if (!wtag.isOpenClose()) {
                throw new IllegalStateException(tag + " must be closed");
            }
            if ("field".equals(name))
                childMarkup = createChildMarkup(child, wtag);
            else if ("label".equals(name))
                childMarkup = getLabelMarkup(child);
            else throw new IllegalStateException(tag.toString());
        } else if (tag instanceof ComponentTag) {
            ComponentTag cTag = (ComponentTag) tag;
            if ((cTag.isOpen() || cTag.isOpenClose()) && "label".equals(cTag.getName())) {
                String wicketFor = cTag.getAttribute("wicket:for");
                if ("wicket:field".equals(wicketFor)) {
                    ComponentTag newTag = new ComponentTag(cTag.getName(), cTag.getType());
                    newTag.getAttributes().putAll(cTag.getAttributes());
                    cTag = newTag;
                    //cTag.getAttributes().remove("wicket:for");
                    cTag.getAttributes().put("wicket:for", child.getMarkupId());
                } else if (wicketFor != null) {
                    throw new IllegalStateException("incorrect value '" + wicketFor
                            + "' for attribute wicket:field of tag " + cTag);
                }
            }

            Markup markup = new Markup(NO_MARKUP_RESOURCE_DATA);
            markup.addMarkupElement(cTag);

            childMarkup = markup;
        } else {
            Markup markup = new Markup(NO_MARKUP_RESOURCE_DATA);
            markup.addMarkupElement(tag);
            childMarkup = markup;
        }
        return childMarkup;
    }

    private Markup createChildMarkup(Component child, WicketTag wtag) {
        String tagName = childTagBuilder.getTagName(child);
        ComponentTag open = childTagBuilder.createOpenTag(child, tagName);

        String childId = child.getMarkupId();
        open.setId(childId);
        open.put("wicket:id", childId);

        //копируем атрибуты викет тега
        if (wtag != null)
            open.getAttributes().putAll(wtag.getAttributes());

        ComponentTag close = childTagBuilder.createCloseTag(tagName, open);
        Markup markup = new Markup(NO_MARKUP_RESOURCE_DATA);
        if (open instanceof ComponentTag) {
            markup.addMarkupElement(open);
            if (close != null) markup.addMarkupElement(close);
        }
        return markup;
    }

    private Markup getLabelMarkup(Component child) {
        String label = labelDecorator.decorate(getLabel(child));

        Markup markup = new Markup(NO_MARKUP_RESOURCE_DATA);
        markup.addMarkupElement(new RawMarkup(label));

        return markup;
    }

    protected String getLabel(Component child) {
        String label = child.getMarkupId();
        if (child instanceof ILabelProvider) {
            ILabelProvider labelProvider = (ILabelProvider) child;

            IModel model = labelProvider.getLabel();
            label = model != null ? model.toString() : label;
        }
        return label;
    }

    public FieldsRepeater add(Component child) {
        add(child, true);
        return this;

    }

    public FieldsRepeater add(Component child, boolean enclosureVisible) {
        Enclosure enclo = new Enclosure(getEnclosureId(child));
        enclo.add(child);
        enclo.setVisible(enclosureVisible);
        if (simplifyMarkupId) {
            enclo.setOutputMarkupId(getOutputMarkupId());
            enclo.setOutputMarkupPlaceholderTag(getOutputMarkupPlaceholderTag());
            child.setOutputMarkupPlaceholderTag(getOutputMarkupPlaceholderTag());
            child.setMarkupId(child.getId());
            enclo.setMarkupId(enclo.getId());
        }
        super.add(enclo);
        return this;
    }

    static class LabelDecorator implements Decorator<String> {
        @Override
        public String decorate(String s) {
            return s;
        }
    }

    class ChildTagBuilder implements Serializable {
        private String getTagName(Component child) {
            String tagName;
            if (child instanceof TextArea) tagName = "textarea";
            else if (child instanceof TextField) tagName = "input";
            else if (child instanceof CheckBox) tagName = "input";
            else if (child instanceof AbstractChoice) tagName = "select";
            else if (child instanceof WebMarkupContainerWithAssociatedMarkup || child instanceof FormComponentPanel)
                tagName = "span";
            else if (child instanceof FieldsRepeater) tagName = "span";
            else {
                throw new UnsupportedOperationException("does not support child element " + child);
            }
            return tagName;
        }

        protected ComponentTag createCloseTag(String tagName, MarkupElement open) {
            ComponentTag close = new ComponentTag(tagName, XmlTag.CLOSE);
            close.setOpenTag((ComponentTag) open);
            return close;
        }

        protected ComponentTag createOpenTag(Component child, String tagName) {
            ComponentTag open = new ComponentTag(tagName, XmlTag.OPEN);
            if (child instanceof CheckBox) open.getAttributes().put("type", "checkbox");
            if (child instanceof TextField) open.getAttributes().put("type", "text");
            return open;
        }

    }

    class Enclosure extends MarkupContainer {

        private String generatedMarkup;

        public Enclosure(String id) {
            super(id, new Model());
        }

        @Override
        public MarkupStream getAssociatedMarkupStream(boolean throwException) {

            if (generatedMarkup == null) {
                StringBuilder builder = new StringBuilder();
                Markup markup = generate();
                for (int i = 0; i < markup.size(); ++i) {
                    MarkupElement element = markup.get(i);
                    if (element != null) builder.append(String.valueOf(element.toCharSequence()).trim());
                }
                generatedMarkup = builder.toString();
            }
            Markup markup;
            try {
                Markup parse = new MarkupParser(generatedMarkup).parse();
                copy(parse, markup = new Markup(NO_MARKUP_RESOURCE_DATA));
            } catch (IOException e) {
                logger.error("error on parsing generated markup : " + generatedMarkup, e);
                throw new RuntimeException(e);
            } catch (ResourceStreamNotFoundException e) {
                logger.error("error on parsing generated markup : " + generatedMarkup, e);
                throw new RuntimeException(e);
            }
            return new MarkupStream(markup);
        }

        protected Markup generate() {
            MarkupStream markupStream = getMarkupStream();
            Markup markup = new Markup(NO_MARKUP_RESOURCE_DATA);

            int endIndex = -1;

            ComponentTag startTag = markupStream.getTag();
            assert startTag != null;

            openEnclosure(markup, startTag);
            Component child = this.get(0);
            endIndex = child(markupStream, markup, startTag, child);
            closeEnclosure(endIndex, markup, markupStream);

            if (endIndex > 0) markupStream.setCurrentIndex(endIndex);
            if (markupStream.hasMore()) markupStream.next();
            return markup;
        }

        @Override
        public boolean hasAssociatedMarkup() {
            return true;
        }

        @Override
        protected void onComponentTagBody(MarkupStream markupStream, ComponentTag openTag) {
            super.onComponentTagBody(markupStream, openTag);
        }

        @Override
        protected void onComponentTag(ComponentTag ctag) {
            super.onComponentTag(ctag);

        }

        @Override
        protected void onRender(@Deprecated MarkupStream badStream) {
            MarkupStream stream = getAssociatedMarkupStream(false);
            Component child = get(0);
            boolean childRendered = false;
            MarkupElement next = stream.get();
            while (next != null) {
                if (next instanceof ComponentTag) {
                    ComponentTag ctag = (ComponentTag) next;
                    if (child.getId().equals(ctag.getId())) {
                        int ci = stream.getCurrentIndex();
                        child.render(stream);
                        childRendered = true;
                        //в результате отрисовки чайлда, стрим ушел вперед, по этому берем текущий, а не следующий элемент
                        if (stream.getCurrentIndex() > ci) {
                            next = stream.get();
                            continue;
                        }
                    } else getResponse().write(ctag);
                } else if (next != null) {
                    getResponse().write(next.toCharSequence());
                }
                next = stream.next();
            }

            if (!childRendered) {
                throw new IllegalStateException("cannot find tag for child " + child);
            }
        }
    }

    private void closeEnclosure(int endIndex, Markup markup, MarkupStream markupStream) {
        MarkupElement closeTag = markupStream.get(endIndex);
        markup.addMarkupElement(closeTag);
    }

    private int child(MarkupStream markupStream, Markup markup, ComponentTag startTag, Component child) {
        int endIndex = -1;
        MarkupElement next;
        while (null != (next = markupStream.next())) {
            if (next instanceof ComponentTag) {
                ComponentTag cnext = (ComponentTag) next;
                ComponentTag ot = cnext.getOpenTag();
                if (ot != null && ot.equals(startTag)) {
                    endIndex = markupStream.getCurrentIndex();
                    break;
                }
            }
            copy(createMarkupFor(next, child), markup);
        }
        return endIndex;
    }

    private void openEnclosure(Markup markup, ComponentTag startTag) {
        ComponentTag tag = new ComponentTag(startTag.getName(), startTag.getType());
        tag.putAll(startTag.getAttributes());
        tag.put("wicket:id", this.getMarkupId());
        markup.addMarkupElement(tag);
    }

}
