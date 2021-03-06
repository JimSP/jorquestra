package br.com.jorchestra.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.jorchestra.canonical.JOrchestaTemplateDefaultType;

public class JOrchestraHandleUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(JOrchestraHandleUtils.class);

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public static String getJOrchestraRequestTemplate(final Method method) {
		final Object[] list = generateObjectTemplate(method.getParameterTypes());

		try {
			return objectTemplateToJson(list);
		} catch (JsonProcessingException e) {
			LOGGER.debug("m=getJOrchestraRequestTemplate", e);
			return null;
		}
	}

	public static String getJOrchestraResponseTemplate(final Method method) {
		final Object[] list = generateObjectTemplate(new Class[] { method.getReturnType() });
		try {
			return objectTemplateToJson(list);
		} catch (JsonProcessingException e) {
			LOGGER.debug("m=getJOrchestraResponseTemplate", e);
			return null;
		}
	}

	private static String objectTemplateToJson(final Object[] list) throws JsonProcessingException {
		if (list != null) {
			return OBJECT_MAPPER.writeValueAsString(list.length == 1 ? list[0] : list);
		} else {
			return null;
		}
	}

	private static Object createAndBuildObjectTemplate(final Class<?> clazz)
			throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException, ClassNotFoundException {
		try {
			if (clazz.isPrimitive() || clazz.isEnum() || String.class.isAssignableFrom(clazz)) {
				return getDefaltValueFromClazz(clazz);
			} else {
				final Object builder = clazz.getMethod("create", new Class<?>[] {}).invoke(null, new Object[] {});
				if (builder != null) {
					try {
						createWithBuilder(builder);
					} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException
							| InstantiationException | IllegalArgumentException | SecurityException
							| ClassNotFoundException e0) {
						LOGGER.debug("m=createAndBuildObjectTemplate, class=" + clazz, e0);
					}
					return builder.getClass().getMethod("build", new Class<?>[] {}).invoke(builder, new Object[] {});
				} else {
					return createWithContructor(clazz);
				}
			}
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
				| SecurityException | ClassNotFoundException e1) {
			LOGGER.debug("m=createAndBuildObjectTemplate, class=" + clazz, e1);
			return createWithContructor(clazz);
		}
	}

	private static Object createWithContructor(final Class<?> clazz)
			throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException, ClassNotFoundException {

		final Constructor<?>[] constructors = clazz.getDeclaredConstructors();

		if (constructors.length == 0 || clazz.isEnum() || clazz.isPrimitive() || String.class.isAssignableFrom(clazz)) {
			return getDefaltValueFromClazz(clazz);
		} else {
			for (Constructor<?> constructor : constructors) {
				if (Modifier.isPublic(constructor.getModifiers())) {
					if (constructor.getParameterCount() == 0) {
						return clazz.newInstance();
					} else if (constructor.getParameterCount() == 1
							&& constructor.getParameterTypes()[0].isAssignableFrom(String.class))
						return constructor.newInstance(getDefaltValueFromClazz(constructor.getParameterTypes()[0]));
					else {
						try {
							final List<Object> parameters = createParameters(constructor.getParameterTypes());
							return constructor.newInstance(parameters.toArray());
						} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException
								| InstantiationException e0) {
							LOGGER.debug("m=createWithContructor, class=" + clazz, e0);
						}
					}
				}
			}
		}

		return null;
	}

	private static Object getDefaltValueFromClazz(final Class<?> clazz) {
		return JOrchestaTemplateDefaultType.getDefaltValueFromClazz(clazz);
	}

	private static void createWithBuilder(final Object builder)
			throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, InstantiationException,
			IllegalArgumentException, SecurityException, ClassNotFoundException {
		final Class<?> builderClazz = builder.getClass();
		final Method[] builderMethods = builderClazz.getDeclaredMethods();
		for (Method withMethod : builderMethods) {
			if (withMethod.getName().contains("with") && !Modifier.isStatic(withMethod.getModifiers())
					&& Modifier.isPublic(withMethod.getModifiers())) {
				final List<Object> parameters = createParameters(withMethod.getParameterTypes());
				withMethod.invoke(builder, parameters.toArray());
			}
		}
	}

	private static List<Object> createParameters(final Class<?>[] parametersClazz)
			throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, InstantiationException,
			IllegalArgumentException, SecurityException, ClassNotFoundException {
		final List<Object> parameters = new ArrayList<>();
		for (Class<?> parameterClazz : parametersClazz) {
			if (parameterClazz.isPrimitive() || parameterClazz.isEnum()
					|| String.class.isAssignableFrom(parameterClazz)) {
				final Object parameter = getDefaltValueFromClazz(parameterClazz);
				parameters.add(parameter);
			} else {
				final Object parameter = createAndBuildObjectTemplate(parameterClazz);
				if (parameter != null) {
					parameters.add(parameter);
				}
			}
		}
		return parameters;
	}

	private static Object[] generateObjectTemplate(final Class<?>[] clazz) {
		final List<Object> list = new ArrayList<>();
		for (Class<?> itemClazz : clazz) {
			try {
				final Object objectTemplate = createAndBuildObjectTemplate(itemClazz);
				if (objectTemplate != null) {
					final Class<?> templateClazz = objectTemplate.getClass();
					for (Field field : templateClazz.getDeclaredFields()) {
						if (!Modifier.isStatic(field.getModifiers()) && !field.getDeclaringClass().isPrimitive()
								&& Modifier.isPublic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
							try {
								final Object value = createAndBuildObjectTemplate(field.getType());

								if (value == null) {
									generateObjectTemplate(new Class<?>[] {});
								} else {
									generateObjectTemplate(new Class<?>[] { value.getClass() });
								}
							} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
									| NoSuchMethodException | SecurityException | InstantiationException e0) {
								LOGGER.debug(
										"m=generateObjectTemplate, step=field, class=" + itemClazz + "field=" + field,
										e0);
							}
						}
					}
					list.add(objectTemplate);
				}
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
					| NoSuchMethodException | SecurityException | InstantiationException | ClassNotFoundException e1) {
				LOGGER.debug("m=generateObjectTemplate, step=field, class=" + itemClazz, e1);
			}
		}
		return list.size() == 0 ? null : list.toArray();
	}
}
