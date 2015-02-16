package ru.buls.wicket;

import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.markup.*;
import org.apache.wicket.markup.html.WebMarkupContainerWithAssociatedMarkup;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.*;
import org.apache.wicket.markup.html.link.AbstractLink;
import org.apache.wicket.markup.parser.XmlTag;
import org.apache.wicket.markup.parser.filter.WicketTagIdentifier;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.resource.ResourceStreamNotFoundException;
import org.apache.wicket.util.value.IValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;

import static org.apache.wicket.markup.MarkupResourceData.NO_MARKUP_RESOURCE_DATA;


/**
 * Created by alexander on 05.10.14.
 * Предназначен для генерации разметки форм на основе шаблона
 * Пример шаблона
 * <span wicket:id="fields">
 * <wicket:label/> <wicket:field class="cssClass"/><br/>
 * </span>
 * где атрибут wicket:id="fields" - идентификатор компонента
 * <wicket:label/> - подпись элемента формы
 * <wicket:field/> - элемент формы
 * <p/>
 * Комноненты добавляются методом add(Component child) или add(Component child, boolean enclosureVisible)
 *
 * @see ChildTagBuilder - реализация генерации тегов для различных элементов формы
 */
public class FieldsRepeater extends MarkupContainer {

    private static final String WICKET_ID = "wicket:id";
    private static final String WICKET_FIELD = "wicket:field";
    private static final String WICKET_FOR = "wicket:for";
    private static final String LABEL = "label";

    private Logger logger = LoggerFactory.getLogger(FieldsRepeater.class);

    static {
        WicketTagIdentifier.registerWellKnownTagName("field");
        WicketTagIdentifier.registerWellKnownTagName("label");
    }

    protected Decorator<String> labelDecorator = new LabelDecorator();
    protected ChildTagBuilder childTagBuilder = new ChildTagBuilder();
    protected boolean simplifyMarkupId = true;

    //    private String generatedMarkup;
    private boolean supportWicketFor = true;
    private int startMarkupIndex = -1;

    public FieldsRepeater(String id) {
        super(id);
    }

    public Enclosure add(Component child) {
        return add(child, child.isVisible());
    }

    public Enclosure add(Component child, boolean enclosureVisible) {
        if (child instanceof Enclosure) return add((Enclosure) child, enclosureVisible);
        else return add(newEnclosure(child), enclosureVisible);
    }

    public Enclosure add(Enclosure enclo, boolean enclosureVisible) {
        enclo.setVisible(enclosureVisible);
        super.add(enclo);
        return enclo;
    }

    public Enclosure add(Enclosure enclo) {
        return add(enclo, true);
    }

    public Enclosure newEnclosure(Component child) {
        Enclosure enclo = new Enclosure(getEnclosureId(child));
        enclo.add(child);
        if (simplifyMarkupId) {
            enclo.setOutputMarkupId(getOutputMarkupId());
            enclo.setOutputMarkupPlaceholderTag(getOutputMarkupPlaceholderTag());
            child.setOutputMarkupPlaceholderTag(getOutputMarkupPlaceholderTag());
            child.setMarkupId(child.getId());
            enclo.setMarkupId(enclo.getId());
        }
        return enclo;
    }

    public void setChildTagBuilder(ChildTagBuilder childTagBuilder) {
        this.childTagBuilder = childTagBuilder;
    }

    public void setLabelDecorator(Decorator<String> labelDecorator) {
        this.labelDecorator = labelDecorator;
    }

    @Override
    public MarkupStream getAssociatedMarkupStream(boolean throwException) {
        String generatedMarkup = generateMarkup();
        Markup markup;
        try {
            markup = new MarkupParser(generatedMarkup).parse();
        } catch (IOException e) {
            logger.error("error on parsing generated markup : " + generatedMarkup, e);
            throw new RuntimeException(e);
        } catch (ResourceStreamNotFoundException e) {
            logger.error("error on parsing generated markup : " + generatedMarkup, e);
            throw new RuntimeException(e);
        }
        return new MarkupStream(markup);
    }

    protected String generateMarkup() {
        StringBuilder builder = new StringBuilder();

        MarkupStream markupStream = getMarkupStream();
        int index = markupStream.getCurrentIndex();
        try {
            //запоминаем индекс в маркапе при старте отрисовки
            //возвращаем при повторной перерисовке
            if (startMarkupIndex == -1)
                startMarkupIndex = index;
            else markupStream.setCurrentIndex(startMarkupIndex);

            for (int i = 0; i < size(); ++i) {
                markupStream.setCurrentIndex(startMarkupIndex);
                Component component = get(i);
                Markup markup = generate((Enclosure) component, markupStream);
                toBuilder(markup, builder);
            }

            return builder.toString();
        } finally {
            if (index != startMarkupIndex) markupStream.setCurrentIndex(index);
        }
    }

