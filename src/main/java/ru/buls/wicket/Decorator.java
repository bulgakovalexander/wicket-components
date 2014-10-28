package ru.buls.wicket;

import java.io.Serializable;

/**
* Created by abulgakov on 07.10.2014.
*/
public interface Decorator<T> extends Serializable {
    T decorate(T t);
}
