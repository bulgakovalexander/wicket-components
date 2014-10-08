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

    public FieldsRepeater(String id, IModel<?> model) {
        super(id, model);
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

            MarkupElement elem = startTag;
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

        if (endIndex > 0) markupStream.setCurrentIndex(endIndex + 1);
    }

    private void renderTag(MarkupElement tag, Component child) {
        Response response = getResponse();
        if (tag instanceof WicketTag) {
            WicketTag wtag = (WicketTag) tag;
            String name = wtag.getName();
            if (!wtag.isOpenClose()) throw new IllegalStateException(tag + " must be closed");
            if ("field".equals(name)) renderChild(child);
            else if ("label".equals(name)) renderLabel(child);
        } else if (tag instanceof ComponentTag) {
            ComponentTag cTag = (ComponentTag) tag;
            if ((cTag.isOpen() || cTag.isOpenClose()) && "label".equals(cTag.getName())) {
                if ("wicket:field".equals(cTag.getAttribute("wicket:for"))) {
                    ComponentTag newTag = new ComponentTag(cTag.getName(), cTag.getType());
                    newTag.getAttributes().putAll(cTag.getAttributes());
                    cTag = newTag;
                    cTag.getAttributes().remove("wicket:for");
                    cTag.getAttributes().put("for", getChildId(child));
                }
            }
            response.write(cTag);
        } else response.write(tag.toCharSequence());
    }

    protected void renderChild(Component child) {
        String tagName = childTagBuilder.getTagName(child);
        ComponentTag open = childTagBuilder.createOpenTag(child, tagName);

        String childId = getChildId(child);
        open.setId(childId);
        open.getAttributes().put("id", childId);

        ComponentTag close = childTagBuilder.createCloseTag(tagName, open);
        MarkupStream markupStream = createChildMarkup(open, close);
        child.render(markupStream);
    }

    private MarkupStream createChildMarkup(ComponentTag open, ComponentTag close) {
        Markup markup = new Markup(MarkupResourceData.NO_MARKUP_RESOURCE_DATA);
        markup.addMarkupElement(open);
        markup.addMarkupElement(close);
        return new MarkupStream(markup);
    }

    protected String getChildId(Component child) {
        return childIdDecorator.decorate(child.getId());
    }

    protected void renderLabel(Component child) {
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
            else throw new UnsupportedOperationException("does not support child element " + child);
            return tagName;
        }

        protected ComponentTag createCloseTag(String tagName, ComponentTag open) {
            ComponentTag close = new ComponentTag(tagName, XmlTag.CLOSE);
            close.setOpenTag(open);
            return close;
        }

        protected ComponentTag createOpenTag(Component child, String tagName) {
            ComponentTag open = new ComponentTag(tagName, XmlTag.OPEN);
            if (child instanceof CheckBox) open.getAttributes().put("type", "checkbox");
            return open;
        }

    }
}