    protected void toBuilder(Markup markup, StringBuilder builder) {
        for (int j = 0; j < markup.size(); ++j) {
            MarkupElement element = markup.get(j);
            if (element != null) builder.append(String.valueOf(element.toCharSequence()).trim());
        }
    }

    protected Markup generate(Enclosure enclosure, MarkupStream markupStream) {
        Markup markup = new Markup(NO_MARKUP_RESOURCE_DATA);

        ComponentTag startTag = markupStream.getTag();
        assert startTag != null;
        assert !startTag.isClose();

        openEnclosure(markup, startTag, enclosure);
        int endIndex = child(markupStream, markup, startTag, enclosure);
        closeEnclosure(endIndex, markup, markupStream);

//        if (endIndex > 0) markupStream.setCurrentIndex(endIndex);
//        if (markupStream.hasMore()) markupStream.next();
        return markup;
    }

    private void closeEnclosure(int endIndex, Markup markup, MarkupStream markupStream) {
        MarkupElement closeTag = markupStream.get(endIndex);
        markup.addMarkupElement(closeTag);
    }

    private int child(MarkupStream markupStream, Markup markup, ComponentTag baseTag, Enclosure enclosure) {
        int endIndex = -1;
        MarkupElement next;
        ComponentTag startTag = (ComponentTag) markupStream.get();
        while (null != (next = markupStream.next())) {
            if (next instanceof ComponentTag) {
                ComponentTag cnext = (ComponentTag) next;
                ComponentTag ot = cnext.getOpenTag();
                if (ot != null && ot.equals(startTag)) {
                    endIndex = markupStream.getCurrentIndex();
                    break;
                }
                assert ot == null || !baseTag.getId().equals(ot.getId()) : "markup overflow";
            }
            copy(createMarkupFor(next, enclosure), markup);
        }
        return endIndex;
    }

    private void openEnclosure(Markup markup, ComponentTag startTag, Enclosure enclosure) {
        ComponentTag tag = new ComponentTag(startTag.getName(), startTag.getType());
        tag.putAll(startTag.getAttributes());
        tag.put(WICKET_ID, enclosure.getMarkupId());
        markup.addMarkupElement(tag);
    }


    @Override
    public boolean hasAssociatedMarkup() {
        return true;
    }

    @Override
    protected void onRender(@Deprecated final MarkupStream markupStream) {
        int startIndex = markupStream.getCurrentIndex();
        MarkupElement thisElement = markupStream.get();
        if (thisElement == null) {
            throw new IllegalArgumentException("markupStream.get() return null");
        } else if (!(thisElement instanceof ComponentTag)) {
            throw new IllegalArgumentException("markupStream.get() must return ComponentTag");
        }

        //рендерим чайлды на основе своего маркапа
        MarkupStream stream = getAssociatedMarkupStream(false);
        while (stream.hasMore()) {
            int currentIndex = stream.getCurrentIndex();

            MarkupElement markupElement = stream.get();
            if (markupElement instanceof ComponentTag) {
                ComponentTag coTag = (ComponentTag) markupElement;
                String id = coTag.getId();
                Component child = get(id);
                child.render(stream);
                int index = stream.getCurrentIndex();
                assert !(index < currentIndex);
                if (index == currentIndex) {
                    stream.setCurrentIndex(currentIndex + 1);
                }
            }
        }

        int endIndex = markupStream.getCurrentIndex();
        //по текущему маркапу проходим до конца, имитируя рендеринг
        ComponentTag openTag = (ComponentTag) thisElement;

        MarkupElement next = markupStream.get();
        while (next != null) {
            if (next instanceof ComponentTag) {
                ComponentTag closeTag = (ComponentTag) next;
                if (openTag.equals(closeTag.getOpenTag())) {
                    //if(markupStream.hasMore()) markupStream.next();
                    break;
                }
            }
            next = markupStream.next();
        }
    }

    private void copy(Markup from, Markup to) {
        for (int i = 0; i < from.size(); ++i)
            to.addMarkupElement(from.get(i));
    }

    private String getEnclosureId(Component child) {
        return "enclosureFor" + child.getMarkupId();
    }

