package ru.buls.wicket;

import org.apache.wicket.Component;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.MarkupStream;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.parser.XmlTag;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.IObjectClassAwareModel;
import org.apache.wicket.util.convert.ConversionException;
import org.apache.wicket.util.string.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by ptabachkov on 04.11.2014.
 */
public class FdcLabel<T> extends FormComponent<T> {

    public FdcLabel(String id) {
        super(id);
        setConvertEmptyInputStringToNull(true);
    }

    /**
     * @param id
     *            See Component
     * @param model
     *            See Component
     * @param type
     *            The type to use when updating the model for this text field
     * @see org.apache.wicket.Component#Component(String, IModel)
     */
    public FdcLabel(final String id, IModel<T> model, Class<T> type) {
        this(id, model);
        setType(type);
        setConvertEmptyInputStringToNull(true);
    }

    public FdcLabel(String id, final Class<T> type) {
        this(id);
        setType(type);
        setConvertEmptyInputStringToNull(true);
    }

    // Flag for the type resolving. FLAG_RESERVED1-3 is taken by form component
    private static final int TYPE_RESOLVED = Component.FLAG_RESERVED4;

    /** Log for reporting. */
    private static final Logger log = LoggerFactory.getLogger(FdcLabel.class);

    private static final long serialVersionUID = 1L;



    /**
     * @param id
     * @param model
     * @see org.apache.wicket.Component#Component(String, IModel)
     */
    public FdcLabel(final String id, final IModel<T> model)
    {
        super(id, model);
        setConvertEmptyInputStringToNull(true);
    }

    /**
     * Processes the component tag.
     *
     * @param tag
     *            Tag to modify
     * @see org.apache.wicket.Component#onComponentTag(org.apache.wicket.markup.ComponentTag)
     */
    @Override
    protected void onComponentTag(final ComponentTag tag)
    {

        // Must be attached to an label tag
        checkComponentTag(tag, "span");

        // check for text type
        String inputType = getInputType();
        if (inputType != null)
        {
            checkComponentTagAttribute(tag, "type", inputType);
        }
        else
        {
            if (tag.getAttributes().containsKey("type"))
            {
                checkComponentTagAttribute(tag, "type", "text");
            }
        }

        super.onComponentTag(tag);
        // always transform the tag to <span></span> so even labels defined as <span/> render
        tag.setType(XmlTag.OPEN);
        tag.put("value", getValue());

        // Default handling for component tag
        super.onComponentTag(tag);
    }

    /**
     * Handle the container's body.
     *
     * @param markupStream
     *            The markup stream
     * @param openTag
     *            The open tag for the body
     * @see org.apache.wicket.Component#onComponentTagBody(MarkupStream, ComponentTag)
     */
    @Override
    protected final void onComponentTagBody(final MarkupStream markupStream, final ComponentTag openTag)
    {
//        checkComponentTag(openTag, "span");
//        replaceComponentTagBody(markupStream, openTag, getValue());
        checkComponentTag(openTag, "span");
        final CharSequence body = Strings.toMultilineMarkup(getDefaultModelObjectAsString());
        replaceComponentTagBody(markupStream, openTag, body);
    }

    /**
     * Subclass should override this method if this textfield is mapped on a different input type as
     * text. Like PasswordField or HiddenField.
     *
     * @return The input type of this textfield, default is null
     */
    protected String getInputType()
    {
        return null;
    }

    /**
     * Text components that implement this interface are know to be able to provide a pattern for
     * formatting output and parsing input. This can be used by for instance date picker components
     * which are based on Javascript and need some knowledge as to how to communicate properly via
     * request parameters.
     */
    public static interface ITextFormatProvider
    {
        /**
         * Gets the pattern for printing output and parsing input.
         *
         * @return The text pattern
         * @see java.text.SimpleDateFormat
         */
        String getTextFormat();
    }



    /**
     * Should the bound object become <code>null</code> when the input is empty?
     *
     * @return <code>true</code> when the value will be set to <code>null</code> when the input is
     *         empty.
     */
    public final boolean getConvertEmptyInputStringToNull()
    {
        return getFlag(FLAG_CONVERT_EMPTY_INPUT_STRING_TO_NULL);
    }

    /**
     * TextFields return an empty string even if the user didn't type anything in them. To be able
     * to work nicely with validation, this method returns false.
     *
     * @see org.apache.wicket.markup.html.form.FormComponent#isInputNullable()
     */
    @Override
    public boolean isInputNullable()
    {
        return false;
    }

    /**
     *
     * @see org.apache.wicket.markup.html.form.FormComponent#convertInput()
     */
    @Override
    protected void convertInput()
    {
        // Stateless forms don't have to be rendered first, convertInput could be called before
        // onBeforeRender calling resolve type here again to check if the type is correctly set.
        resolveType();
        super.convertInput();
    }

    /**
     * If the type is not set try to guess it if the model supports it.
     *
     * @see org.apache.wicket.Component#onBeforeRender()
     */
    @Override
    protected void onBeforeRender()
    {
        super.onBeforeRender();
        resolveType();
    }

    /**
     *
     */
    private void resolveType()
    {
        if (!getFlag(TYPE_RESOLVED) && getType() == null)
        {
            // Set the type, but only if it's not a String (see WICKET-606).
            // Otherwise, getConvertEmptyInputStringToNull() won't work.
            Class<?> type = getModelType(getDefaultModel());
            if (!String.class.equals(type))
            {
                setType(type);
            }
            setFlag(TYPE_RESOLVED, true);
        }
    }

    /**
     *
     * @param model
     * @return the type of the model object or <code>null</code>
     */
    private Class<?> getModelType(IModel<?> model)
    {
        if (model instanceof IObjectClassAwareModel)
        {
            Class<?> objectClass = ((IObjectClassAwareModel<?>)model).getObjectClass();
            if (objectClass == null)
            {
                log.warn("Couldn't resolve model type of " + model + " for " + this +
                        ", please set the type yourself.");
            }
            return objectClass;
        }
        else
        {
            return null;
        }
    }

    /**
     * Should the bound object become <code>null</code> when the input is empty?
     *
     * @param flag
     *            the value to set this flag.
     * @return this3
     */
    public final FormComponent<T> setConvertEmptyInputStringToNull(boolean flag)
    {
        setFlag(FLAG_CONVERT_EMPTY_INPUT_STRING_TO_NULL, flag);
        return this;
    }

    /**
     * @see org.apache.wicket.markup.html.form.FormComponent#convertValue(String[])
     */
    @Override
    protected T convertValue(String[] value) throws ConversionException
    {
        String tmp = value != null && value.length > 0 ? value[0] : null;
        if (getConvertEmptyInputStringToNull() && Strings.isEmpty(tmp))
        {
            return null;
        }
        return super.convertValue(value);
    }

    /**
     * @see FormComponent#supportsPersistence()
     */
    @Override
    protected boolean supportsPersistence()
    {
        return true;
    }
}
