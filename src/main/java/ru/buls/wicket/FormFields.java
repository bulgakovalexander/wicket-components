package ru.buls.wicket;

import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.Response;
import org.apache.wicket.markup.*;
import org.apache.wicket.markup.html.WebMarkupContainerWithAssociatedMarkup;
import org.apache.wicket.markup.html.form.FormComponentPanel;
import org.apache.wicket.markup.html.form.ILabelProvider;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.parser.XmlTag;
import org.apache.wicket.markup.parser.filter.WicketTagIdentifier;
import org.apache.wicket.model.IModel;

import java.util.Iterator;


/**
 * Created by alexander on 05.10.14.
 */
public class FormFields extends MarkupContainer {

    static {
        WicketTagIdentifier.registerWellKnownTagName("field");
        WicketTagIdentifier.registerWellKnownTagName("label");
    }

    public FormFields(String id, IModel<?> model) {
        super(id, model);
    }

    private String getTagName(Component child) {
        String tagName;
        if (child instanceof TextArea) tagName = "textarea";
        else if (child instanceof WebMarkupContainerWithAssociatedMarkup || child instanceof FormComponentPanel)
            tagName = "span";
        else throw new UnsupportedOperationException("doesnot support child element " + child);
        return tagName;
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
            String name = ((WicketTag) tag).getName();
            if ("field".equals(name)) renderChild(child);
            else if ("label".equals(name)) renderLabel(child);
        } else if (tag instanceof ComponentTag) {
            ComponentTag cTag = (ComponentTag) tag;
            if ((cTag.isOpen() || cTag.isOpenClose()) && "label".equals(cTag.getName())) {
                if ("wicket:field".equals(cTag.getAttribute("wicket:for")))
                    cTag.getAttributes().put("for", getChildId(child));
            }
            response.write(cTag);
        } else response.write(tag.toCharSequence());
    }

    protected void renderChild(Component child) {
        String tagName = getTagName(child);

        ComponentTag open = new ComponentTag(tagName, XmlTag.OPEN);
        open.setId(getChildId(child));
        open.getAttributes().put("id", getChildId(child));

        Markup markup = new Markup(MarkupResourceData.NO_MARKUP_RESOURCE_DATA);
        markup.addMarkupElement(open);
        ComponentTag close = new ComponentTag(tagName, XmlTag.CLOSE);
        close.setOpenTag(open);
        markup.addMarkupElement(close);
        MarkupStream markupStream = new MarkupStream(markup);
        child.render(markupStream);
    }

    protected String getChildId(Component child) {
        return child.getId();
    }

    protected void renderLabel(Component child) {
        String label = getLabel(child);

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
}
