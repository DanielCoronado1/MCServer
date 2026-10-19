package com.dolfin.space.compat;

import java.lang.reflect.*;
import java.util.*;

/** Bean-compatible Ledger ActionType proxy; audit-only actions never rollback world state. */
public final class LedgerActionProxy implements InvocationHandler {
    private final String identifier;
    private final Map<String, Object> values = new HashMap<>();

    public LedgerActionProxy(String identifier, Map<String, Object> defaults) {
        this.identifier = identifier;
        values.putAll(defaults);
    }

    public Object invoke(Object self, Method method, Object[] args) {
        String name = method.getName();
        if (name.equals("getIdentifier")) return identifier;
        if (name.startsWith("set") && args != null && args.length == 1) {
            values.put(name.substring(3), args[0]);
            return null;
        }
        if (name.startsWith("get") && values.containsKey(name.substring(3)))
            return values.get(name.substring(3));
        if (name.equals("toString")) return identifier + values;
        if (name.equals("hashCode")) return System.identityHashCode(self);
        if (name.equals("equals")) return self == args[0];
        if (method.getReturnType() == boolean.class) return false;
        if (method.getReturnType() == int.class) return 0;
        return null;
    }

    public static Object create(Class<?> api, String id, Map<String, Object> values) {
        return Proxy.newProxyInstance(
                api.getClassLoader(), new Class<?>[] {api}, new LedgerActionProxy(id, values));
    }
}
