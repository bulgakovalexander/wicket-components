package ru.buls.wicket;

import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.Response;
import org.apache.wicket.markup.*;
import org.apache.wicket.markup.html.WebMarkupContainerWithAssociatedMarkup;
import org.apache.wicket.markup.html.form.*;
import org.apache.wicket.markup.parser.XmlTag;
import org.apache.wicket.markup.parser.filter.WicketTagIdentifier;
import org.apache.wicket.model.IModel;

import java.io.Serializable;
import java.util.Iterator;


/**
 * Created by alexander on 05.10.14.
 */
public class FieldsRepeater extends MarkupContainer {

    static {
        WicketTagIdentifier.registerWellKnownTagName("field");
        WicketTagIdentifier.registerWellKnownTagName("label");
    }

    protected Decorator<String> childIdDecorator = new ChildIdDecorator();
    protected Decorator<String> labelDecorator = new LabelDecorator();
    protected ChildTagBuilder childTagBuilder = new ChildTagBuilder();

    public FieldsRepeater(String id) {
        super(id);
    }

    @Override
    protected void onRender(final MarkupStream markupStream) {
        ComponentTag startTag = markupStream.getTag();
        assert startTag != null;
        int startIndex = markupStream.getCurrentIndex();
        int endIndex = -1;
        Iterator<? extends Component> it = iterator();
        if (it.hasNext()) do {
            Component child = it.next();
            if (child == null) {
                throw new IllegalStateException("the render iterator returned null for a child");
            }

            ComponentTag newStart = new ComponentTag(startTag.getName(), startTag.getType());
            newStart.getAttributes().putAll(startTag.getAttributes());
            newStart.getAttributes().remove("wicket:id");
            MarkupElement elem = newStart;

            renderTag(elem, child);

            while (!startTag.isOpenClose() && markupStream.hasMore()) {
                elem = markupStream.next();
                renderTag(elem, child);
                if (elem instanceof ComponentTag
                        && startTag == ((ComponentTag) elem).getOpenTag()) {
                    endIndex = markupStream.getCurrentIndex();
                    markupStream.setCurrentIndex(startIndex);
                    break;
                }
            }

        } while (it.hasNext());
        else markupStream.skipComponent();

        if (endIndex > 0) {
            markupStream.setCurrentIndex(endIndex);
            if (markupStream.hasMore()) markupStream.next();
        }
    }

    private void renderTag(MarkupElement tag, Component child) {
        Response response = getResponse();
        if (tag instanceof WicketTag) {
            WicketTag wtag = (WicketTag) tag;
            String name = wtag.getName();
            if (!wtag.isOpenClose()) throw new IllegalStateException(tag + " must be closed");
            if ("field".equals(name)) renderChild(child, wtag);
            else if ("label".equals(name)) renderLabel(child, wtag);
        } else if (tag instanceof ComponentTag) {
            ComponentTag cTag = (ComponentTag) tag;
            if ((cTag.isOpen() || cTag.isOpenClose()) && "label".equals(cTag.getName())) {
                String wicketFor = cTag.getAttribute("wicket:for");
                if ("wicket:field".equals(wicketFor)) {
                    ComponentTag newTag = new ComponentTag(cTag.getName(), cTag.getType());
                    newTag.getAttributes().putAll(cTag.getAttributes());
                    cTag = newTag;
                    cTag.getAttributes().remove("wicket:for");
                    cTag.getAttributes().put("for", getChildId(child));
                } else if (wicketFor != null) {
                    throw new IllegalStateException("incorrect value '" + wicketFor
                            + "' for attribute wicket:field of tag " + cTag);
                }
            }
            response.write(cTag);
        } else response.write(tag.toCharSequence());
    }

    protected void renderChild(Component child, WicketTag wtag) {
        String tagName = childTagBuilder.getTagName(child);
        ComponentTag open = childTagBuilder.createOpenTag(child, tagName);

        String childId = getChildId(child);
        open.setId(childId);
        open.getAttributes().put("id", childId);

        open.getAttributes().putAll(wtag.getAttributes());

        ComponentTag close = childTagBuilder.createCloseTag(tagName, open);
        MarkupStream markupStream = createChildMarkup(open, close);
        child.render(markupStream);
    }

    private MarkupStream createChildMarkup(MarkupElement open, MarkupElement close) {
        Markup markup = new Markup(MarkupResourceData.NO_MARKUP_RESOURCE_DATA);
        if (open instanceof ComponentTag) {
            markup.addMarkupElement(open);
            if (close != null) markup.addMarkupElement(close);
        }
        return new MarkupStream(markup);
    }

    protected String getChildId(Component child) {
        return childIdDecorator.decorate(child.getId());
    }

    protected void renderLabel(Component child, WicketTag wtag) {
        String label = labelDecorator.decorate(getLabel(child));

        getResponse().write(label);
    }

    protected String getLabel(Component child) {
        String label = getChildId(child);
        if (child instanceof ILabelProvider) {
            ILabelProvider labelProvider = (ILabelProvider) child;

            IModel model = labelProvider.getLabel();
            label = model != null ? model.toString() : label;
        }
        return label;
    }

    static class ChildIdDecorator implements Decorator<String> {
        @Override
        public String decorate(String s) {
            return s;
        }
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
            else throw new UnsupportedOperationException("does not support child element " + child);
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
}
