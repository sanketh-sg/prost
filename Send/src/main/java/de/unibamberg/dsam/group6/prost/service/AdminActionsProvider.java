package de.unibamberg.dsam.group6.prost.service;

import static java.lang.String.format;

import de.unibamberg.dsam.group6.prost.util.annotation.AdminAction;
import de.unibamberg.dsam.group6.prost.util.exception.CallFailedException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Handles loading classes annotated with @AdminAction
 * and displaying/calling their actions from the admin panel (frontend).
 */
@Service
@RequiredArgsConstructor
public class AdminActionsProvider {

    @Data
    public static class AdminActionInstance {
        private final String instanceName;
        private final Object instance;

        public List<String> getMethodNames() {
            return Arrays.stream(this.instance.getClass().getMethods())
                    .filter(m ->
                            Modifier.isPublic(m.getModifiers()) && m.getName().startsWith("action__"))
                    .map(m -> m.getName().replace("action__", ""))
                    .toList();
        }

        public void call(String methodName) throws CallFailedException {
            Method method;
            try {
                method = this.instance.getClass().getMethod(format("action__%s", methodName));
                if (Modifier.isPublic(method.getModifiers())
                        && (method.getReturnType().equals(Future.class)
                                || method.getReturnType().equals(void.class))) {
                    method.invoke(this.instance);
                } else {
                    throw new NoSuchMethodException();
                }
            } catch (Exception e) {
                throw new CallFailedException(e);
            }
        }

        public Future<Object> callAndReturn(String methodName) throws CallFailedException {
            Method method;
            try {
                method = this.instance.getClass().getMethod(format("action__%s", methodName));
                if (Modifier.isPublic(method.getModifiers())
                        && (method.getReturnType().equals(Future.class))) {
                    return (Future<Object>) method.invoke(this.instance);
                } else {
                    throw new NoSuchMethodException();
                }
            } catch (Exception e) {
                throw new CallFailedException(e);
            }
        }
    }

    private final ApplicationContext ctx;

    public List<AdminActionInstance> getAnnotatedInstances() {
        var beans = this.ctx.getBeansWithAnnotation(AdminAction.class);
        return beans.keySet().stream()
                .map(k -> new AdminActionInstance(k, beans.get(k)))
                .toList();
    }
}
