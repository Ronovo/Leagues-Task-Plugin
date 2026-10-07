package com.leaguestasks.sync;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.gameval.VarbitID;
import org.slf4j.Logger;

/**
 * Shared reflection over {@code gameval} id classes: {@link #discover} scans
 * {@link VarbitID} for name-matched fields, and {@link #resolveStaticIntField}
 * looks up one named static int field on any such class ({@code VarbitID},
 * {@code VarPlayerID}, ...).
 */
final class VarbitIdFields
{
    private VarbitIdFields()
    {
    }

    @FunctionalInterface
    interface FieldMapper<T>
    {
        T apply(Field field, Matcher matcher) throws IllegalAccessException;
    }

    static <T> List<T> discover(Pattern pattern, FieldMapper<T> mapper, Logger log)
    {
        List<T> discovered = new ArrayList<>();
        for (Field field : VarbitID.class.getFields())
        {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != int.class)
            {
                continue;
            }

            Matcher matcher = pattern.matcher(field.getName());
            if (!matcher.matches())
            {
                continue;
            }

            try
            {
                discovered.add(mapper.apply(field, matcher));
            }
            catch (IllegalAccessException e)
            {
                log.debug("Could not read VarbitID.{}", field.getName(), e);
            }
        }
        return discovered;
    }

    static int resolveStaticIntField(String fieldName, Logger log)
    {
        return resolveStaticIntField(VarbitID.class, fieldName, log);
    }

    static int resolveStaticIntField(Class<?> owner, String fieldName, Logger log)
    {
        try
        {
            Field field = owner.getField(fieldName);
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class)
            {
                return field.getInt(null);
            }
        }
        catch (ReflectiveOperationException e)
        {
            log.debug("Could not read {}.{}", owner.getSimpleName(), fieldName, e);
        }
        return -1;
    }
}