    private Markup createMarkupFor(MarkupElement tag, Enclosure enclosure) {
        Component child = enclosure.get(0);
        Markup childMarkup;
        if (tag instanceof WicketTag) {
            WicketTag wtag = (WicketTag) tag;
            String name = wtag.getName();
            if (!wtag.isOpenClose())
                throw new IllegalStateException(tag + " must be closed");
            if ("field".equals(name))
                childMarkup = createChildMarkup(child, wtag);
            else if (LABEL.equals(name))
                childMarkup = getLabelMarkup(enclosure);
            else throw new IllegalStateException(tag.toString());
        } else {
            if (tag instanceof ComponentTag) tag = createLabelElement(tag, child);
            Markup markup = new Markup(NO_MARKUP_RESOURCE_DATA);
            markup.addMarkupElement(tag);
            childMarkup = markup;
        }
        return childMarkup;
    }

    private MarkupElement createLabelElement(MarkupElement tag, Component child) {
        ComponentTag cTag = (ComponentTag) tag;
        if ((cTag.isOpen() || cTag.isOpenClose()) && LABEL.equals(cTag.getName())) {
            String wicketFor = cTag.getAttribute(WICKET_FOR);
            if (WICKET_FIELD.equals(wicketFor)) {

                ComponentTag newTag = new ComponentTag(cTag.getName(), cTag.getType());
                IValueMap attributes = newTag.getAttributes();
                attributes.putAll(cTag.getAttributes());

                if (supportWicketFor && child instanceof ILabelProvider)
                    attributes.put(WICKET_FOR, child.getMarkupId());
                else attributes.remove(WICKET_FOR);

                tag = newTag;
            } else if (wicketFor != null)
                throw new IllegalStateException("incorrect value '" + wicketFor
                        + "' for attribute " + WICKET_FIELD + " of tag " + cTag);
        }
        return tag;
    }

    private Markup createChildMarkup(Component child, WicketTag wtag) {
        String tagName = childTagBuilder.getTagName(child);
        ComponentTag open = childTagBuilder.createOpenTag(child, tagName);

        String childId = child.getMarkupId();
        open.setId(childId);
        open.put(WICKET_ID, childId);

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

    private Markup getLabelMarkup(Enclosure enclosure) {
        String label = enclosure.showLabel ? labelDecorator.decorate(getLabel(enclosure)) : "";

        Markup markup = new Markup(NO_MARKUP_RESOURCE_DATA);
        markup.addMarkupElement(new RawMarkup(label));

        return markup;
    }

    protected String getLabel(Enclosure enclosure) {
        IModel model = enclosure.getLabel();
        return model != null && model.getObject() != null
                ? model.getObject().toString() : null;
    }

    public static class LabelDecorator implements Decorator<String> {
        public String suffix;
        public String prefix;

        public LabelDecorator() {
        }

        public LabelDecorator(String suffix) {
            this.suffix = suffix;
        }

        @Override
        public String decorate(String s) {
            return (s != null && !s.isEmpty())
                    ? (prefix != null ? prefix : "") + s + (suffix != null ? suffix : "") : s;
        }
    }

    public static class ChildTagBuilder implements Serializable {
        public String getTagName(Component child) {
            String tagName;
            if (child instanceof TextField) tagName = "input";
            else if (child instanceof CheckBox) tagName = "input";
            else if (child instanceof Button) tagName = "input";
            else if (child instanceof TextArea) tagName = "textarea";
            else if (child instanceof AbstractChoice) tagName = "select";
            else if (child instanceof FdcLabel) tagName = "span";
            else if (child instanceof WebMarkupContainerWithAssociatedMarkup
                    || child instanceof FormComponentPanel
                    || child instanceof FieldsRepeater
                    || child instanceof Label
                    || child instanceof AbstractLink)
                tagName = "span";
            else {
                if (child != null) throw new UnsupportedOperationException("does not support child element "
                        + child.getClass());
                else throw new NullPointerException("child cannot be null");
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
            IValueMap attributes = open.getAttributes();
            String type = null;
            if (child instanceof CheckBox) type = "checkbox";
            else if (child instanceof TextField) type = "text";
            else if (child instanceof Button) type = "button";
            if (type != null) attributes.put("type", type);
            return open;
        }

    }

    public class Enclosure extends MarkupContainer implements ILabelProvider {

        IModel label;
        boolean showLabel = true;

        public Enclosure(String id) {
            super(id, new Model());
        }

        @Override
        protected void onRender(MarkupStream markupStream) {
            super.onRender(markupStream);
        }

        public Component get() {
            return get(0);
        }

        @Override
        public IModel getLabel() {
            Component component = get();
            if (component instanceof FormComponent) label = ((FormComponent) component).getLabel();
            return label;
        }

        public void setLabel(IModel label) {
            this.label = label;
            Component component = get();
            if (component instanceof FormComponent) ((FormComponent) component).setLabel(label);
        }

        public void setShowLabel(boolean showLabel) {
            this.showLabel = showLabel;
        }
    }
}
